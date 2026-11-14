@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.qiongge.callassistant.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.qiongge.callassistant.data.local.entity.CallRecordEntity
import com.qiongge.callassistant.data.local.entity.ContactStatus
import com.qiongge.callassistant.data.local.entity.CustomerEntity
import com.qiongge.callassistant.data.local.entity.CustomerListEntity
import com.qiongge.callassistant.data.local.entity.TaskProgressEntity
import com.qiongge.callassistant.data.local.model.TaskCustomerRow
import com.qiongge.callassistant.data.local.model.TaskSummary
import com.qiongge.callassistant.ui.components.CardMotionProfile
import com.qiongge.callassistant.ui.components.EditorialButton
import com.qiongge.callassistant.ui.components.InsetPill
import com.qiongge.callassistant.ui.components.RaisedCard
import com.qiongge.callassistant.ui.theme.G2CornerShape
import com.qiongge.callassistant.ui.theme.NumberTypography

@Composable
fun TasksPage(
    tasks: List<TaskSummary>,
    onCreateTask: (String) -> Unit,
    onOpenTask: (Long) -> Unit,
    onContinueTask: (Long, Long, Long) -> Unit,
    onRenameTask: (CustomerListEntity, String) -> Unit,
    onDeleteTask: (Long) -> Unit,
    automaticDialEnabled: Boolean,
    automaticDialDelaySeconds: Int,
    onAutomaticDialEnabledChange: (Boolean) -> Unit,
    onAutomaticDialDelayChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<CustomerListEntity?>(null) }
    var deletingTask by remember { mutableStateOf<TaskSummary?>(null) }
    var dialSettingsTask by remember { mutableStateOf<CustomerListEntity?>(null) }
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        overscrollEffect = rememberOverscrollEffect(),
    ) {
        item(key = "intro") {
            PageIntro(
                title = "今天要联系谁？",
                subtitle = "把客户放进任务名单，按进度接着跟进。",
            )
        }
        if (tasks.isEmpty()) {
            item(key = "empty") {
                EmptyStateCard(
                    title = "还没有任务名单",
                    body = "先手动建一份名单，再录入客户和电话号码。",
                    action = "新建任务名单",
                    onAction = { showCreateDialog = true },
                )
            }
        } else {
            item(key = "create") {
                EditorialButton(
                    label = "新建任务名单",
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(tasks, key = { it.task.id }) { summary ->
                TaskSummaryCard(
                    summary = summary,
                    onOpen = { onOpenTask(summary.task.id) },
                    onContinue = {
                        val membershipId = summary.currentMembershipId
                        val customerId = summary.currentCustomerId
                        if (membershipId != null && customerId != null) {
                            onContinueTask(summary.task.id, customerId, membershipId)
                        } else {
                            onOpenTask(summary.task.id)
                        }
                    },
                    onRename = { editingTask = summary.task },
                    onDelete = { deletingTask = summary },
                    onDialSettings = { dialSettingsTask = summary.task },
                )
            }
        }
    }

    if (showCreateDialog) {
        TaskEditorDialog(
            title = "新建任务名单",
            onDismiss = { showCreateDialog = false },
            onSave = {
                showCreateDialog = false
                onCreateTask(it)
            },
        )
    }
    editingTask?.let { task ->
        TaskEditorDialog(
            initialTitle = task.title,
            title = "重命名名单",
            onDismiss = { editingTask = null },
            onSave = {
                editingTask = null
                onRenameTask(task, it)
            },
        )
    }
    deletingTask?.let { summary ->
        ConfirmActionDialog(
            title = "删除这份名单？",
            message = "“${summary.task.title}”中的名单关系会删除，客户资料仍保留在线索管理中。",
            confirmLabel = "删除名单",
            onDismiss = { deletingTask = null },
            onConfirm = {
                deletingTask = null
                onDeleteTask(summary.task.id)
            },
        )
    }
    dialSettingsTask?.let {
        AlertDialog(
            onDismissRequest = { dialSettingsTask = null },
            title = { Text("自动拨号设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("拨打下一位", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = automaticDialEnabled, onCheckedChange = onAutomaticDialEnabledChange)
                    }
                    Text("仅在通话结束并返回应用后生效；倒计时期间可取消。", style = MaterialTheme.typography.bodySmall)
                    Text("拨号间隔：$automaticDialDelaySeconds 秒", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = automaticDialDelaySeconds.toFloat(),
                        onValueChange = { onAutomaticDialDelayChange(it.toInt().coerceIn(5, 30)) },
                        valueRange = 5f..30f,
                        steps = 24,
                        enabled = automaticDialEnabled,
                    )
                    Text("总开关对所有任务生效；仅在继续联系且还有下一位时自动拨号。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { dialSettingsTask = null }) { Text("完成") } },
        )
    }
}

@Composable
private fun TaskSummaryCard(
    summary: TaskSummary,
    onOpen: () -> Unit,
    onContinue: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDialSettings: () -> Unit,
) {
    val remaining = (summary.customerCount - summary.contactedCount).coerceAtLeast(0)
    RaisedCard(
        modifier = Modifier.fillMaxWidth(),
        motionProfile = CardMotionProfile.Large,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.task.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "名单里有 ${summary.customerCount} 位客户，已记录 ${summary.contactedCount} 位的联系状态。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (summary.currentMembershipId != null) {
                    InsetPill(text = "有进行中的进度")
                }
            }
            Text(
                text = if (remaining > 0) "还剩 $remaining 位待跟进" else if (summary.customerCount == 0) "名单还是空的" else "名单客户均已记录联系状态",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EditorialButton(
                label = if (summary.currentMembershipId != null && summary.currentCustomerId != null) {
                    "继续联系"
                } else {
                    "打开名单"
                },
                onClick = if (summary.currentMembershipId != null && summary.currentCustomerId != null) {
                    onContinue
                } else {
                    onOpen
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRename) { Text("重命名") }
                TextButton(onClick = onDelete) { Text("删除名单", color = MaterialTheme.colorScheme.error) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDialSettings) { Text("自动拨号") }
            }
        }
    }
}

@Composable
fun TaskDetailPage(
    task: CustomerListEntity?,
    members: List<TaskCustomerRow>,
    allCustomers: List<CustomerEntity>,
    progress: TaskProgressEntity?,
    onCreateCustomer: (String, String) -> Unit,
    onAddExistingCustomer: (Long) -> Unit,
    onOpenCustomer: (Long, Long) -> Unit,
    onContinueTask: (Long, Long) -> Unit,
    onRemoveCustomer: (Long) -> Unit,
    onRenameTask: (CustomerListEntity, String) -> Unit,
    onDeleteTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var removingMember by remember { mutableStateOf<TaskCustomerRow?>(null) }
    val listState = rememberLazyListState()
    val currentMembershipId = progress?.currentMembershipId
    val resumeRow = members.firstOrNull { it.membershipId == currentMembershipId }
        ?: members.firstOrNull()

    LaunchedEffect(task?.id, currentMembershipId) {
        val currentIndex = members.indexOfFirst { it.membershipId == currentMembershipId }
        if (currentIndex >= 0) {
            listState.scrollToItem(currentIndex + 2)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        overscrollEffect = rememberOverscrollEffect(),
    ) {
        item(key = "task-header") {
            if (task == null) {
                RaisedCard(
                    modifier = Modifier.fillMaxWidth(),
                    motionProfile = CardMotionProfile.Compact,
                ) {
                    Text("正在打开名单…", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PageIntro(task.title, "名单中有 ${members.size} 位客户。进度保存在本机。")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editingTask = true }, shape = G2CornerShape(16.dp)) {
                            Text("重命名")
                        }
                        OutlinedButton(onClick = { confirmingDelete = true }, shape = G2CornerShape(16.dp)) {
                            Text("删除名单", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    resumeRow?.let { row ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (row.membershipId == currentMembershipId) {
                                    "上次停在 ${row.customer.name}"
                                } else {
                                    "从名单第一位开始联系"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            EditorialButton(
                                label = "继续联系",
                                onClick = { onContinueTask(row.customer.id, row.membershipId) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    EditorialButton(
                        label = "添加客户",
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (members.isEmpty()) {
            item(key = "empty-members") {
                EmptyStateCard(
                    title = "名单里还没有客户",
                    body = "可以手动新建客户，也可以从线索管理中添加已有线索。",
                    action = "添加第一位客户",
                    onAction = { showAddDialog = true },
                )
            }
        } else {
            item(key = "members-label") {
                Text("名单客户", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            }
            items(members, key = { it.membershipId }) { row ->
                val isCurrent = row.membershipId == currentMembershipId
                CustomerRowCard(
                    customer = row.customer,
                    badge = if (isCurrent) "上次打开" else null,
                    onClick = { onOpenCustomer(row.customer.id, row.membershipId) },
                    trailing = {
                        TextButton(onClick = { removingMember = row }) {
                            Text("移出")
                        }
                    },
                )
            }
        }
    }

    if (showAddDialog) {
        val memberIds = remember(members) { members.mapTo(mutableSetOf()) { it.customer.id } }
        AddCustomerToTaskDialog(
            availableCustomers = allCustomers.filterNot { it.id in memberIds },
            onDismiss = { showAddDialog = false },
            onCreate = { name, phone ->
                onCreateCustomer(name, phone)
                showAddDialog = false
            },
            onSelectExisting = {
                onAddExistingCustomer(it)
                showAddDialog = false
            },
        )
    }
    if (editingTask && task != null) {
        TaskEditorDialog(
            initialTitle = task.title,
            title = "重命名名单",
            onDismiss = { editingTask = false },
            onSave = {
                editingTask = false
                onRenameTask(task, it)
            },
        )
    }
    if (confirmingDelete && task != null) {
        ConfirmActionDialog(
            title = "删除这份名单？",
            message = "名单中的客户资料仍保留在线索管理中。",
            confirmLabel = "删除名单",
            onDismiss = { confirmingDelete = false },
            onConfirm = {
                confirmingDelete = false
                onDeleteTask(task.id)
            },
        )
    }
    removingMember?.let { row ->
        ConfirmActionDialog(
            title = "从名单移出客户？",
            message = "${row.customer.name} 的客户资料会保留，之后仍可从线索管理中重新加入。",
            confirmLabel = "移出名单",
            onDismiss = { removingMember = null },
            onConfirm = {
                removingMember = null
                onRemoveCustomer(row.customer.id)
            },
        )
    }
}

@Composable
fun LeadsPage(
    leads: List<CustomerEntity>,
    query: String,
    statusFilter: ContactStatus?,
    onQueryChange: (String) -> Unit,
    onStatusFilter: (ContactStatus?) -> Unit,
    onAddCustomer: (String, String) -> Unit,
    onOpenCustomer: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        overscrollEffect = rememberOverscrollEffect(),
    ) {
        item(key = "lead-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PageIntro("线索都在这里", "本机保存的客户资料，可搜索、筛选和编辑。")
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("搜索姓名或电话号码") },
                    shape = G2CornerShape(18.dp),
                )
                StatusFilterRow(selected = statusFilter, onSelect = onStatusFilter)
                EditorialButton(
                    label = "手动新增线索",
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (leads.isEmpty()) {
            item(key = "empty-leads") {
                EmptyStateCard(
                    title = if (query.isBlank() && statusFilter == null) "还没有客户线索" else "没有匹配的线索",
                    body = if (query.isBlank() && statusFilter == null) "手动新增姓名和电话号码，先把客户资料留在本机。" else "试试换一个关键词或联系状态。",
                    action = if (query.isBlank() && statusFilter == null) "新增线索" else null,
                    onAction = { showCreateDialog = true },
                )
            }
        } else {
            item(key = "lead-count") {
                Text("${leads.size} 条线索", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(leads, key = CustomerEntity::id) { customer ->
                CustomerRowCard(customer = customer, onClick = { onOpenCustomer(customer.id) })
            }
        }
    }

    if (showCreateDialog) {
        CustomerEditorDialog(
            title = "新增线索",
            submitLabel = "保存线索",
            onDismiss = { showCreateDialog = false },
            onSave = { name, phone ->
                onAddCustomer(name, phone)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun StatusFilterRow(
    selected: ContactStatus?,
    onSelect: (ContactStatus?) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 2.dp),
        overscrollEffect = rememberOverscrollEffect(),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("全部") },
                shape = G2CornerShape(50.dp),
            )
        }
        items(ContactStatus.entries, key = { it.name }) { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(if (selected == status) null else status) },
                label = { Text(status.label) },
                shape = G2CornerShape(50.dp),
            )
        }
    }
}

@Composable
fun CustomerDetailPage(
    customer: CustomerEntity?,
    tasks: List<CustomerListEntity>,
    contactRecords: List<CallRecordEntity>,
    activeTask: CustomerListEntity?,
    taskMembers: List<TaskCustomerRow>,
    selectedMembershipId: Long?,
    automaticDialEnabled: Boolean,
    automaticDialDelaySeconds: Int,
    onEdit: (CustomerEntity, String, String) -> Unit,
    onDelete: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenTaskCustomer: (Long, Long) -> Unit,
    onRecordContact: (Long, ContactStatus, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingCustomer by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var selectedStatus by remember(customer?.id, customer?.status) {
        mutableStateOf(customer?.status ?: ContactStatus.NOT_CONTACTED)
    }
    var noteDraft by remember(customer?.id) { mutableStateOf("") }
    var countdown by remember(activeTask?.id) { mutableIntStateOf(0) }
    var awaitingCallReturn by remember(activeTask?.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentPhone = customer?.phone.orEmpty()
    val currentTaskIndexForDial = taskMembers.indexOfFirst { it.membershipId == selectedMembershipId }
    val nextMemberForDial = taskMembers.getOrNull(currentTaskIndexForDial + 1)
    fun launchCall(phone: String) {
        try {
            awaitingCallReturn = true
            context.startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", phone, null)))
        } catch (_: ActivityNotFoundException) {
            awaitingCallReturn = false
            scope.launch { snackbarHostState.showSnackbar("当前设备无法发起电话。") }
        } catch (_: SecurityException) {
            awaitingCallReturn = false
            scope.launch { snackbarHostState.showSnackbar("没有拨打电话权限，请重新授权后再试。") }
        }
    }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && currentPhone.isNotBlank()) {
            launchCall(currentPhone)
        } else {
            scope.launch { snackbarHostState.showSnackbar("需要授予拨打电话权限才能呼出。") }
        }
    }

    DisposableEffect(lifecycleOwner, automaticDialEnabled, automaticDialDelaySeconds, nextMemberForDial?.membershipId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingCallReturn) {
                awaitingCallReturn = false
                if (automaticDialEnabled && nextMemberForDial != null) countdown = automaticDialDelaySeconds
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(countdown, automaticDialEnabled, nextMemberForDial?.membershipId) {
        if (countdown > 0 && automaticDialEnabled) {
            kotlinx.coroutines.delay(1_000)
            if (countdown == 1) {
                val next = nextMemberForDial
                if (next != null) {
                    countdown = 0
                    onOpenTaskCustomer(next.customer.id, next.membershipId)
                    launchCall(next.customer.phone)
                } else countdown = 0
            } else countdown -= 1
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = rememberOverscrollEffect(),
        ) {
            if (customer == null) {
                item(key = "customer-detail-loading") {
                    RaisedCard(
                        modifier = Modifier.fillMaxWidth(),
                        motionProfile = CardMotionProfile.Large,
                    ) {
                        Text("正在打开客户资料…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                val currentCustomer = customer
                item(key = "customer-detail") {
                    RaisedCard(
                        modifier = Modifier.fillMaxWidth(),
                        motionProfile = CardMotionProfile.Large,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(currentCustomer.name, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                currentCustomer.phone,
                                style = NumberTypography.copy(fontSize = 24.sp, lineHeight = 30.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                InsetPill(text = currentCustomer.status.label)
                                InsetPill(text = "本机资料")
                            }
                            Text(
                                "通话结束后返回应用，才能记录联系结果。自动拨号仅在开启后拨打任务下一位。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            EditorialButton(
                                label = "拨打当前客户",
                                onClick = {
                                    try {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                            launchCall(currentCustomer.phone)
                                        } else callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                                    } catch (_: ActivityNotFoundException) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("无法打开系统拨号应用。")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (activeTask != null && nextMemberForDial != null) {
                                if (automaticDialEnabled) {
                                    Text("自动拨号已开启 · 间隔 ${automaticDialDelaySeconds} 秒", style = MaterialTheme.typography.bodyMedium)
                                    if (countdown > 0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text("${countdown} 秒后拨打下一位", style = MaterialTheme.typography.bodyMedium)
                                            TextButton(onClick = { countdown = 0 }) { Text("取消") }
                                        }
                                    }
                                } else {
                                    Text("自动拨号已关闭", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { editingCustomer = true }, shape = G2CornerShape(16.dp)) {
                                    Text("编辑资料")
                                }
                                OutlinedButton(onClick = { confirmingDelete = true }, shape = G2CornerShape(16.dp)) {
                                    Text("删除客户", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                val currentTaskIndex = taskMembers.indexOfFirst {
                    it.membershipId == selectedMembershipId
                }
                if (activeTask != null && currentTaskIndex >= 0) {
                    val previousMember = taskMembers.getOrNull(currentTaskIndex - 1)
                    val nextMember = taskMembers.getOrNull(currentTaskIndex + 1)
                    item(key = "task-contact-position") {
                        RaisedCard(
                            modifier = Modifier.fillMaxWidth(),
                            motionProfile = CardMotionProfile.Medium,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(activeTask.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "第 ${currentTaskIndex + 1} 位，共 ${taskMembers.size} 位",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            previousMember?.let {
                                                onOpenTaskCustomer(it.customer.id, it.membershipId)
                                            }
                                        },
                                        enabled = previousMember != null,
                                        modifier = Modifier.weight(1f),
                                        shape = G2CornerShape(16.dp),
                                    ) {
                                        Text("上一位")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            nextMember?.let {
                                                onOpenTaskCustomer(it.customer.id, it.membershipId)
                                            }
                                        },
                                        enabled = nextMember != null,
                                        modifier = Modifier.weight(1f),
                                        shape = G2CornerShape(16.dp),
                                    ) {
                                        Text("下一位")
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "contact-result-editor") {
                    RaisedCard(
                        modifier = Modifier.fillMaxWidth(),
                        motionProfile = CardMotionProfile.Large,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("联系结果", style = MaterialTheme.typography.titleLarge)
                            ContactStatus.entries.chunked(3).forEach { rowStatuses ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowStatuses.forEach { status ->
                                        ContactStatusChoice(
                                            status = status,
                                            selected = selectedStatus == status,
                                            onClick = { selectedStatus = status },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = noteDraft,
                                onValueChange = { noteDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("备注（选填）") },
                                placeholder = { Text("写下需要记住的内容") },
                                minLines = 3,
                                maxLines = 5,
                                shape = G2CornerShape(16.dp),
                            )
                            EditorialButton(
                                label = "保存联系记录",
                                enabled = noteDraft.isNotBlank() || selectedStatus != currentCustomer.status,
                                onClick = {
                                    onRecordContact(currentCustomer.id, selectedStatus, noteDraft)
                                    noteDraft = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                item(key = "contact-history-title") {
                    Text("联系记录", style = MaterialTheme.typography.titleMedium)
                }
                if (contactRecords.isEmpty()) {
                    item(key = "contact-history-empty") {
                        RaisedCard(
                            modifier = Modifier.fillMaxWidth(),
                            motionProfile = CardMotionProfile.Compact,
                        ) {
                            Text(
                                "还没有联系记录。拨号后可以回来补充结果和备注。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(contactRecords, key = { it.id }) { record ->
                        ContactRecordCard(record = record)
                    }
                }

                item(key = "customer-tasks-label") {
                    Text("所属任务名单", style = MaterialTheme.typography.titleMedium)
                }
                if (tasks.isEmpty()) {
                    item(key = "customer-no-tasks") {
                        RaisedCard(
                            modifier = Modifier.fillMaxWidth(),
                            motionProfile = CardMotionProfile.Medium,
                        ) {
                            Text(
                                "这位客户还没有加入任何任务名单。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(tasks, key = { it.id }) { task ->
                        RaisedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenTask(task.id) },
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                            motionProfile = CardMotionProfile.Medium,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(task.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "打开名单",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val currentCustomer = customer
    if (editingCustomer && currentCustomer != null) {
        CustomerEditorDialog(
            initialName = currentCustomer.name,
            initialPhone = currentCustomer.phone,
            title = "编辑客户资料",
            onDismiss = { editingCustomer = false },
            onSave = { name, phone ->
                editingCustomer = false
                onEdit(currentCustomer, name, phone)
            },
        )
    }
    if (confirmingDelete && currentCustomer != null) {
        ConfirmActionDialog(
            title = "删除客户资料？",
            message = "客户会从所有任务名单和线索管理中删除。",
            confirmLabel = "删除客户",
            onDismiss = { confirmingDelete = false },
            onConfirm = {
                confirmingDelete = false
                onDelete(currentCustomer.id)
            },
        )
    }
}

@Composable
private fun ContactStatusChoice(
    status: ContactStatus,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            },
        shape = G2CornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun ContactRecordCard(record: CallRecordEntity) {
    RaisedCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        motionProfile = CardMotionProfile.Compact,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.result.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = record.createdAt.toContactTimeLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            record.note?.let { note ->
                Text(note, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private val ContactRecordTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun Long.toContactTimeLabel(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(ContactRecordTimeFormatter)

@Composable
private fun CustomerRowCard(
    customer: CustomerEntity,
    badge: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    RaisedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        motionProfile = CardMotionProfile.Compact,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(customer.name, style = MaterialTheme.typography.titleMedium)
                    badge?.let { InsetPill(text = it) }
                }
                Text(customer.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(customer.status.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun PageIntro(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    body: String,
    action: String?,
    onAction: () -> Unit,
) {
    RaisedCard(
        modifier = Modifier.fillMaxWidth(),
        motionProfile = CardMotionProfile.Large,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                EditorialButton(label = action, onClick = onAction, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = G2CornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

private val ContactStatus.label: String
    get() = when (this) {
        ContactStatus.NOT_CONTACTED -> "未联系"
        ContactStatus.UNREACHED -> "未接通"
        ContactStatus.CONNECTED -> "已接通"
        ContactStatus.INTERESTED -> "有意向"
        ContactStatus.NOT_INTERESTED -> "暂不考虑"
        ContactStatus.DO_NOT_CONTACT -> "不再联系"
    }
