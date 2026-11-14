package com.qiongge.callassistant.ocr.layout

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

/** Compact storage for OCR cell text without changing the row's readable source text. */
object OcrSourceCellCodec {
    fun encode(cells: List<String>): String? {
        if (cells.isEmpty()) return null
        return cells.joinToString(CELL_SEPARATOR) { cell ->
            if (cell.isEmpty()) EMPTY_CELL else encoder.encodeToString(cell.toByteArray(UTF_8))
        }
    }

    fun decode(value: String?): List<String>? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            value.split(CELL_SEPARATOR).map { cell ->
                if (cell == EMPTY_CELL) "" else decoder.decode(cell).toString(UTF_8)
            }
        }.getOrNull()
    }

    private const val CELL_SEPARATOR = "."
    private const val EMPTY_CELL = "~"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
}
