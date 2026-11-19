package com.qiongge.callassistant.domain.importing

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

data class TabularFileData(
    val rows: List<List<String>>,
    val nameColumn: Int,
    val phoneColumn: Int,
    val headers: List<String>,
    val phoneColumns: List<Int>,
)

object TabularFileReader {
    const val MAX_FILE_BYTES = 20 * 1024 * 1024
    private const val MAX_UNCOMPRESSED_BYTES = 32 * 1024 * 1024
    private const val MAX_ROWS = 10_000
    private const val MAX_COLUMNS = 64

    fun read(displayName: String, bytes: ByteArray): TabularFileData {
        if (bytes.isEmpty()) throw TabularImportException("文件是空的。")
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val sourceRows = when (extension) {
            "csv" -> parseCsv(decodeText(bytes))
            "xlsx" -> parseXlsx(bytes)
            "xls" -> throw TabularImportException("旧版 .xls 暂不支持，请用 Excel 另存为 .xlsx。")
            else -> throw TabularImportException("文件格式不支持，请选择 .csv 或 .xlsx。")
        }
        if (sourceRows.size > MAX_ROWS + 1) throw TabularImportException("文件超过 $MAX_ROWS 行，请拆分后导入。")
        val firstDataIndex = sourceRows.indexOfFirst { row -> row.any(String::isNotBlank) }
        if (firstDataIndex < 0) throw TabularImportException("文件里没有名单内容。")

        val firstRow = sourceRows[firstDataIndex].take(MAX_COLUMNS)
        val headerName = findHeader(firstRow, NAME_HEADERS)
        val headerPhone = firstRow.indexOfFirst(::isPhoneHeader)
        val hasHeader = headerName >= 0 || headerPhone >= 0 || PropertyFields.hasHeader(firstRow)
        val rows = sourceRows.drop(firstDataIndex + if (hasHeader) 1 else 0)
            .map { it.take(MAX_COLUMNS) }
        if (rows.isEmpty()) throw TabularImportException("文件只有表头，没有客户行。")

        val columnCount = maxOf(firstRow.size, rows.maxOfOrNull { it.size } ?: 0).coerceAtMost(MAX_COLUMNS)
        val phoneColumn = headerPhone.takeIf { it in 0 until columnCount }
            ?: guessPhoneColumn(rows, columnCount)
        val nameColumn = headerName.takeIf { it in 0 until columnCount }
            ?: guessNameColumn(rows, columnCount, phoneColumn)
        val headers = firstRow.takeIf { hasHeader }.orEmpty()
        val phoneColumns = (listOf(phoneColumn) + (0 until columnCount).filter { column ->
            column != nameColumn && (
                isPhoneHeader(headers.getOrNull(column).orEmpty()) ||
                    rows.any { row -> PhoneRecognition.isPhoneCell(row.getOrNull(column).orEmpty()) }
                )
        }).distinct()
        return TabularFileData(rows, nameColumn, phoneColumn, headers, phoneColumns)
    }

