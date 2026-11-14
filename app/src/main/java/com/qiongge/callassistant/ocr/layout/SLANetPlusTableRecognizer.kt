package com.qiongge.callassistant.ocr.layout

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log
import java.nio.FloatBuffer
import java.util.EnumSet
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Runs the bundled SLANet_plus structure model and assigns OCR boxes to its predicted cells. */
class SLANetPlusTableRecognizer(
    context: Context,
    private val preferNnapi: Boolean = true,
) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val environment = OrtEnvironment.getEnvironment()

    @Volatile
    private var session: OrtSession? = null
    @Volatile
    private var inputName: String = "x"
    @Volatile
    private var usingNnapi = false
    @Volatile
    private var nnapiCpuDisabled = false

    val executionProvider: String
        get() = when {
            !usingNnapi -> "CPU"
            nnapiCpuDisabled -> "NNAPI (Qualcomm CPU disabled)"
            else -> "NNAPI"
        }

    fun mapTextToCells(bitmap: Bitmap, lines: List<OcrTextLine>): List<List<List<OcrTextLine>>>? {
        if (lines.isEmpty()) return null
        val cropRect = cropToText(bitmap, lines)
        val crop = Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
        return try {
            val prepared = prepareInput(crop)
            val (locations, locationShape, structure, structureShape) = run(prepared.input)
            val cells = decodeCells(
                locations = locations,
                locationShape = locationShape,
                structure = structure,
                structureShape = structureShape,
                ratio = prepared.ratio,
                offsetX = cropRect.left,
                offsetY = cropRect.top,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
            )
            if (cells.size < 2) return null

            val rows = cells.groupBy(Cell::rowIndex).toSortedMap()
            val assignedByRow = rows.mapValues { (_, rowCells) ->
                val orderedCells = rowCells.sortedBy(Cell::left)
                val buckets = List(orderedCells.size) { mutableListOf<OcrTextLine>() }
                lines.forEach { line ->
                    val centerX = (line.left + line.right) / 2f
                    val centerY = (line.top + line.bottom) / 2f
                    val containing = orderedCells.indices.filter { orderedCells[it].contains(centerX, centerY) }
                    val targetIndex = when {
                        containing.isNotEmpty() -> containing.minByOrNull { orderedCells[it].area }!!
                        else -> orderedCells.indices
                            .map { index -> index to orderedCells[index].overlapRatio(line) }
                            .filter { (_, overlap) -> overlap >= MIN_LINE_OVERLAP }
                            .maxByOrNull { it.second }
                            ?.first
                    }
                    if (targetIndex != null) buckets[targetIndex] += line
                }
                buckets.map { it.sortedBy(OcrTextLine::left) }
            }
            val mappedLineCount = assignedByRow.values.flatten().sumOf { it.size }
            if (mappedLineCount < lines.size * MIN_MAPPED_LINE_RATIO) return null
            assignedByRow.values
                .filter { row -> row.any { cell -> cell.isNotEmpty() } }
                .map { row -> row.map { it.toList() } }
                .takeIf { it.isNotEmpty() }
        } finally {
            if (crop !== bitmap) crop.recycle()
        }
    }

    private fun run(input: FloatArray): ModelOutputs = synchronized(lock) {
        val activeSession = ensureSession()
        try {
            runSession(activeSession, input)
        } catch (acceleratorError: Exception) {
            if (!usingNnapi) throw acceleratorError
            Log.w(TAG, "SLANet_plus NNAPI inference failed; retrying with CPU", acceleratorError)
            createSession(useNnapi = false)
            runSession(session ?: throw IllegalStateException("SLANet_plus CPU session was not created"), input)
        }
    }

    private fun ensureSession(): OrtSession {
        session?.let { return it }
        return try {
            createSession(useNnapi = preferNnapi)
            session ?: error("SLANet_plus session was not created")
        } catch (providerError: Exception) {
            if (!preferNnapi) throw providerError
            Log.w(TAG, "SLANet_plus NNAPI session creation failed; retrying with CPU", providerError)
            createSession(useNnapi = false)
            session ?: error("SLANet_plus CPU session was not created")
        }
    }

    private fun createSession(useNnapi: Boolean) {
        session?.close()
        session = null
        val disableNnapiCpu = useNnapi && shouldDisableNnapiCpu()
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(2)
            if (useNnapi) {
                val flags = if (disableNnapiCpu) {
                    EnumSet.of(NNAPIFlags.CPU_DISABLED)
                } else {
                    EnumSet.noneOf(NNAPIFlags::class.java)
                }
                addNnapi(flags)
            }
        }
        try {
            val model = appContext.assets.open(MODEL_ASSET).use { it.readBytes() }
            val created = environment.createSession(model, options)
            inputName = created.inputNames.firstOrNull() ?: "x"
            session = created
            usingNnapi = useNnapi
            nnapiCpuDisabled = disableNnapiCpu
            Log.i(TAG, "SLANet_plus execution provider configured: $executionProvider")
        } finally {
            options.close()
        }
    }

    private fun runSession(activeSession: OrtSession, input: FloatArray): ModelOutputs {
        val inputTensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, CHANNELS.toLong(), MODEL_SIZE.toLong(), MODEL_SIZE.toLong()),
        )
        val result = try {
            activeSession.run(mapOf(inputName to inputTensor))
        } finally {
            inputTensor.close()
        }
        return try {
            var locations: FloatArray? = null
            var locationShape: LongArray? = null
            var structure: FloatArray? = null
            var structureShape: LongArray? = null
            activeSession.outputNames.forEach { name ->
                val tensor = result.get(name).orElse(null) as? OnnxTensor ?: return@forEach
                val shape = tensor.info.shape
                when (shape.lastOrNull()?.toInt()) {
                    LOCATION_WIDTH -> {
                        locations = tensor.floatBuffer.toFloatArray()
                        locationShape = shape
                    }
                    STRUCTURE_CLASS_COUNT -> {
                        structure = tensor.floatBuffer.toFloatArray()
                        structureShape = shape
                    }
                }
            }
            ModelOutputs(
                locations = locations ?: error("SLANet_plus location output is missing"),
                locationShape = locationShape ?: error("SLANet_plus location shape is missing"),
                structure = structure ?: error("SLANet_plus structure output is missing"),
                structureShape = structureShape ?: error("SLANet_plus structure shape is missing"),
            )
        } finally {
            result.close()
        }
    }

    private fun decodeCells(
        locations: FloatArray,
        locationShape: LongArray,
        structure: FloatArray,
        structureShape: LongArray,
        ratio: Float,
        offsetX: Int,
        offsetY: Int,
        imageWidth: Int,
        imageHeight: Int,
    ): List<Cell> {
        if (locationShape.size < 3 || structureShape.size < 3) return emptyList()
        val locationSteps = locationShape[1].toInt()
        val locationWidth = locationShape[2].toInt()
        val structureSteps = structureShape[1].toInt()
        val classCount = structureShape[2].toInt()
        if (locationWidth != LOCATION_WIDTH || classCount != STRUCTURE_CLASS_COUNT) return emptyList()
        val steps = min(locationSteps, structureSteps)
        var currentRow = -1
        val cells = mutableListOf<Cell>()
        for (step in 0 until steps) {
            val classOffset = step * classCount
            var token = 0
            var confidence = Float.NEGATIVE_INFINITY
            for (candidate in 0 until classCount) {
                val score = structure[classOffset + candidate]
                if (score > confidence) {
                    token = candidate
                    confidence = score
                }
            }
            if (step > 0 && token == EOS_TOKEN) break
            when (token) {
                TABLE_ROW_TOKEN -> currentRow++
                SHORT_CELL_TOKEN, EMPTY_CELL_TOKEN -> {
                    if (currentRow < 0) continue
                    val offset = step * LOCATION_WIDTH
                    val points = FloatArray(8)
                    for (point in 0 until 4) {
                        points[point * 2] = (locations[offset + point * 2] * MODEL_SIZE / ratio + offsetX)
                            .coerceIn(0f, imageWidth.toFloat())
                        points[point * 2 + 1] = (locations[offset + point * 2 + 1] * MODEL_SIZE / ratio + offsetY)
                            .coerceIn(0f, imageHeight.toFloat())
                    }
                    val cell = Cell(currentRow, points)
                    if (cell.area > MIN_CELL_AREA) cells += cell
                }
            }
        }
        return cells
    }

    private fun prepareInput(bitmap: Bitmap): PreparedInput {
        val ratio = MODEL_SIZE.toFloat() / max(bitmap.width, bitmap.height)
        val targetWidth = (bitmap.width * ratio).toInt().coerceIn(1, MODEL_SIZE)
        val targetHeight = (bitmap.height * ratio).toInt().coerceIn(1, MODEL_SIZE)
        val resized = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        try {
            val pixels = IntArray(targetWidth * targetHeight)
            resized.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            val input = FloatArray(CHANNELS * MODEL_SIZE * MODEL_SIZE)
            val planeSize = MODEL_SIZE * MODEL_SIZE
            for (y in 0 until targetHeight) {
                val row = y * targetWidth
                val targetRow = y * MODEL_SIZE
                for (x in 0 until targetWidth) {
                    val color = pixels[row + x]
                    val index = targetRow + x
                    val blue = Color.blue(color) / 255f
                    val green = Color.green(color) / 255f
                    val red = Color.red(color) / 255f
                    input[index] = (blue - MEAN[0]) / STD[0]
                    input[planeSize + index] = (green - MEAN[1]) / STD[1]
                    input[planeSize * 2 + index] = (red - MEAN[2]) / STD[2]
                }
            }
            return PreparedInput(input, ratio)
        } finally {
            if (resized !== bitmap) resized.recycle()
        }
    }

    private fun cropToText(bitmap: Bitmap, lines: List<OcrTextLine>): CropRect {
        val padding = (max(bitmap.width, bitmap.height) * CROP_PADDING_RATIO).toInt()
        val left = (lines.minOf(OcrTextLine::left) - padding).coerceAtLeast(0)
        val top = (lines.minOf(OcrTextLine::top) - padding).coerceAtLeast(0)
        val right = (lines.maxOf(OcrTextLine::right) + padding).coerceAtMost(bitmap.width)
        val bottom = (lines.maxOf(OcrTextLine::bottom) + padding).coerceAtMost(bitmap.height)
        return CropRect(left, top, right.coerceAtLeast(left + 1), bottom.coerceAtLeast(top + 1))
    }

    private fun shouldDisableNnapiCpu(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else ""
        return socManufacturer.equals("QTI", ignoreCase = true) ||
            socManufacturer.equals("Qualcomm", ignoreCase = true) ||
            Build.HARDWARE.equals("qcom", ignoreCase = true)
    }

    private fun FloatBuffer.toFloatArray(): FloatArray {
        val copy = duplicate()
        copy.rewind()
        return FloatArray(copy.remaining()).also { copy.get(it) }
    }

    private data class PreparedInput(val input: FloatArray, val ratio: Float)
    private data class ModelOutputs(
        val locations: FloatArray,
        val locationShape: LongArray,
        val structure: FloatArray,
        val structureShape: LongArray,
    )
    private data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun width() = right - left
        fun height() = bottom - top
    }

    private data class Cell(val rowIndex: Int, val points: FloatArray) {
        val left: Float get() = (0..6 step 2).minOf { points[it] }
        private val top: Float get() = (1..7 step 2).minOf { points[it] }
        private val right: Float get() = (0..6 step 2).maxOf { points[it] }
        private val bottom: Float get() = (1..7 step 2).maxOf { points[it] }
        val area: Float get() = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)

        fun contains(x: Float, y: Float): Boolean {
            var inside = false
            var previous = 3
            for (current in 0..3) {
                val currentX = points[current * 2]
                val currentY = points[current * 2 + 1]
                val previousX = points[previous * 2]
                val previousY = points[previous * 2 + 1]
                val crosses = (currentY > y) != (previousY > y) &&
                    x < (previousX - currentX) * (y - currentY) / (previousY - currentY + 0.00001f) + currentX
                if (crosses) inside = !inside
                previous = current
            }
            return inside
        }

        fun overlapRatio(line: OcrTextLine): Float {
            val width = (min(right, line.right.toFloat()) - max(left, line.left.toFloat())).coerceAtLeast(0f)
            val height = (min(bottom, line.bottom.toFloat()) - max(top, line.top.toFloat())).coerceAtLeast(0f)
            val lineArea = (line.right - line.left).coerceAtLeast(1) * (line.bottom - line.top).coerceAtLeast(1)
            return width * height / lineArea
        }
    }

    companion object {
        private const val TAG = "SLANetPlus"
        private const val MODEL_ASSET = "ocr/models/table/inference.onnx"
        private const val MODEL_SIZE = 488
        private const val CHANNELS = 3
        private const val LOCATION_WIDTH = 8
        private const val STRUCTURE_CLASS_COUNT = 50
        // Class ids follow PaddleOCR's merge_no_span_structure TableLabelDecode mapping.
        private const val TABLE_ROW_TOKEN = 5
        private const val SHORT_CELL_TOKEN = 7
        private const val EMPTY_CELL_TOKEN = 48
        private const val EOS_TOKEN = 49
        private const val MIN_CELL_AREA = 36f
        private const val MIN_LINE_OVERLAP = 0.28f
        private const val MIN_MAPPED_LINE_RATIO = 0.45f
        private const val CROP_PADDING_RATIO = 0.025f
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }
}
