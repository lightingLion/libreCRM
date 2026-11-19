package com.qiongge.callassistant.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.qiongge.callassistant.data.local.AppDatabase
import com.qiongge.callassistant.data.local.dao.CustomerDao
import com.qiongge.callassistant.data.local.entity.ContactStatus
import com.qiongge.callassistant.data.local.model.CustomerExportRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class DataExportFormat { CSV, XLSX }

class LocalDataManagementRepository(
    context: Context,
    private val database: AppDatabase,
) {
    private val contentResolver = context.applicationContext.contentResolver
    private val customerDao: CustomerDao = database.customerDao()

    suspend fun exportCustomers(uri: Uri, format: DataExportFormat): Int = withContext(Dispatchers.IO) {
        val rows = customerDao.getExportRows()
        val output = contentResolver.openOutputStream(uri, "wt")
            ?: throw ImportWorkflowException("无法创建导出文件。")
        output.use { stream ->
            when (format) {
                DataExportFormat.CSV -> writeCsv(stream, rows)
                DataExportFormat.XLSX -> writeXlsx(stream, rows)
            }
        }
        rows.size
    }

    suspend fun exportCsvToDirectory(directory: Uri, fileName: String): Int = withContext(Dispatchers.IO) {
        val document = DocumentsContract.buildDocumentUriUsingTree(directory, DocumentsContract.getTreeDocumentId(directory))
        val file = DocumentsContract.createDocument(contentResolver, document, "text/csv", fileName)
            ?: throw ImportWorkflowException("无法在默认目录创建 CSV，请重新选择目录或另存。")
        try {
            exportCustomers(file, DataExportFormat.CSV)
        } catch (exception: Exception) {
            withContext(NonCancellable) {
                runCatching { DocumentsContract.deleteDocument(contentResolver, file) }
            }
            throw exception
        }
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }

    private fun writeCsv(output: OutputStream, rows: List<CustomerExportRow>) {
        BufferedWriter(OutputStreamWriter(output, UTF_8), 32 * 1024).use { writer ->
            writer.write('\uFEFF'.code)
            writeCsvRow(writer, HEADERS)
            rows.forEach { row ->
                writeCsvRow(
                    writer,
                    listOf(
                        row.name,
                        row.phone,
                        row.building.orEmpty(),
                        row.room.orEmpty(),
                        row.customResult ?: ContactStatus.valueOf(row.status).labelForExport(),
                        row.note.orEmpty(),
                        row.taskNames.orEmpty(),
                        formatTimestamp(row.updatedAt),
                    ),
                )
            }
            writer.flush()
        }
    }

    private fun writeCsvRow(writer: BufferedWriter, cells: List<String>) {
        writer.write(cells.joinToString(",") { cell -> "\"${cell.replace("\"", "\"\"")}\"" })
        writer.write("\r\n")
    }

    private fun writeXlsx(output: OutputStream, rows: List<CustomerExportRow>) {
        if (rows.size + 1 > 1_048_576) throw ImportWorkflowException("Excel 工作表最多支持 1,048,576 行。")
        ZipOutputStream(output, UTF_8).use { zip ->
            writeZipEntry(
                zip,
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            )
            writeZipEntry(
                zip,
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            )
            writeZipEntry(
                zip,
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="客户资料" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            )
            writeZipEntry(
                zip,
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            )
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""".toByteArray(UTF_8),
            )
            var rowNumber = 1
            writeXlsxRow(zip, rowNumber++, HEADERS)
            rows.forEach { row ->
                writeXlsxRow(
                    zip,
                    rowNumber++,
                    listOf(
                        row.name,
                        row.phone,
                        row.building.orEmpty(),
                        row.room.orEmpty(),
                        row.customResult ?: ContactStatus.valueOf(row.status).labelForExport(),
                        row.note.orEmpty(),
                        row.taskNames.orEmpty(),
                        formatTimestamp(row.updatedAt),
                    ),
                )
            }
            zip.write("</sheetData></worksheet>".toByteArray(UTF_8))
            zip.closeEntry()
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(UTF_8))
        zip.closeEntry()
    }

    private fun writeXlsxRow(zip: ZipOutputStream, rowNumber: Int, cells: List<String>) {
        zip.write("<row r=\"$rowNumber\">".toByteArray(UTF_8))
        cells.forEachIndexed { columnIndex, value ->
            val reference = "${columnName(columnIndex)}$rowNumber"
            val safeValue = value.filter { it.code >= 0x20 || it == '\t' || it == '\n' || it == '\r' }
            zip.write(
                "<c r=\"$reference\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escapeXml(safeValue)}</t></is></c>".toByteArray(UTF_8),
            )
        }
        zip.write("</row>".toByteArray(UTF_8))
    }

    private fun columnName(index: Int): String {
        var value = index + 1
        val name = StringBuilder()
        while (value > 0) {
            value -= 1
            name.insert(0, ('A'.code + value % 26).toChar())
            value /= 26
        }
        return name.toString()
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(
                when (character) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&apos;"
                    else -> character.toString()
                },
            )
        }
    }

    private fun formatTimestamp(value: Long): String =
        TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))

    companion object {
        private val HEADERS = listOf("姓名", "手机号", "栋数", "门牌号", "联系状态", "备注", "所属任务", "最后更新时间")
        private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    private fun ContactStatus.labelForExport(): String = when (this) {
        ContactStatus.NOT_CONTACTED -> "未联系"
        ContactStatus.UNREACHED -> "未接通"
        ContactStatus.CONNECTED -> "已接通"
        ContactStatus.INTERESTED -> "有意向"
        ContactStatus.NOT_INTERESTED -> "暂不考虑"
        ContactStatus.DO_NOT_CONTACT -> "不再联系"
        ContactStatus.CUSTOM -> "自定义"
    }
}
