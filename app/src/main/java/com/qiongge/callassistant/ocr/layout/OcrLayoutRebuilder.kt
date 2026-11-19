package com.qiongge.callassistant.ocr.layout

import com.qiongge.callassistant.domain.importing.PhoneRecognition
import com.qiongge.callassistant.domain.importing.ImportFieldRules
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class OcrTextLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float,
)

data class TableGrid(
    val horizontalSeparators: List<Int>,
    val verticalSeparators: List<Int>,
)

data class RecognizedImportRow(
    val name: String,
    val phone: String,
    val sourceText: String,
    val cells: List<String>,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float,
    val reviewed: Boolean,
)

object OcrLayoutRebuilder {
    private data class RowGroup(val lines: MutableList<OcrTextLine>) {
        val centerY: Float get() = lines.map { (it.top + it.bottom) / 2f }.average().toFloat()
        val averageHeight: Float get() = lines.map { (it.bottom - it.top).coerceAtLeast(1) }.average().toFloat()
    }

    private data class FieldColumns(
        val name: Int? = null,
        val phone: Int? = null,
        val remark: Int? = null,
    )

    fun rebuild(
        lines: List<OcrTextLine>,
        imageWidth: Int,
        imageHeight: Int,
        grid: TableGrid? = null,
        structuredRows: List<List<List<OcrTextLine>>>? = null,
    ): List<RecognizedImportRow> {
        if (imageWidth <= 0 || imageHeight <= 0) return emptyList()
        val validLines = lines.filter { it.text.isNotBlank() && it.right > it.left && it.bottom > it.top }
        if (!structuredRows.isNullOrEmpty()) {
            val cellTexts = structuredRows.map { row ->
                row.map { cell -> cell.joinToString(" ") { it.text.trim() }.trim() }
            }
            val fieldColumns = inferFieldColumnsFromText(cellTexts)
            return structuredRows.mapNotNull { rowCells ->
                val cleanCells = rowCells.map { cell ->
                    cell.filter { it.text.isNotBlank() && it.right > it.left && it.bottom > it.top }
                        .sortedBy(OcrTextLine::left)
                }
                val rowLines = cleanCells.flatten()
                if (rowLines.isEmpty()) return@mapNotNull null
                val cellText = cleanCells.map { cell -> cell.joinToString(" ") { it.text.trim() }.trim() }
                RowGroup(rowLines.toMutableList()).toImportRows(
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    structuredCells = cellText,
                    nameColumn = fieldColumns.name,
                    phoneColumn = fieldColumns.phone,
                    remarkColumn = fieldColumns.remark,
                )
            }.flatten()
        }
        if (grid != null && grid.horizontalSeparators.size >= 2 && grid.verticalSeparators.size >= 2) {
            val gridGroups = groupByGridRows(validLines, grid)
            if (gridGroups.isNotEmpty()) {
                val columnSeparators = grid.verticalSeparators.distinct().sorted()
                val fieldColumns = inferFieldColumnsFromText(
                    gridGroups.map { it.toCellTextRow(gridColumnSeparators = columnSeparators) },
                )
                return gridGroups.flatMap { group ->
                    group.toImportRows(
                        imageWidth,
                        imageHeight,
                        gridColumnSeparators = columnSeparators,
                        nameColumn = fieldColumns.name,
                        phoneColumn = fieldColumns.phone,
                        remarkColumn = fieldColumns.remark,
                    )
                }
            }
        }
        val groups = mutableListOf<RowGroup>()
        validLines.sortedBy { it.top }
            .forEach { line ->
                val lineCenter = (line.top + line.bottom) / 2f
                val lineHeight = (line.bottom - line.top).coerceAtLeast(1).toFloat()
                val compatible = groups
                    .map { group -> group to abs(group.centerY - lineCenter) }
                    .filter { (group, delta) -> delta <= max(lineHeight, group.averageHeight) * 0.68f }
                    .minByOrNull { it.second }
                    ?.first
                if (compatible == null) groups += RowGroup(mutableListOf(line)) else compatible.lines += line
            }

        val columnStarts = inferTextColumnStarts(validLines, imageWidth)
        val orderedGroups = groups.sortedBy(RowGroup::centerY)
        val fieldColumns = inferFieldColumnsFromText(
            orderedGroups.map { it.toCellTextRow(textColumnStarts = columnStarts) },
        )
        return orderedGroups.flatMap { group ->
            group.toImportRows(
                imageWidth,
                imageHeight,
                textColumnStarts = columnStarts,
                nameColumn = fieldColumns.name,
                phoneColumn = fieldColumns.phone,
                remarkColumn = fieldColumns.remark,
            )
        }
    }

