package com.qiongge.callassistant.ocr.layout

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

object OpenCvTableGridDetector {
    fun detect(bitmap: Bitmap): TableGrid? {
        if (bitmap.width < 80 || bitmap.height < 80) return null
        val rgba = Mat()
        val gray = Mat()
        val binary = Mat()
        val horizontal = Mat()
        val vertical = Mat()
        val horizontalKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(max(18, bitmap.width / 24).toDouble(), 1.0),
        )
        val verticalKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(1.0, max(18, bitmap.height / 24).toDouble()),
        )
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)
            Imgproc.morphologyEx(binary, horizontal, Imgproc.MORPH_OPEN, horizontalKernel)
            Imgproc.morphologyEx(binary, vertical, Imgproc.MORPH_OPEN, verticalKernel)

            val horizontalLines = findLines(horizontal, horizontal = true, minCoverage = 0.28)
            val verticalLines = findLines(vertical, horizontal = false, minCoverage = 0.24)
            if (horizontalLines.size >= 2 && verticalLines.size >= 2) {
                TableGrid(horizontalLines, verticalLines)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            rgba.release()
            gray.release()
            binary.release()
            horizontal.release()
            vertical.release()
            horizontalKernel.release()
            verticalKernel.release()
        }
    }

    private fun findLines(mask: Mat, horizontal: Boolean, minCoverage: Double): List<Int> {
        val width = mask.cols()
        val height = mask.rows()
        if (width <= 0 || height <= 0) return emptyList()
        val pixels = ByteArray(width * height)
        mask.get(0, 0, pixels)
        val lineCount = if (horizontal) height else width
        val lineLength = if (horizontal) width else height
        val minimumPixels = (lineLength * minCoverage).toInt()
        val coordinates = mutableListOf<Int>()
        for (line in 0 until lineCount) {
            var active = 0
            for (position in 0 until lineLength) {
                val index = if (horizontal) line * width + position else position * width + line
                if ((pixels[index].toInt() and 0xff) > 0) active++
            }
            if (active >= minimumPixels) coordinates += line
        }

        if (coordinates.isEmpty()) return emptyList()
        val mergeGap = max(2, (lineCount * 0.0025).toInt())
        val lines = mutableListOf<Int>()
        var start = coordinates.first()
        var previous = start
        coordinates.drop(1).forEach { current ->
            if (current - previous > mergeGap + 1) {
                lines += (start + previous) / 2
                start = current
            }
            previous = current
        }
        lines += (start + previous) / 2
        return lines.takeIf { it.size <= MAX_SEPARATORS }.orEmpty()
    }

    private const val MAX_SEPARATORS = 120
}
