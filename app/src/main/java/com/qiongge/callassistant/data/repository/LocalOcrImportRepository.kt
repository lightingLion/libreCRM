package com.qiongge.callassistant.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.qiongge.callassistant.data.local.AppDatabase
import com.qiongge.callassistant.data.local.dao.ImportDraftDao
import com.qiongge.callassistant.data.local.entity.ContactStatus
import com.qiongge.callassistant.data.local.entity.CustomerEntity
import com.qiongge.callassistant.data.local.entity.CustomerListEntity
import com.qiongge.callassistant.data.local.entity.ImportDraftEntity
import com.qiongge.callassistant.data.local.entity.ImportDraftImageEntity
import com.qiongge.callassistant.data.local.entity.ImportDraftRowEntity
import com.qiongge.callassistant.data.local.entity.ImportSkipReason
import com.qiongge.callassistant.data.local.entity.ImportRecordEntity
import com.qiongge.callassistant.data.local.entity.ListMembershipEntity
import com.qiongge.callassistant.data.local.entity.TaskProgressEntity
import com.qiongge.callassistant.data.local.model.ImportDraftSnapshot
import com.qiongge.callassistant.domain.importing.PhoneRecognition
import com.qiongge.callassistant.domain.importing.TabularFileReader
import com.qiongge.callassistant.domain.importing.TabularImportException
import com.qiongge.callassistant.ocr.engine.LocalPaddleOcrEngine
import com.qiongge.callassistant.ocr.layout.OcrLayoutRebuilder
import com.qiongge.callassistant.ocr.layout.OcrSourceCellCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

data class OcrImportProgress(
    val currentImage: Int,
    val totalImages: Int,
)

data class OcrImportResult(
    val imageCount: Int,
    val failedImageCount: Int,
    val recognizedRowCount: Int,
    val skippedRowCount: Int,
)

data class TabularImportResult(
    val fileName: String,
    val readyRowCount: Int,
    val skippedRowCount: Int,
)

