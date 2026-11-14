package com.qiongge.callassistant.ocr.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRResult
import com.paddle.ocr.util.OpenCVUtils
import com.qiongge.callassistant.ocr.layout.OpenCvTableGridDetector
import com.qiongge.callassistant.ocr.layout.OcrTextLine
import com.qiongge.callassistant.ocr.layout.SLANetPlusTableRecognizer
import com.qiongge.callassistant.ocr.layout.TableGrid
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PreparedOcrImage(
    val file: File,
    val displayName: String,
    val width: Int,
    val height: Int,
)

data class RecognizedOcrPage(
    val lines: List<OcrTextLine>,
    val grid: TableGrid?,
    val structuredRows: List<List<List<OcrTextLine>>>?,
    val inferenceTimeMs: Long,
    val executionProvider: String,
)

class LocalPaddleOcrEngine(context: Context) {
    private val appContext = context.applicationContext
    private val modelMutex = Mutex()
    private val tableStructureRecognizer = SLANetPlusTableRecognizer(appContext)

    @Volatile
    private var paddleOcr: PaddleOCR? = null

    suspend fun prepareImage(uri: Uri, pageIndex: Int, directory: File): PreparedOcrImage =
        withContext(Dispatchers.IO) {
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Cannot create private image directory")
            }
            val displayName = queryDisplayName(uri) ?: "名单照片 ${pageIndex + 1}"
            val temporaryFile = File.createTempFile("source-$pageIndex-", ".tmp", directory)
            val preparedFile = File(directory, "page-${System.currentTimeMillis()}-$pageIndex.jpg")
            try {
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Unable to read selected image")
                input.use { source ->
                    temporaryFile.outputStream().buffered().use { target ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var totalBytes = 0L
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            if (totalBytes > MAX_SOURCE_BYTES) throw IOException("Image exceeds size limit")
                            target.write(buffer, 0, count)
                        }
                    }
                }

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temporaryFile.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image")
                val decoded = BitmapFactory.decodeFile(
                    temporaryFile.absolutePath,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                ) ?: throw IOException("Unable to decode image")
                val oriented = orient(decoded, temporaryFile)
                if (oriented !== decoded) decoded.recycle()
                FileOutputStream(preparedFile).use { output ->
                    if (!oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                        throw IOException("Unable to prepare image")
                    }
                }
                PreparedOcrImage(preparedFile, displayName, oriented.width, oriented.height)
                    .also { oriented.recycle() }
            } catch (exception: Exception) {
                preparedFile.delete()
                throw exception
            } finally {
                temporaryFile.delete()
            }
        }

    suspend fun recognize(file: File): RecognizedOcrPage = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
        ) ?: throw IOException("Unable to load image for recognition")
        try {
            if (!OpenCVUtils.init(appContext)) throw IOException("OpenCV failed to initialize")
            val grid = OpenCvTableGridDetector.detect(bitmap)
            val engine = getEngine()
            val result = engine.recognize(bitmap)
            val lines = result.results.mapNotNull { it.toTextLine() }
            val structuredRows = runCatching {
                tableStructureRecognizer.mapTextToCells(bitmap, lines)
            }.onFailure { exception ->
                Log.w(TAG, "SLANet_plus failed; falling back to OpenCV and coordinate grouping", exception)
            }.getOrNull()
            Log.i(TAG, "PP-OCRv6 small finished in ${result.totalTimeMs} ms; configured provider=${engine.executionProvider}; lines=${lines.size}")
            Log.i(
                TAG,
                "SLANet_plus field structure provider=${tableStructureRecognizer.executionProvider}; rows=${structuredRows?.size ?: 0}",
            )
            RecognizedOcrPage(
                lines = lines,
                grid = grid,
                structuredRows = structuredRows,
                inferenceTimeMs = result.totalTimeMs,
                executionProvider = engine.executionProvider,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun getEngine(): PaddleOCR {
        paddleOcr?.let { return it }
        return modelMutex.withLock {
            paddleOcr?.let { return@withLock it }
            if (!OpenCVUtils.init(appContext)) throw IOException("OpenCV failed to initialize")
            PaddleOCR.create(
                context = appContext,
                config = PaddleOCRConfig(
                    detLimitSideLen = 64,
                    detLimitType = "min",
                    detMaxSideLimit = MAX_DIMENSION,
                    recBatchSize = 1,
                ),
                engineConfig = EngineConfig(numThreads = 4, preferNnapi = true),
                detModelAssetPath = DET_MODEL,
                recModelAssetPath = REC_MODEL,
                recConfigAssetPath = REC_CONFIG,
            ).also { engine ->
                paddleOcr = engine
                Log.i(TAG, "Loaded PP-OCRv6 small; execution provider configured: ${engine.executionProvider}")
            }
        }
    }

    private fun OCRResult.toTextLine(): OcrTextLine? {
        if (text.isBlank()) return null
        val left = box.points.minOf { it.x }.roundToInt()
        val top = box.points.minOf { it.y }.roundToInt()
        val right = box.points.maxOf { it.x }.roundToInt()
        val bottom = box.points.maxOf { it.y }.roundToInt()
        if (right <= left || bottom <= top) return null
        return OcrTextLine(text, left, top, right, bottom, confidence)
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0)?.take(120) else null }
    }.getOrNull()

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (
            width / sample > MAX_DIMENSION ||
            height / sample > MAX_DIMENSION ||
            (width.toLong() / sample) * (height.toLong() / sample) > MAX_PIXELS
        ) {
            sample *= 2
        }
        return sample
    }

    private fun orient(bitmap: Bitmap, source: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        const val MAX_IMAGES = 5
        private const val TAG = "LocalPaddleOcr"
        private const val MAX_SOURCE_BYTES = 32L * 1024L * 1024L
        private const val MAX_DIMENSION = 2400
        private const val MAX_PIXELS = 4_000_000L
        private const val JPEG_QUALITY = 88
        private const val DET_MODEL = "ocr/models/det/inference.onnx"
        private const val REC_MODEL = "ocr/models/rec/inference.onnx"
        private const val REC_CONFIG = "ocr/models/rec/inference.yml"
    }
}