    private fun groupByGridRows(lines: List<OcrTextLine>, grid: TableGrid): List<RowGroup> {
        val separators = grid.horizontalSeparators.distinct().sorted()
        return separators.zipWithNext().mapNotNull { (top, bottom) ->
            if (bottom <= top) return@mapNotNull null
            val cellLines = lines.filter { line ->
                val centerY = (line.top + line.bottom) / 2
                centerY >= top && centerY < bottom
            }.sortedBy { it.left }
            cellLines.takeIf { it.isNotEmpty() }?.let { RowGroup(it.toMutableList()) }
        }
    }

    private fun RowGroup.toImportRows(
        imageWidth: Int,
        imageHeight: Int,
        gridColumnSeparators: List<Int>? = null,
        textColumnStarts: List<Int>? = null,
        structuredCells: List<String>? = null,
        nameColumn: Int? = null,
        phoneColumn: Int? = null,
        remarkColumn: Int? = null,
    ): List<RecognizedImportRow> {
        val orderedLines = lines.sortedBy { it.left }
        val source = structuredCells?.filter(String::isNotBlank)?.joinToString(" ")
            ?: orderedLines.joinToString(" ") { it.text.trim() }.trim()
        if (source.isBlank()) return emptyList()
        val cells = structuredCells ?: toCellTextRow(
            gridColumnSeparators = gridColumnSeparators,
            textColumnStarts = textColumnStarts,
        )
        val headerRow = cells.any {
            identifyHeaderField(it) == HeaderField.NAME || identifyHeaderField(it) == HeaderField.PHONE
        }
        val phoneText = phoneColumn?.let { cells.getOrNull(it).orEmpty() }
        val nameText = nameColumn?.let { cells.getOrNull(it).orEmpty() } ?: source
        val candidates = if (headerRow) emptyList() else (
            PhoneRecognition.candidates(phoneText ?: source) + cells.flatMapIndexed { index, cell ->
                if (index == nameColumn || index == phoneColumn || index == remarkColumn) emptyList()
                else PhoneRecognition.candidates(cell)
            }
        ).distinct()
        val phones = candidates.ifEmpty { listOf("") }
        val left = orderedLines.minOf { it.left }.toFloat() / imageWidth
        val top = orderedLines.minOf { it.top }.toFloat() / imageHeight
        val right = orderedLines.maxOf { it.right }.toFloat() / imageWidth
        val bottom = orderedLines.maxOf { it.bottom }.toFloat() / imageHeight
        val confidence = orderedLines.map(OcrTextLine::confidence).average().toFloat().coerceIn(0f, 1f)
        val uncertainBox = confidence in 0f..0.38f

        return phones.map { phone ->
            val withoutPhone = if (phone.isBlank()) source else source.replaceFirst(phone, " ")
            val name = if (headerRow) "" else extractName(if (nameColumn != null) nameText else withoutPhone)
            val remark = remarkColumn?.let { cells.getOrNull(it).orEmpty() }.orEmpty()
            val displaySource = if (remark.isBlank()) source else "$source | 备注：$remark"
            RecognizedImportRow(
                name = name,
                phone = PhoneRecognition.normalizeForEditing(phone),
                sourceText = displaySource,
                cells = cells,
                left = left.coerceIn(0f, 1f),
                top = top.coerceIn(0f, 1f),
                right = right.coerceIn(0f, 1f),
                bottom = bottom.coerceIn(0f, 1f),
                confidence = confidence,
                reviewed = name.isNotBlank() && PhoneRecognition.isMainlandMobile(phone) && !uncertainBox,
            )
        }
    }

    private fun RowGroup.toCellTextRow(
        gridColumnSeparators: List<Int>? = null,
        textColumnStarts: List<Int>? = null,
    ): List<String> {
        val orderedLines = lines.sortedBy { it.left }
        val source = orderedLines.joinToString(" ") { it.text.trim() }.trim()
        val cells = when {
            gridColumnSeparators != null -> gridColumnSeparators.zipWithNext().map { (left, right) ->
                orderedLines
                    .filter { line ->
                        val centerX = (line.left + line.right) / 2
                        centerX >= left && centerX < right
                    }
                    .joinToString(" ") { it.text.trim() }
                    .trim()
            }
            !textColumnStarts.isNullOrEmpty() -> {
                val columnLines = List(textColumnStarts.size) { mutableListOf<OcrTextLine>() }
                orderedLines.forEach { line ->
                    val column = textColumnStarts.indices.minByOrNull { index -> abs(line.left - textColumnStarts[index]) } ?: 0
                    columnLines[column] += line
                }
                columnLines.map { column -> column.joinToString(" ") { it.text.trim() }.trim() }
            }
            else -> listOf(source)
        }.ifEmpty { listOf(source) }
        return cells
    }