class LocalOcrImportRepository(
    context: Context,
    private val database: AppDatabase,
) {
    private val appContext = context.applicationContext
    private val dao: ImportDraftDao = database.importDraftDao()
    private val customerDao = database.customerDao()
    private val taskDao = database.taskDao()
    private val recognizer = LocalPaddleOcrEngine(appContext)
    private val draftDirectory = File(appContext.filesDir, "ocr-import-draft")

    fun observeDraft(): Flow<ImportDraftSnapshot?> = combine(
        dao.observeDraft(),
        dao.observeImages(),
        dao.observeRows(),
    ) { draft, images, rows ->
        draft?.let { ImportDraftSnapshot(it, images, rows) }
    }

    suspend fun cleanupOrphanedFiles() = withContext(Dispatchers.IO) {
        if (dao.getDraft() == null) draftDirectory.deleteRecursively()
    }

    suspend fun ensureDraft(defaultTitle: String) = withContext(Dispatchers.IO) {
        if (dao.getDraft() == null) {
            if (!draftDirectory.exists() && !draftDirectory.mkdirs()) {
                throw ImportWorkflowException("无法创建本机草稿空间。")
            }
            dao.insertDraft(ImportDraftEntity(title = defaultTitle))
        }
    }

    suspend fun appendImages(
        uris: List<Uri>,
        defaultTitle: String,
        onProgress: (OcrImportProgress) -> Unit,
    ): OcrImportResult = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty())
        ensureDraft(defaultTitle)
        val existingImages = dao.getImages()
        if (existingImages.size + uris.size > LocalPaddleOcrEngine.MAX_IMAGES) {
            throw ImportWorkflowException("一份草稿最多处理 ${LocalPaddleOcrEngine.MAX_IMAGES} 张图片。")
        }
        var failedCount = 0
        val addedImageIds = mutableSetOf<Long>()
        uris.forEachIndexed { index, uri ->
            onProgress(OcrImportProgress(index + 1, uris.size))
            val imageOrder = existingImages.size + index
            try {
                val prepared = recognizer.prepareImage(uri, imageOrder, draftDirectory)
                val imageId = dao.insertImage(
                    ImportDraftImageEntity(
                        displayName = prepared.displayName,
                        localPath = prepared.file.absolutePath,
                        sortOrder = imageOrder,
                        width = prepared.width,
                        height = prepared.height,
                    ),
                )
                addedImageIds += imageId
                try {
                    val rows = recognizeAndRebuild(prepared.file, imageId, prepared.width, prepared.height)
                    if (rows.isNotEmpty()) dao.insertRows(rows)
                } catch (exception: kotlinx.coroutines.CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    failedCount += 1
                }
            } catch (exception: kotlinx.coroutines.CancellationException) {
                throw exception
            } catch (exception: Exception) {
                failedCount += 1
            }
        }
        val refreshedRows = reEvaluateDuplicatePhoneSkips()
        val newImageRows = refreshedRows.filter { it.sourceImageId in addedImageIds }
        OcrImportResult(
            imageCount = uris.size,
            failedImageCount = failedCount,
            recognizedRowCount = newImageRows.count { !it.skipped },
            skippedRowCount = newImageRows.count { it.skipped },
        )
    }

    suspend fun retryImage(imageId: Long): Int = withContext(Dispatchers.IO) {
        val image = dao.getImage(imageId) ?: throw ImportWorkflowException("找不到这张图片。")
        val file = File(image.localPath)
        if (!file.isFile) throw ImportWorkflowException("本机草稿图片已丢失，请重新选择。")
        dao.deleteRowsForImage(imageId)
        reEvaluateDuplicatePhoneSkips()
        val rows = recognizeAndRebuild(file, image.id, image.width, image.height)
        if (rows.isNotEmpty()) dao.insertRows(rows)
        reEvaluateDuplicatePhoneSkips().count { it.sourceImageId == imageId && !it.skipped }
    }

    suspend fun importTabularFile(uri: Uri): TabularImportResult = withContext(Dispatchers.IO) {
        if (dao.getRows().isNotEmpty() || dao.getImages().isNotEmpty()) {
            throw ImportWorkflowException("先完成或删除当前导入草稿，再导入 CSV 或 Excel 文件。")
        }
        val fileName = queryDisplayName(uri)
        val bytes = appContext.contentResolver.openInputStream(uri)?.use(::readBounded)
            ?: throw ImportWorkflowException("无法读取所选文件。")
        val table = try {
            TabularFileReader.read(fileName, bytes)
        } catch (exception: TabularImportException) {
            throw ImportWorkflowException(exception.message ?: "文件内容无法识别。")
        }
        val nextOrder = dao.nextRowOrder()
        val rows = table.rows.mapIndexed { index, cells ->
            val name = cells.getOrNull(table.nameColumn).orEmpty().trim().take(40)
            val phoneText = cells.getOrNull(table.phoneColumn).orEmpty().trim()
            val phone = PhoneRecognition.candidates(phoneText)
                .firstOrNull()
                ?.let(PhoneRecognition::normalizeForEditing)
                ?: PhoneRecognition.normalizeForEditing(phoneText).take(32)
            ImportDraftRowEntity(
                sourceImageId = null,
                sortOrder = nextOrder + index,
                name = name,
                phone = phone,
                sourceText = cells.joinToString(" | ") { it.trim() },
                sourceCells = OcrSourceCellCodec.encode(cells),
                sourceFileName = fileName,
                sourceLeft = null,
                sourceTop = null,
                sourceRight = null,
                sourceBottom = null,
                confidence = null,
                reviewed = name.isNotBlank() && PhoneRecognition.isMainlandMobile(phone),
                skipped = name.isBlank() || phone.isBlank(),
                duplicateMode = null,
                duplicateCustomerId = null,
                skipReason = if (name.isBlank() || phone.isBlank()) ImportSkipReason.INCOMPLETE else null,
            )
        }
        val title = fileName.substringBeforeLast('.', fileName).take(60).ifBlank { "导入名单" }
        ensureDraft(title)
        database.withTransaction {
            dao.insertRows(rows)
            dao.getDraft()?.let { dao.updateDraft(it.copy(title = title, updatedAt = System.currentTimeMillis())) }
        }
        val refreshedRows = reEvaluateDuplicatePhoneSkips()
        val fileRows = refreshedRows.filter { it.sourceFileName == fileName }
        TabularImportResult(
            fileName = fileName,
            readyRowCount = fileRows.count { !it.skipped },
            skippedRowCount = fileRows.count { it.skipped },
        )
    }

    suspend fun updateTitle(title: String) = withContext(Dispatchers.IO) {
        dao.getDraft()?.let { dao.updateDraft(it.copy(title = title, updatedAt = System.currentTimeMillis())) }
    }

    suspend fun updateRow(row: ImportDraftRowEntity) = withContext(Dispatchers.IO) {
        val normalized = row.copy(
            reviewed = !row.skipped && row.name.isNotBlank() && PhoneRecognition.isMainlandMobile(row.phone),
            skipReason = if (row.skipped) {
                row.skipReason ?: if (row.name.isNotBlank() && row.phone.isNotBlank()) {
                    ImportSkipReason.MANUAL
                } else {
                    ImportSkipReason.INCOMPLETE
                }
            } else {
                null
            },
        )
        database.withTransaction {
            dao.updateRow(normalized)
            dao.getDraft()?.let { dao.updateDraft(it.copy(updatedAt = System.currentTimeMillis())) }
        }
        reEvaluateDuplicatePhoneSkips()
    }

    suspend fun applyFieldMapping(imageId: Long, nameColumn: Int, phoneColumn: Int): Int = withContext(Dispatchers.IO) {
        applyFieldMappingToRows(
            rows = dao.getRows().filter { it.sourceImageId == imageId },
            nameColumn = nameColumn,
            phoneColumn = phoneColumn,
            sourceLabel = "这张图片",
        )
    }

    suspend fun applyFileFieldMapping(fileName: String, nameColumn: Int, phoneColumn: Int): Int = withContext(Dispatchers.IO) {
        applyFieldMappingToRows(
            rows = dao.getRows().filter { it.sourceFileName == fileName },
            nameColumn = nameColumn,
            phoneColumn = phoneColumn,
            sourceLabel = "这个文件",
        )
    }

    private suspend fun applyFieldMappingToRows(
        rows: List<ImportDraftRowEntity>,
        nameColumn: Int,
        phoneColumn: Int,
        sourceLabel: String,
    ): Int {
        if (nameColumn == phoneColumn) throw ImportWorkflowException("姓名列和手机号列不能相同。")
        val rowsWithCells = rows.mapNotNull { row ->
            OcrSourceCellCodec.decode(row.sourceCells)?.let { row to it }
        }
        if (rowsWithCells.isEmpty()) throw ImportWorkflowException("${sourceLabel}没有可映射的识别列，请重新读取文件。")

        val mappedRows = rowsWithCells.map { (row, cells) ->
            val nameCell = cells.getOrNull(nameColumn).orEmpty().trim()
            val phoneCell = cells.getOrNull(phoneColumn).orEmpty().trim()
            val headerRow = isFieldHeader(nameCell) || isFieldHeader(phoneCell)
            val name = if (headerRow) "" else nameCell.take(40)
            val phoneText = if (headerRow) "" else phoneCell
            val phone = PhoneRecognition.candidates(phoneText)
                .firstOrNull()
                ?.let(PhoneRecognition::normalizeForEditing)
                ?: PhoneRecognition.normalizeForEditing(phoneText).take(32)
            val preserveManualSkip = row.skipped && (
                row.skipReason == ImportSkipReason.MANUAL ||
                    (row.skipReason == null && row.name.isNotBlank() && row.phone.isNotBlank())
                )
            val incomplete = headerRow || name.isBlank() || phone.isBlank()

            row.copy(
                name = name,
                phone = phone,
                reviewed = name.isNotBlank() && PhoneRecognition.isMainlandMobile(phone) && !preserveManualSkip,
                skipped = preserveManualSkip || incomplete,
                duplicateMode = null,
                duplicateCustomerId = null,
                skipReason = when {
                    preserveManualSkip -> ImportSkipReason.MANUAL
                    incomplete -> ImportSkipReason.INCOMPLETE
                    else -> null
                },
            )
        }
        database.withTransaction {
            mappedRows.forEach { row -> dao.updateRow(row) }
            dao.getDraft()?.let { dao.updateDraft(it.copy(updatedAt = System.currentTimeMillis())) }
        }
        reEvaluateDuplicatePhoneSkips()
        return mappedRows.size
    }

    suspend fun addManualRow(): Long = withContext(Dispatchers.IO) {
        ensureDraft("新导入名单")
        dao.insertRow(
            ImportDraftRowEntity(
                sourceImageId = null,
                sortOrder = dao.nextRowOrder(),
                name = "",
                phone = "",
                sourceText = "手动补充",
                sourceLeft = null,
                sourceTop = null,
                sourceRight = null,
                sourceBottom = null,
                confidence = null,
                reviewed = false,
                skipped = false,
                duplicateMode = null,
                duplicateCustomerId = null,
                skipReason = null,
            ),
        )
    }

    suspend fun deleteRow(rowId: Long) = withContext(Dispatchers.IO) {
        dao.deleteRow(rowId)
        reEvaluateDuplicatePhoneSkips()
    }

    suspend fun discardDraft() = withContext(Dispatchers.IO) {
        database.withTransaction { dao.deleteDraft() }
        draftDirectory.deleteRecursively()
    }

    suspend fun prepareImportReview() = withContext(Dispatchers.IO) {
        reEvaluateDuplicatePhoneSkips()
        Unit
    }

    suspend fun commitDraft(): Long = withContext(Dispatchers.IO) {
        reEvaluateDuplicatePhoneSkips()
        val draft = dao.getDraft() ?: throw ImportWorkflowException("没有待确认的导入草稿。")
        if (draft.title.isBlank()) throw ImportWorkflowException("请先填写名单名称。")
        val allRows = dao.getRows()
        val includedRows = allRows.filterNot(ImportDraftRowEntity::skipped)
            .filter { it.name.isNotBlank() && it.phone.isNotBlank() }
        if (includedRows.isEmpty()) throw ImportWorkflowException("请至少保留一位客户再导入。")
        if (includedRows.any { !PhoneRecognition.isMainlandMobile(it.phone) }) {
            throw ImportWorkflowException("请修正仍保留行中的无效手机号，空姓名或手机号的行会自动跳过。")
        }

        val customersByPhone = customerDao.getAllOnce()
            .mapNotNull { customer -> PhoneRecognition.comparableDigits(customer.phone)?.let { it to customer } }
            .groupBy({ it.first }, { it.second })
        val draftPhoneCounts = includedRows
            .mapNotNull { row -> PhoneRecognition.comparableDigits(row.phone)?.let { it to row } }
            .groupingBy { it.first }
            .eachCount()
        val explicitlyIndependentPhones = includedRows
            .filter { it.duplicateMode == CREATE_NEW }
            .mapNotNull { PhoneRecognition.comparableDigits(it.phone) }
            .toSet()
        val listId = database.withTransaction {
            val now = System.currentTimeMillis()
            val newListId = taskDao.insertList(CustomerListEntity(title = draft.title.trim(), createdAt = now, updatedAt = now))
            var nextOrder = 0
            val linkedCustomerIds = mutableSetOf<Long>()
            includedRows.forEach { row ->
                val normalizedPhone = PhoneRecognition.comparableDigits(row.phone)
                    ?: throw ImportWorkflowException("名单中存在无效手机号。")
                val matches = customersByPhone[normalizedPhone].orEmpty()
                if (draftPhoneCounts[normalizedPhone].orZero() > 1 &&
                    normalizedPhone !in explicitlyIndependentPhones && row.duplicateMode == null
                ) {
                    throw ImportWorkflowException("草稿中有相同号码，请明确选择保留重复行，或恢复为独立客户。")
                }
                val customerId = when {
                    matches.isEmpty() -> {
                        if (row.duplicateMode == REUSE_EXISTING) {
                            throw ImportWorkflowException("所选的已有线索已不存在，请重新核对重复项。")
                        }
                        customerDao.insert(
                            CustomerEntity(
                                name = row.name.trim(),
                                phone = normalizedPhone,
                                status = ContactStatus.NOT_CONTACTED,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                    }
                    row.duplicateMode == CREATE_NEW -> customerDao.insert(
                        CustomerEntity(
                            name = row.name.trim(),
                            phone = normalizedPhone,
                            status = ContactStatus.NOT_CONTACTED,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    row.duplicateMode == REUSE_EXISTING -> {
                        val selected = matches.firstOrNull { it.id == row.duplicateCustomerId }
                            ?: throw ImportWorkflowException("所选的已有线索与号码不匹配，请重新核对。")
                        selected.id
                    }
                    else -> throw ImportWorkflowException("发现已有相同号码，请选择关联已有线索或新建独立客户。")
                }
                if (!linkedCustomerIds.add(customerId)) {
                    throw ImportWorkflowException("同一已有客户被重复加入名单，请跳过重复行后再导入。")
                }
                val membershipId = taskDao.insertMembership(
                    ListMembershipEntity(listId = newListId, customerId = customerId, sortOrder = nextOrder++),
                )
                if (nextOrder == 1) {
                    taskDao.saveProgress(TaskProgressEntity(listId = newListId, currentMembershipId = membershipId, updatedAt = now))
                }
            }
            taskDao.insertImportRecord(
                ImportRecordEntity(
                    sourceType = if (allRows.any { it.sourceFileName != null }) "TABULAR_FILE" else "LOCAL_OCR_IMAGE",
                    sourceName = allRows.mapNotNull { it.sourceFileName }.distinct().singleOrNull() ?: "本地图片",
                    recognizedCount = allRows.size,
                    confirmedCount = linkedCustomerIds.size,
                    createdAt = now,
                ),
            )
            dao.deleteDraft()
            newListId
        }
        draftDirectory.deleteRecursively()
        listId
    }

    private suspend fun reEvaluateDuplicatePhoneSkips(): List<ImportDraftRowEntity> {
        val seenPhones = mutableSetOf<String>()
        val rows = dao.getRows()
        val refreshedRows = rows.map { row ->
            val complete = row.name.isNotBlank() && row.phone.isNotBlank()
            val previousReason = when {
                row.skipReason != null -> row.skipReason
                row.skipped && complete -> ImportSkipReason.MANUAL
                row.skipped -> ImportSkipReason.INCOMPLETE
                else -> null
            }

            when {
                previousReason == ImportSkipReason.MANUAL -> row.copy(
                    skipped = true,
                    skipReason = ImportSkipReason.MANUAL,
                )

                !complete -> when {
                    previousReason == ImportSkipReason.DUPLICATE_PHONE ||
                        previousReason == ImportSkipReason.INCOMPLETE -> row.copy(
                        skipped = true,
                        duplicateMode = null,
                        duplicateCustomerId = null,
                        skipReason = ImportSkipReason.INCOMPLETE,
                    )

                    row.skipped -> row.copy(skipReason = ImportSkipReason.INCOMPLETE)
                    else -> row.copy(skipped = false, skipReason = null)
                }

                else -> {
                    val phone = PhoneRecognition.comparableDigits(row.phone)
                    when {
                        phone == null && (previousReason == ImportSkipReason.DUPLICATE_PHONE ||
                            previousReason == ImportSkipReason.INCOMPLETE
                        ) -> row.copy(skipped = false, skipReason = null)

                        phone == null -> row

                        row.duplicateMode == CREATE_NEW && !row.skipped -> {
                            seenPhones += phone
                            row.copy(skipped = false, skipReason = null)
                        }

                        seenPhones.add(phone) -> row.copy(skipped = false, skipReason = null)

                        else -> row.copy(
                            skipped = true,
                            duplicateMode = null,
                            duplicateCustomerId = null,
                            skipReason = ImportSkipReason.DUPLICATE_PHONE,
                        )
                    }
                }
            }
        }

        database.withTransaction {
            refreshedRows.filterIndexed { index, row -> row != rows[index] }.forEach { dao.updateRow(it) }
            dao.getDraft()?.let { dao.updateDraft(it.copy(updatedAt = System.currentTimeMillis())) }
        }
        return refreshedRows
    }

    private suspend fun recognizeAndRebuild(
        file: File,
        imageId: Long,
        width: Int,
        height: Int,
    ): List<ImportDraftRowEntity> {
        val recognized = recognizer.recognize(file)
        val parsed = OcrLayoutRebuilder.rebuild(
            lines = recognized.lines,
            imageWidth = width,
            imageHeight = height,
            grid = recognized.grid,
            structuredRows = recognized.structuredRows,
        )
        var sortOrder = dao.nextRowOrder()
        return parsed.map { row ->
            ImportDraftRowEntity(
                sourceImageId = imageId,
                sortOrder = sortOrder++,
                name = row.name,
                phone = row.phone,
                sourceText = row.sourceText,
                sourceCells = OcrSourceCellCodec.encode(row.cells),
                sourceLeft = row.left,
                sourceTop = row.top,
                sourceRight = row.right,
                sourceBottom = row.bottom,
                confidence = row.confidence,
                reviewed = row.reviewed,
                skipped = row.name.isBlank() || row.phone.isBlank(),
                duplicateMode = null,
                duplicateCustomerId = null,
                skipReason = if (row.name.isBlank() || row.phone.isBlank()) ImportSkipReason.INCOMPLETE else null,
            )
        }
    }

    companion object {
        const val CREATE_NEW = "CREATE_NEW"
        const val REUSE_EXISTING = "REUSE_EXISTING"
    }

    private fun queryDisplayName(uri: Uri): String = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf(String::isNotBlank)
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
        ?: "名单.csv"

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > TabularFileReader.MAX_FILE_BYTES) {
                throw ImportWorkflowException("文件超过 20 MB，请拆分后再导入。")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

private fun isFieldHeader(value: String): Boolean {
    val token = value.lowercase().filter(Char::isLetterOrDigit)
    return token in setOf(
        "姓名",
        "客户姓名",
        "客户名称",
        "联系人",
        "名称",
        "name",
        "手机",
        "手机号",
        "手机号码",
        "电话",
        "电话号码",
        "联系电话",
        "联系方式",
        "phone",
        "mobile",
        "tel",
    )
}

private fun Int?.orZero(): Int = this ?: 0

class ImportWorkflowException(message: String) : Exception(message)