    private fun decodeText(bytes: ByteArray): String {
        val (charset, offset) = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                StandardCharsets.UTF_8 to 3
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                StandardCharsets.UTF_16LE to 2
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                StandardCharsets.UTF_16BE to 2
            else -> StandardCharsets.UTF_8 to 0
        }
        val content = bytes.copyOfRange(offset, bytes.size)
        return try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content))
                .toString()
        } catch (_: CharacterCodingException) {
            if (offset != 0 || charset != StandardCharsets.UTF_8) {
                throw TabularImportException("文件编码无法识别，请另存为 UTF-8 CSV。")
            }
            runCatching {
                Charset.forName("GB18030").newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString()
            }.getOrElse { throw TabularImportException("文件编码无法识别，请另存为 UTF-8 CSV。") }
        }
    }

    private fun parseCsv(text: String): List<List<String>> {
        val delimiter = detectDelimiter(text)
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var rowTouched = false
        var index = 0

        fun finishCell() {
            row += cell.toString()
            cell.setLength(0)
        }

        fun finishRow() {
            finishCell()
            rows += row.toList()
            row.clear()
            rowTouched = false
        }

        while (index < text.length) {
            val character = text[index]
            when {
                character == '"' && quoted && index + 1 < text.length && text[index + 1] == '"' -> {
                    cell.append('"')
                    index += 1
                    rowTouched = true
                }
                character == '"' && (quoted || cell.isEmpty()) -> {
                    quoted = !quoted
                    rowTouched = true
                }
                character == delimiter && !quoted -> {
                    finishCell()
                    rowTouched = true
                }
                (character == '\n' || character == '\r') && !quoted -> {
                    finishRow()
                    if (character == '\r' && index + 1 < text.length && text[index + 1] == '\n') index += 1
                }
                else -> {
                    cell.append(character)
                    rowTouched = true
                }
            }
            index += 1
        }
        if (quoted) throw TabularImportException("CSV 引号没有闭合，请检查文件内容。")
        if (rowTouched || row.isNotEmpty() || cell.isNotEmpty()) finishRow()
        return rows
    }

    private fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull(String::isNotBlank).orEmpty()
        var quoted = false
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        var index = 0
        while (index < firstLine.length) {
            val character = firstLine[index]
            if (character == '"' && quoted && index + 1 < firstLine.length && firstLine[index + 1] == '"') {
                index += 1
            } else if (character == '"') {
                quoted = !quoted
            } else if (!quoted && character in counts.keys) {
                counts[character] = counts.getValue(character) + 1
            }
            index += 1
        }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    private fun parseXlsx(bytes: ByteArray): List<List<String>> {
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0
        try {
            ZipInputStream(ByteArrayInputStream(bytes), StandardCharsets.UTF_8).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val content = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                                throw TabularImportException("Excel 文件展开后过大，无法安全导入。")
                            }
                            content.write(buffer, 0, count)
                        }
                        entries[entry.name] = content.toByteArray()
                    }
                    zip.closeEntry()
                }
            }
        } catch (exception: TabularImportException) {
            throw exception
        } catch (_: Exception) {
            throw TabularImportException("Excel 文件损坏或格式不受支持。")
        }

        val workbook = entries["xl/workbook.xml"] ?: throw TabularImportException("无法读取 Excel 工作簿。")
        val relationships = entries["xl/_rels/workbook.xml.rels"]
            ?: throw TabularImportException("无法读取 Excel 工作表索引。")
        val relationshipId = firstSheetRelationshipId(workbook)
        val target = relationshipTarget(relationships, relationshipId)
        val sheetPath = resolveSheetPath(target)
        val sheet = entries[sheetPath] ?: throw TabularImportException("无法读取 Excel 的第一个工作表。")
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        return parseWorksheet(sheet, sharedStrings)
    }

    private fun firstSheetRelationshipId(workbook: ByteArray): String {
        val parser = xmlParser(workbook)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val namespaceValue = parser.getAttributeValue(
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                    "id",
                )
                val fallback = (0 until parser.attributeCount)
                    .firstNotNullOfOrNull { index ->
                        parser.getAttributeName(index).takeIf { it.endsWith(":id") || it == "id" }
                            ?.let { parser.getAttributeValue(index) }
                    }
                return namespaceValue ?: fallback ?: throw TabularImportException("Excel 第一个工作表没有关系索引。")
            }
            parser.next()
        }
        throw TabularImportException("Excel 工作簿里没有工作表。")
    }

    private fun relationshipTarget(relationships: ByteArray, relationshipId: String): String {
        val parser = xmlParser(relationships)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                if (id == relationshipId) {
                    return parser.getAttributeValue(null, "Target")
                        ?: throw TabularImportException("Excel 工作表路径缺失。")
                }
            }
            parser.next()
        }
        throw TabularImportException("Excel 第一个工作表关系缺失。")
    }

    private fun resolveSheetPath(target: String): String {
        val resolved = runCatching {
            java.net.URI.create("xl/").resolve(target).normalize().path.removePrefix("/")
        }.getOrElse { "" }
        if (!resolved.startsWith("xl/") || ".." in resolved.split('/')) {
            throw TabularImportException("Excel 工作表路径无效。")
        }
        return resolved
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val parser = xmlParser(bytes)
        var inItem = false
        var inText = false
        var current = StringBuilder()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> {
                        inItem = true
                        current = StringBuilder()
                    }
                    "t" -> if (inItem) inText = true
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem && inText) current.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "si" -> {
                        strings += current.toString()
                        inItem = false
                        inText = false
                    }
                }
            }
            parser.next()
        }
        return strings
    }

    private fun parseWorksheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val parser = xmlParser(bytes)
        val rows = mutableListOf<List<String>>()
        var cells = mutableMapOf<Int, String>()
        var columnCount = 0
        var cellColumn = -1
        var cellType = ""
        var cellValue = StringBuilder()
        var captureText = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> cells = mutableMapOf()
                    "c" -> {
                        cellColumn = columnIndex(parser.getAttributeValue(null, "r").orEmpty())
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        cellValue = StringBuilder()
                    }
                    "v", "t" -> captureText = true
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (captureText) cellValue.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> captureText = false
                    "c" -> if (cellColumn >= 0) {
                        val rawValue = cellValue.toString()
                        val value = if (cellType == "s") {
                            rawValue.toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty()
                        } else {
                            rawValue
                        }
                        cells[cellColumn] = value
                        columnCount = maxOf(columnCount, cellColumn + 1)
                    }
                    "row" -> {
                        val width = maxOf(columnCount, (cells.keys.maxOrNull() ?: -1) + 1).coerceAtMost(MAX_COLUMNS)
                        rows += List(width) { cells[it].orEmpty() }
                        if (rows.size > MAX_ROWS) throw TabularImportException("文件超过 $MAX_ROWS 行，请拆分后导入。")
                    }
                }
            }
            parser.next()
        }
        return rows
    }

    private fun xmlParser(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        setInput(ByteArrayInputStream(bytes), "UTF-8")
    }

    private fun columnIndex(reference: String): Int {
        var index = 0
        var letters = 0
        for (character in reference) {
            if (character !in 'A'..'Z' && character !in 'a'..'z') break
            val upper = character.uppercaseChar()
            index = index * 26 + (upper - 'A' + 1)
            letters += 1
        }
        return if (letters == 0) -1 else index - 1
    }

    private fun findHeader(headers: List<String>, candidates: Set<String>): Int =
        headers.indexOfFirst { header ->
            val normalized = header.lowercase().filter(Char::isLetterOrDigit)
            normalized in candidates || candidates.any { candidate -> normalized.contains(candidate) }
        }

    private fun guessPhoneColumn(rows: List<List<String>>, columnCount: Int): Int {
        if (columnCount == 0) return -1
        val scores = (0 until columnCount).map { column ->
            rows.take(200).count { row ->
                row.getOrNull(column).orEmpty().let { cell ->
                    PhoneRecognition.candidates(cell).any(PhoneRecognition::isMainlandMobile) ||
                        PhoneRecognition.isMainlandMobile(cell)
                }
            }
        }
        val best = scores.indices.maxByOrNull { scores[it] } ?: -1
        if (best >= 0 && scores[best] > 0) return best
        return if (columnCount > 1) 1 else -1
    }

    private fun guessNameColumn(rows: List<List<String>>, columnCount: Int, phoneColumn: Int): Int {
        if (columnCount == 0) return -1
        val candidates = (0 until columnCount).filter { it != phoneColumn }
        if (candidates.isEmpty()) return -1
        val scores = candidates.associateWith { column ->
            rows.take(200).count { row ->
                val value = row.getOrNull(column).orEmpty().trim()
                value.any { it in '\u4E00'..'\u9FFF' || it.isLetter() } &&
                    PhoneRecognition.candidates(value).isEmpty()
            }
        }
        val best = candidates.maxByOrNull { scores[it] ?: 0 } ?: return -1
        return best.takeIf { (scores[it] ?: 0) > 0 } ?: candidates.first()
    }

    private val NAME_HEADERS = setOf("姓名", "客户姓名", "客户名称", "联系人", "名称", "客户", "name", "customername", "contactname")
    private fun isPhoneHeader(value: String): Boolean {
        val header = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFKC)
            .lowercase().replace(Regex("""[\s_-]+"""), "")
        return header.trimEnd(Char::isDigit) in PHONE_HEADERS ||
            header in setOf("备用电话", "备用手机号", "第二联系电话", "业主电话", "业主手机")
    }

    private val PHONE_HEADERS = setOf("手机", "手机号", "手机号码", "电话", "电话号码", "联系电话", "联系方式", "phone", "mobile", "tel", "telephone")
}

class TabularImportException(message: String) : Exception(message)