    private fun inferFieldColumnsFromText(cellTexts: List<List<String>>): FieldColumns {
        val headerCells = cellTexts.take(MAX_HEADER_SCAN_ROWS)
            .firstOrNull { row ->
                row.any { identifyHeaderField(it) == HeaderField.NAME || identifyHeaderField(it) == HeaderField.PHONE }
            }
        if (headerCells != null) {
            return FieldColumns(
                name = headerCells.indexOfFirst { identifyHeaderField(it) == HeaderField.NAME }.takeIf { it >= 0 },
                phone = headerCells.indexOfFirst { identifyHeaderField(it) == HeaderField.PHONE }.takeIf { it >= 0 },
                remark = headerCells.indexOfFirst { identifyHeaderField(it) == HeaderField.REMARK }.takeIf { it >= 0 },
            )
        }

        val columnCount = cellTexts.maxOfOrNull(List<String>::size) ?: return FieldColumns()
        val phoneEvidence = (0 until columnCount)
            .map { column ->
                column to cellTexts.count { row ->
                    PhoneRecognition.candidates(row.getOrNull(column).orEmpty()).isNotEmpty()
                }
            }
            .filter { (_, score) -> score > 0 }
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first })
        val phoneColumn = phoneEvidence.firstOrNull()?.first
        val nameColumn = (0 until columnCount)
            .filter { it != phoneColumn }
            .map { column ->
                val score = cellTexts.sumOf { row ->
                    val nameScore = ImportFieldRules.nameColumnScore(row.getOrNull(column).orEmpty())
                    if (nameScore == 0) {
                        0
                    } else {
                        val sameRowPhone = phoneColumn?.let { phoneIndex ->
                            PhoneRecognition.candidates(row.getOrNull(phoneIndex).orEmpty()).isNotEmpty()
                        } == true
                        nameScore + if (sameRowPhone) SAME_ROW_PHONE_BONUS else 0
                    }
                }
                column to score
            }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<Int, Int>> { it.second }
                    .thenBy { pair -> phoneColumn?.let { abs(pair.first - it) } ?: pair.first }
                    .thenBy { pair -> if (phoneColumn != null && pair.first < phoneColumn) 0 else 1 }
                    .thenBy { it.first },
            )
            .firstOrNull()
            ?.first
        return FieldColumns(name = nameColumn, phone = phoneColumn)
    }

    private enum class HeaderField { NAME, PHONE, REMARK }

    private fun identifyHeaderField(value: String): HeaderField? {
        val token = value.lowercase().filter(Char::isLetterOrDigit)
        return when (token) {
            "姓名", "客户姓名", "客户名称", "联系人", "名称", "name" -> HeaderField.NAME
            "手机", "手机号", "客户手机号", "手机号码", "联系手机", "电话", "客户电话", "电话号码", "联系电话", "联系电话号码", "联系方式", "phone", "mobile", "tel" -> HeaderField.PHONE
            "备注", "客户备注", "备注信息", "说明", "其他", "note", "remark" -> HeaderField.REMARK
            else -> null
        }
    }

    private fun inferTextColumnStarts(lines: List<OcrTextLine>, imageWidth: Int): List<Int> {
        if (lines.isEmpty()) return emptyList()
        val heights = lines.map { (it.bottom - it.top).coerceAtLeast(1) }.sorted()
        val medianHeight = heights[heights.size / 2]
        val mergeDistance = max(18, max((imageWidth * 0.035f).roundToInt(), (medianHeight * 1.4f).roundToInt()))
        val clusters = mutableListOf<MutableList<Int>>()
        lines.map(OcrTextLine::left).sorted().forEach { left ->
            val current = clusters.lastOrNull()
            if (current == null || left - current.average().roundToInt() > mergeDistance) {
                clusters += mutableListOf(left)
            } else {
                current += left
            }
        }
        return clusters.map { cluster -> cluster.average().roundToInt() }
    }

    private fun extractName(text: String): String {
        return ImportFieldRules.findPersonName(text).orEmpty()
    }

    private const val SAME_ROW_PHONE_BONUS = 5
    private const val MAX_HEADER_SCAN_ROWS = 5
}
