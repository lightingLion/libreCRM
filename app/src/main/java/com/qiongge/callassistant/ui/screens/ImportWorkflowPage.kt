package com.qiongge.callassistant.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.qiongge.callassistant.data.local.entity.CustomerEntity
import com.qiongge.callassistant.data.local.entity.ImportDraftImageEntity
import com.qiongge.callassistant.data.local.entity.ImportDraftRowEntity
import com.qiongge.callassistant.data.local.entity.ImportSkipReason
import com.qiongge.callassistant.domain.importing.PhoneRecognition
import com.qiongge.callassistant.ocr.layout.OcrSourceCellCodec
import com.qiongge.callassistant.ui.MainUiState
import com.qiongge.callassistant.ui.components.CardMotionProfile
import com.qiongge.callassistant.ui.components.EditorialButton
import com.qiongge.callassistant.ui.components.InsetPill
import com.qiongge.callassistant.ui.components.RaisedCard
import com.qiongge.callassistant.ui.theme.G2CornerShape
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImportListPage(
    state: MainUiState,
    onTasksClick: () -> Unit,
    onAddImages: (List<Uri>, List<File>) -> Unit,
    onAddTabularFile: (Uri) -> Unit,
    onUpdateTitle: (String) -> Unit,
    onAddManualRow: () -> Unit,
    onUpdateRow: (ImportDraftRowEntity) -> Unit,
    onDeleteRow: (Long) -> Unit,
    onRetryImage: (Long) -> Unit,
    onApplyFieldMapping: (Long, Int, Int) -> Unit,
    onApplyFileFieldMapping: (String, Int, Int) -> Unit,
    onDiscardDraft: () -> Unit,
    onCommitDraft: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snapshot = state.importDraft
    val imageSlotsRemaining = (MAX_IMAGES - (snapshot?.images?.size ?: 0)).coerceAtLeast(0)
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var previewImage by remember { mutableStateOf<ImportDraftImageEntity?>(null) }
    var fieldMappingImage by remember { mutableStateOf<ImportDraftImageEntity?>(null) }
    var fieldMappingFileName by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var showImportConfirmation by remember { mutableStateOf(false) }
    var localMessage by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = imageSlotsRemaining.coerceAtLeast(1)),
    ) { uris ->
        if (uris.isNotEmpty()) onAddImages(uris, emptyList())
    }
    val tabularFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onAddTabularFile)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingCaptureFile
        val uri = pendingCaptureUri
        pendingCaptureFile = null
        pendingCaptureUri = null
        if (saved && file != null && uri != null) onAddImages(listOf(uri), listOf(file)) else file?.delete()
    }
    val launchCamera: () -> Unit = {
        var createdFile: File? = null
        try {
            val directory = File(context.cacheDir, "ocr-camera")
            if (!directory.exists() && !directory.mkdirs()) error("无法创建临时拍照目录")
            val file = File.createTempFile("capture-", ".jpg", directory)
            createdFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            pendingCaptureFile = file
            pendingCaptureUri = uri
            camera.launch(uri)
        } catch (_: Exception) {
            createdFile?.delete()
            pendingCaptureFile = null
            pendingCaptureUri = null
            localMessage = "相机暂时无法打开，请从相册选择图片。"
        }
    }

    var title by remember(snapshot?.draft?.id) { mutableStateOf(snapshot?.draft?.title.orEmpty()) }
    var showActiveRows by remember(snapshot?.draft?.id) { mutableStateOf(false) }
    var showSkippedRows by remember(snapshot?.draft?.id) { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        overscrollEffect = rememberOverscrollEffect(),
    ) {
        item(key = "import-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("导入名单", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Text(
                    "导入图片、CSV 或 Excel；空字段行自动跳过，可按需抽查，统一确认后写入客户资料。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (snapshot == null) {
            item(key = "ocr-method") {
                RaisedCard(modifier = Modifier.fillMaxWidth(), motionProfile = CardMotionProfile.Large) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("本机图片识别", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            InsetPill("完全离线")
                        }
                        Text(
                            "内置 PP-OCRv6 small 识别文字坐标，再用 SLANet_plus 匹配表格行列和单元格；骁龙机型优先尝试 Qualcomm NNAPI，无法运行时回退 CPU。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "较大模型下载和切换留在后续设置阶段。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        EditorialButton(
                            label = "从相册选择图片",
                            onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isOcrProcessing && imageSlotsRemaining > 0,
                        )
                        OutlinedButton(
                            onClick = launchCamera,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isOcrProcessing && imageSlotsRemaining > 0,
                        ) {
                            Text("拍摄名单")
                        }
                        OutlinedButton(
                            onClick = {
                                tabularFilePicker.launch(
                                    arrayOf(
                                        "text/*",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isOcrProcessing,
                        ) {
                            Text("选择 CSV / Excel 文件")
                        }
                    }
                }
            }
            item(key = "import-privacy") {
                RaisedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("资料留在本机", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "照片保存在应用私有目录。识别结果不会自动生成任务；确认导入后才创建客户和名单。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        EditorialButton("前往已有任务", onTasksClick, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        } else {
            item(key = "draft-title") {
                RaisedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("导入草稿", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            InsetPill("自动保存")
                        }
                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                title = it.take(60)
                                onUpdateTitle(title)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("名单名称") },
                            singleLine = true,
                        )
                        Text(
                            "${snapshot.images.size}/$MAX_IMAGES 张图片 · ${snapshot.rows.count { !it.skipped }} 条待导入 · ${snapshot.rows.count { it.skipped }} 条自动跳过",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "姓名或手机号任一项为空就自动跳过；信息完整的行无需逐条点确认，最后统一确认导入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isOcrProcessing && imageSlotsRemaining > 0,
                            ) { Text("继续选图") }
                            OutlinedButton(
                                onClick = launchCamera,
                                modifier = Modifier.weight(1f),
                                enabled = !state.isOcrProcessing && imageSlotsRemaining > 0,
                            ) { Text("拍照") }
                        }
                        OutlinedButton(
                            onClick = onAddManualRow,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isOcrProcessing && !state.isImportCommitting,
                        ) { Text("手动补一位客户") }
                    }
                }
            }

            if (state.isOcrProcessing) {
                item(key = "ocr-progress") {
                    RaisedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(strokeWidth = 3.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val progress = state.ocrProgress
                                Text(
                                    if (progress == null) "正在处理导入草稿" else "正在本机识别",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    if (progress == null) state.importMessage ?: "正在整理导入草稿…" else "图片 ${progress.currentImage}/${progress.totalImages}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (snapshot.images.isNotEmpty()) {
                item(key = "source-images-heading") {
                    Text("来源图片", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
                }
                items(snapshot.images, key = { "image-${it.id}" }) { image ->
                    val mappingAvailable = snapshot.rows.any { row ->
                        row.sourceImageId == image.id && (OcrSourceCellCodec.decode(row.sourceCells)?.size ?: 0) >= 2
                    }
                    SourceImageCard(
                        image = image,
                        rowCount = snapshot.rows.count { it.sourceImageId == image.id },
                        fieldMappingAvailable = mappingAvailable,
                        busy = state.isOcrProcessing || state.isImportCommitting,
                        onPreview = { previewImage = image },
                        onRetry = { onRetryImage(image.id) },
                        onMapFields = { fieldMappingImage = image },
                    )
                }
            }

            val tabularFiles = snapshot.rows.mapNotNull { it.sourceFileName }.distinct()
            if (tabularFiles.isNotEmpty()) {
                item(key = "source-files-heading") {
                    Text("来源文件", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
                }
                items(tabularFiles, key = { "source-file-$it" }) { fileName ->
                    val fileRows = snapshot.rows.filter { it.sourceFileName == fileName }
                    val mappingAvailable = fileRows.any { (OcrSourceCellCodec.decode(it.sourceCells)?.size ?: 0) >= 2 }
                    SourceFileCard(
                        fileName = fileName,
                        rowCount = fileRows.size,
                        fieldMappingAvailable = mappingAvailable,
                        busy = state.isOcrProcessing || state.isImportCommitting,
                        onMapFields = { fieldMappingFileName = fileName },
                    )
                }
            }

            item(key = "recognized-rows-heading") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("导入预览", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    InsetPill("完整行可导入")
                }
            }
            if (snapshot.rows.isEmpty()) {
                item(key = "empty-rows") {
                    RaisedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("还没有识别行", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "可以重试来源图片，或手动补充客户。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                val activeRows = snapshot.rows.filterNot(ImportDraftRowEntity::skipped)
                val skippedRows = snapshot.rows.filter(ImportDraftRowEntity::skipped)
                item(key = "active-rows-toggle") {
                    RaisedCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${activeRows.size} 条未跳过记录，可批量导入", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "后续重复手机号和姓名或手机号缺项的行会自动跳过；需要抽查时再展开记录，最后统一确认导入。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { showActiveRows = !showActiveRows },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (showActiveRows) "收起完整记录" else "抽查完整记录")
                            }
                        }
                    }
                }
                if (showActiveRows) {
                    items(activeRows, key = { "row-${it.id}" }) { row ->
                        val currentPhone = PhoneRecognition.comparableDigits(row.phone)
                        val samePhoneCount = if (currentPhone == null) 0 else snapshot.rows.count {
                            !it.skipped && PhoneRecognition.comparableDigits(it.phone) == currentPhone
                        }
                        val matches = if (currentPhone == null) emptyList() else state.allCustomers.filter {
                            PhoneRecognition.comparableDigits(it.phone) == currentPhone
                        }
                        ImportReviewRowCard(
                            row = row,
                            samePhoneCount = samePhoneCount,
                            matchingCustomers = matches,
                            enabled = !state.isOcrProcessing && !state.isImportCommitting,
                            onUpdate = onUpdateRow,
                            onDelete = { onDeleteRow(row.id) },
                        )
                    }
                }
                if (skippedRows.isNotEmpty()) {
                    item(key = "skipped-rows-toggle") {
                        TextButton(
                            onClick = { showSkippedRows = !showSkippedRows },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (showSkippedRows) "收起已跳过的 ${skippedRows.size} 行" else "查看已跳过的 ${skippedRows.size} 行")
                        }
                    }
                    if (showSkippedRows) {
                        items(skippedRows, key = { "row-${it.id}" }) { row ->
                            ImportReviewRowCard(
                                row = row,
                                samePhoneCount = 0,
                                matchingCustomers = emptyList(),
                                enabled = !state.isOcrProcessing && !state.isImportCommitting,
                                onUpdate = onUpdateRow,
                                onDelete = { onDeleteRow(row.id) },
                            )
                        }
                    }
                }
            }

            state.importMessage?.let { message ->
                item(key = "import-message") {
                    RaisedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = onDismissMessage, modifier = Modifier.align(Alignment.End)) { Text("关闭") }
                        }
                    }
                }
            }
            localMessage?.let { message ->
                item(key = "local-message") {
                    RaisedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { localMessage = null }) { Text("关闭") }
                        }
                    }
                }
            }

            item(key = "draft-actions") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "只有未跳过、姓名和有效手机号完整的行会导入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EditorialButton(
                        label = if (state.isImportCommitting) "正在保存…" else "确认导入名单",
                        onClick = { showImportConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isOcrProcessing && !state.isImportCommitting && snapshot.rows.any { !it.skipped },
                    )
                    TextButton(
                        onClick = { showDiscardConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isOcrProcessing && !state.isImportCommitting,
                    ) { Text("删除这份草稿和图片") }
                }
            }
        }
    }

    previewImage?.let { image ->
        SourceImageDialog(image, onDismiss = { previewImage = null })
    }
    fieldMappingImage?.let { image ->
        val sourceCells = snapshot?.rows.orEmpty()
            .filter { it.sourceImageId == image.id }
            .mapNotNull { OcrSourceCellCodec.decode(it.sourceCells) }
        FieldMappingDialog(
            sourceTitle = image.displayName,
            scopeDescription = "只应用到这张表的所有识别行。",
            sourceCells = sourceCells,
            onDismiss = { fieldMappingImage = null },
            onApply = { nameColumn, phoneColumn ->
                fieldMappingImage = null
                onApplyFieldMapping(image.id, nameColumn, phoneColumn)
            },
        )
    }
    fieldMappingFileName?.let { fileName ->
        val sourceCells = snapshot?.rows.orEmpty()
            .filter { it.sourceFileName == fileName }
            .mapNotNull { OcrSourceCellCodec.decode(it.sourceCells) }
        FieldMappingDialog(
            sourceTitle = fileName,
            scopeDescription = "只应用到这个文件的所有识别行。",
            sourceCells = sourceCells,
            onDismiss = { fieldMappingFileName = null },
            onApply = { nameColumn, phoneColumn ->
                fieldMappingFileName = null
                onApplyFileFieldMapping(fileName, nameColumn, phoneColumn)
            },
        )
    }
    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("删除这份导入草稿？") },
            text = { Text("草稿中的识别结果和应用私有目录里的图片都会删除，客户资料不会被改动。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirmation = false
                    onDiscardDraft()
                }) { Text("删除草稿") }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirmation = false }) { Text("取消") } },
        )
    }
    if (showImportConfirmation) {
        AlertDialog(
            onDismissRequest = { showImportConfirmation = false },
            title = { Text("确认创建名单？") },
            text = { Text("将统一导入姓名和有效手机号完整的行。空字段行已自动跳过，不需要逐条确认。") },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirmation = false
                    onCommitDraft()
                }) { Text("确认导入") }
            },
            dismissButton = { TextButton(onClick = { showImportConfirmation = false }) { Text("继续核对") } },
        )
    }
}

@Composable
private fun SourceImageCard(
    image: ImportDraftImageEntity,
    rowCount: Int,
    fieldMappingAvailable: Boolean,
    busy: Boolean,
    onPreview: () -> Unit,
    onRetry: () -> Unit,
    onMapFields: () -> Unit,
) {
    RaisedCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("图片 ${image.sortOrder + 1}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                InsetPill(if (rowCount == 0) "未识别" else "$rowCount 行")
            }
            Text(
                "${image.displayName} · ${image.width} × ${image.height}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f)) { Text("查看原图") }
                OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f), enabled = !busy) { Text("重新识别") }
            }
            if (fieldMappingAvailable) {
                TextButton(onClick = onMapFields, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text("识别不准？手动选字段（可选）")
                }
            }
        }
    }
}

@Composable
private fun SourceFileCard(
    fileName: String,
    rowCount: Int,
    fieldMappingAvailable: Boolean,
    busy: Boolean,
    onMapFields: () -> Unit,
) {
    RaisedCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fileName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                InsetPill("$rowCount 行")
            }
            Text("只读取本地文件，内容尚未写入客户资料。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (fieldMappingAvailable) {
                TextButton(onClick = onMapFields, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text("识别不准？手动选字段（可选）")
                }
            }
        }
    }
}

@Composable
private fun ImportReviewRowCard(
    row: ImportDraftRowEntity,
    samePhoneCount: Int,
    matchingCustomers: List<CustomerEntity>,
    enabled: Boolean,
    onUpdate: (ImportDraftRowEntity) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(row.id) { mutableStateOf(row.name) }
    var phone by remember(row.id) { mutableStateOf(row.phone) }
    val hasPendingEdits = name != row.name || phone != row.phone

    RaisedCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("名单行 ${row.sortOrder + 1}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                row.confidence?.let { InsetPill("识别 ${((it * 100).toInt()).coerceIn(0, 100)}%") }
            }
            row.sourceText.takeIf(String::isNotBlank)?.let {
                Text("原始识别：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (row.skipped) {
                val duplicatePhoneSkipped = row.skipReason == ImportSkipReason.DUPLICATE_PHONE
                val skipDescription = when (row.skipReason) {
                    ImportSkipReason.DUPLICATE_PHONE -> "手机号与前面行重复，后续行默认自动跳过。"
                    ImportSkipReason.INCOMPLETE -> "姓名或手机号为空，此行已自动跳过。"
                    else -> "此行已跳过，不会导入。"
                }
                Text(skipDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                OutlinedButton(
                    onClick = {
                        onUpdate(
                            row.copy(
                                skipped = false,
                                reviewed = false,
                                duplicateMode = if (duplicatePhoneSkipped) LocalOcrImportMode.CREATE_NEW else null,
                                duplicateCustomerId = null,
                                skipReason = null,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                ) { Text(if (duplicatePhoneSkipped) "恢复为独立客户" else "恢复此行") }
            } else {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(40)
                        onUpdate(row.copy(name = name, phone = phone, reviewed = false))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("客户姓名") },
                    singleLine = true,
                    enabled = enabled,
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it.take(32)
                        val sameNumber = PhoneRecognition.comparableDigits(phone) == PhoneRecognition.comparableDigits(row.phone)
                        onUpdate(
                            row.copy(
                                name = name,
                                phone = phone,
                                reviewed = false,
                                duplicateMode = if (sameNumber) row.duplicateMode else null,
                                duplicateCustomerId = if (sameNumber) row.duplicateCustomerId else null,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("手机号码") },
                    singleLine = true,
                    enabled = enabled,
                )
                val valid = name.isNotBlank() && PhoneRecognition.isMainlandMobile(phone)
                InsetPill(
                    when {
                        hasPendingEdits -> "正在保存修改…"
                        valid -> "姓名和手机号完整，可直接导入"
                        else -> "姓名与有效手机号需补全"
                    },
                )

                if (samePhoneCount > 1) {
                    Text(
                        "草稿中还有相同号码；同一已有客户不能重复加入同一名单，重复行请选独立客户或跳过。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (matchingCustomers.isNotEmpty()) {
                    Text("已有线索使用这个号码，请明确选择：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    FilterChip(
                        selected = row.duplicateMode == LocalOcrImportMode.CREATE_NEW,
                        onClick = {
                            onUpdate(row.copy(duplicateMode = LocalOcrImportMode.CREATE_NEW, duplicateCustomerId = null))
                        },
                        enabled = enabled && !hasPendingEdits,
                        label = { Text("新建独立客户") },
                    )
                    matchingCustomers.take(MAX_DUPLICATE_CHOICES).forEach { customer ->
                        FilterChip(
                            selected = row.duplicateMode == LocalOcrImportMode.REUSE_EXISTING && row.duplicateCustomerId == customer.id,
                            onClick = {
                                onUpdate(
                                    row.copy(
                                        duplicateMode = LocalOcrImportMode.REUSE_EXISTING,
                                        duplicateCustomerId = customer.id,
                                    ),
                                )
                            },
                            enabled = enabled && !hasPendingEdits,
                            label = { Text("关联 ${customer.name} · ${customer.phone}") },
                        )
                    }
                } else if (samePhoneCount > 1) {
                    Text("这份草稿中也有相同号码。要保留本行，请选为独立客户；否则跳过。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    FilterChip(
                        selected = row.duplicateMode == LocalOcrImportMode.CREATE_NEW,
                        onClick = { onUpdate(row.copy(duplicateMode = LocalOcrImportMode.CREATE_NEW, duplicateCustomerId = null)) },
                        enabled = enabled && !hasPendingEdits,
                        label = { Text("保留为独立客户") },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = {
                            onUpdate(
                                row.copy(
                                    name = name,
                                    phone = phone,
                                    skipped = true,
                                    reviewed = false,
                                    duplicateMode = null,
                                    duplicateCustomerId = null,
                                    skipReason = ImportSkipReason.MANUAL,
                                ),
                            )
                        },
                        enabled = enabled && !hasPendingEdits,
                    ) { Text("跳过此行") }
                    TextButton(onClick = onDelete, enabled = enabled) { Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun SourceImageDialog(image: ImportDraftImageEntity, onDismiss: () -> Unit) {
    var bitmap by remember(image.localPath) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(image.localPath) { mutableStateOf(false) }
    LaunchedEffect(image.localPath) {
        val preview = withContext(Dispatchers.IO) { decodePreviewBitmap(image.localPath) }
        bitmap = preview
        failed = preview == null
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = G2CornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("来源图片", style = MaterialTheme.typography.titleLarge)
                Text(image.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val preview = bitmap
                when {
                    preview != null -> Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "名单来源图片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp)
                            .aspectRatio(preview.width.toFloat() / preview.height.toFloat()),
                        contentScale = ContentScale.Fit,
                    )
                    failed -> Text("本机草稿图片已无法读取。", color = MaterialTheme.colorScheme.error)
                    else -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("关闭") }
            }
        }
    }
}

@Composable
private fun FieldMappingDialog(
    sourceTitle: String,
    scopeDescription: String,
    sourceCells: List<List<String>>,
    onDismiss: () -> Unit,
    onApply: (nameColumn: Int, phoneColumn: Int) -> Unit,
) {
    val columnCount = sourceCells.maxOfOrNull { it.size } ?: 0
    val samples = (0 until columnCount).map { column ->
        sourceCells.mapNotNull { it.getOrNull(column)?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
            .take(3)
    }
    val suggestedPhoneColumn = samples.indexOfFirst { values ->
        values.any { PhoneRecognition.candidates(it).isNotEmpty() }
    }.takeIf { it >= 0 }
    val defaultPhoneColumn = suggestedPhoneColumn ?: if (columnCount > 1) 1 else 0
    val defaultNameColumn = (0 until columnCount).firstOrNull { it != defaultPhoneColumn } ?: 0
    var nameColumn by remember(sourceTitle, columnCount) { mutableStateOf(defaultNameColumn) }
    var phoneColumn by remember(sourceTitle, columnCount) { mutableStateOf(defaultPhoneColumn) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动指定字段") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("$sourceTitle · $scopeDescription")
                Text(
                    "识别正确时直接关闭，继续向下核对即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("姓名列", style = MaterialTheme.typography.titleSmall)
                samples.forEachIndexed { index, values ->
                    FieldColumnChoice(
                        column = index,
                        samples = values,
                        selected = nameColumn == index,
                        onSelect = { nameColumn = index },
                    )
                }
                Text("手机号列", style = MaterialTheme.typography.titleSmall)
                samples.forEachIndexed { index, values ->
                    FieldColumnChoice(
                        column = index,
                        samples = values,
                        selected = phoneColumn == index,
                        onSelect = { phoneColumn = index },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(nameColumn, phoneColumn) }, enabled = columnCount >= 2 && nameColumn != phoneColumn) {
                Text("应用到本表")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FieldColumnChoice(
    column: Int,
    samples: List<String>,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val sampleLabel = if (samples.isEmpty()) "" else samples.joinToString("、", prefix = " · ")
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = "第${column + 1}列$sampleLabel",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun decodePreviewBitmap(path: String): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_PREVIEW_DIMENSION ||
                bounds.outHeight / sampleSize > MAX_PREVIEW_DIMENSION
            ) {
                sampleSize *= 2
            }
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )
        }
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }
}

private object LocalOcrImportMode {
    const val CREATE_NEW = "CREATE_NEW"
    const val REUSE_EXISTING = "REUSE_EXISTING"
}

private const val MAX_IMAGES = 5
private const val MAX_DUPLICATE_CHOICES = 4
private const val MAX_PREVIEW_DIMENSION = 1600
