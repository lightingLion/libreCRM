@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.qiongge.callassistant.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.telecom.TelecomManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.telephony.TelephonyManager
import androidx.compose.animation.core.animate
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.qiongge.callassistant.data.local.entity.CallRecordEntity
import com.qiongge.callassistant.R
import com.qiongge.callassistant.data.local.entity.ContactStatus
import com.qiongge.callassistant.data.local.entity.CustomerEntity
import com.qiongge.callassistant.data.local.entity.CustomerListEntity
import com.qiongge.callassistant.data.local.entity.TaskProgressEntity
import com.qiongge.callassistant.data.local.model.TaskCustomerRow
import com.qiongge.callassistant.data.local.model.TaskSummary
import com.qiongge.callassistant.data.local.model.TaskContactRecord
import com.qiongge.callassistant.domain.calling.CallReturnCountdown
import com.qiongge.callassistant.domain.calling.PhoneCallState
import com.qiongge.callassistant.domain.calling.DialSim
import com.qiongge.callassistant.domain.calling.PhoneDialing
import com.qiongge.callassistant.domain.calling.automaticDialDelayRange
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.qiongge.callassistant.ui.components.dismissKeyboardOnBlankTap
import com.qiongge.callassistant.ui.components.CardMotionProfile
import com.qiongge.callassistant.ui.components.EditorialButton
import com.qiongge.callassistant.ui.components.InsetPill
import com.qiongge.callassistant.ui.components.RaisedCard
import com.qiongge.callassistant.ui.theme.G2CornerShape
import com.qiongge.callassistant.ui.theme.DialNumberTypography
import com.qiongge.callassistant.ui.theme.NumberTypography
import com.qiongge.callassistant.ui.components.WorkbookCustomerRow
import com.qiongge.callassistant.ui.components.projectedDragPosition
import com.qiongge.callassistant.ui.theme.AppSpacing
import com.qiongge.callassistant.ui.theme.AppMotion

@Composable
fun TasksPage(
    tasks: List<TaskSummary>,
    onCreateTask: (String) -> Unit,
    onImportClick: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onContinueTask: (Long, Long, Long) -> Unit,
    onRenameTask: (CustomerListEntity, String) -> Unit,
    onDeleteTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<CustomerListEntity?>(null) }
    var deletingTask by remember { mutableStateOf<TaskSummary?>(null) }
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
                    body = "导入已有名单，或手动新建名单并添加客户。",
                    action = "导入名单",
                    onAction = onImportClick,
                )
            }
            item(key = "create-empty") {
                TextButton(onClick = { showCreateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("手动新建名单")
                }
            }
        } else {
            item(key = "create") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${tasks.size} 份名单", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { showCreateDialog = true }) { Text("新建名单") }
                }
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
}

@Composable
private fun AutomaticDialSettingsDialog(
    automaticDialEnabled: Boolean,
    automaticDialDelaySeconds: Int,
    onAutomaticDialEnabledChange: (Boolean) -> Unit,
    onAutomaticDialDelayChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自动拨号设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("拨打下一位", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = automaticDialEnabled, onCheckedChange = onAutomaticDialEnabledChange)
                }
                Text("通话结束且应用在前台时开始倒计时，期间可取消。", style = MaterialTheme.typography.bodySmall)
                Text("拨号间隔：$automaticDialDelaySeconds 秒", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = automaticDialDelaySeconds.toFloat(),
                    onValueChange = { onAutomaticDialDelayChange(it.roundToInt()) },
                    valueRange = automaticDialDelayRange.first.toFloat()..automaticDialDelayRange.last.toFloat(),
                    steps = automaticDialDelayRange.last - automaticDialDelayRange.first - 1,
                    enabled = automaticDialEnabled,
                )
                Text("倒计时归零后保存当前结果，再拨打下一位。此开关对所有任务生效。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun TaskSummaryCard(
    summary: TaskSummary,
    onOpen: () -> Unit,
    onContinue: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val remaining = (summary.customerCount - summary.contactedCount).coerceAtLeast(0)
    val hasProgress = summary.currentMembershipId != null && summary.currentCustomerId != null
    var showMenu by remember { mutableStateOf(false) }
    RaisedCard(
        modifier = Modifier.fillMaxWidth(),
        motionProfile = CardMotionProfile.Large,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.task.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${summary.customerCount} 位客户 · 已记录 ${summary.contactedCount} 位",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(painterResource(R.drawable.ic_more), contentDescription = "名单更多操作")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("查看名单") }, onClick = { showMenu = false; onOpen() })
                        DropdownMenuItem(text = { Text("重命名") }, onClick = { showMenu = false; onRename() })
                        DropdownMenuItem(text = { Text("删除名单", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
            if (summary.customerCount > 0 && summary.contactedCount > 0) {
                LinearProgressIndicator(
                    progress = { (summary.contactedCount.toFloat() / summary.customerCount).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Text(
                text = if (remaining > 0) "还剩 $remaining 位待跟进" else if (summary.customerCount == 0) "名单还是空的" else "名单客户均已记录联系状态",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EditorialButton(
                label = if (hasProgress) {
                    "继续联系"
                } else {
                    "打开名单"
                },
                onClick = if (hasProgress) {
                    onContinue
                } else {
                    onOpen
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (hasProgress) {
                TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("查看名单") }
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
    var showTaskMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val currentMembershipId = progress?.currentMembershipId
    val currentIndex = remember(members, currentMembershipId) {
        members.indexOfFirst { it.membershipId == currentMembershipId }
    }
    val resumeRow = members.getOrNull(currentIndex) ?: members.firstOrNull()

    LaunchedEffect(task?.id, currentMembershipId, currentIndex >= 0) {
        if (currentIndex >= 0) {
            listState.scrollToItem(currentIndex + 2)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = AppSpacing.readingWidth).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(
                start = AppSpacing.lg, top = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.joined),
            overscrollEffect = rememberOverscrollEffect(),
        ) {
            item(key = "task-header") {
                if (task == null) {
                    Text("正在打开名单…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${members.size} 位客户",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Box {
                                    IconButton(onClick = { showTaskMenu = true }) {
                                        Icon(painterResource(R.drawable.ic_more), contentDescription = "名单更多操作")
                                    }
                                    DropdownMenu(expanded = showTaskMenu, onDismissRequest = { showTaskMenu = false }) {
                                        DropdownMenuItem(text = { Text("重命名") }, onClick = { showTaskMenu = false; editingTask = true })
                                        DropdownMenuItem(text = { Text("删除名单", color = MaterialTheme.colorScheme.error) }, onClick = { showTaskMenu = false; confirmingDelete = true })
                                    }
                                }
                            }
                            resumeRow?.let { row ->
                                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                    Text(
                                        text = if (row.membershipId == currentMembershipId) {
                                            "上次停在 ${row.customer.name}"
                                        } else {
                                            "从名单第一位开始联系"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Button(
                                            onClick = { onContinueTask(row.customer.id, row.membershipId) },
                                            modifier = Modifier.weight(1f).heightIn(min = AppSpacing.touchTarget),
                                        ) {
                                            Text("继续联系")
                                        }
                                        TextButton(onClick = { showAddDialog = true }) {
                                            Text("添加客户")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (task == null) {
                return@LazyColumn
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
                    Text(
                        "名单客户",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(
                            start = AppSpacing.sm, top = AppSpacing.xl, bottom = AppSpacing.sm,
                        ),
                    )
                }
                itemsIndexed(members, key = { _, row -> row.membershipId }) { index, row ->
                    val outerCorner = MaterialTheme.shapes.large.topStart
                    val innerCorner = MaterialTheme.shapes.extraSmall.topStart
                    val top = if (index == 0) outerCorner else innerCorner
                    val bottom = if (index == members.lastIndex) outerCorner else innerCorner
                    WorkbookCustomerRow(
                        ordinal = index + 1,
                        name = row.customer.name,
                        phone = row.customer.phone,
                        result = row.customer.customResult ?: row.customer.status.label,
                        propertyLabel = listOfNotNull(
                            row.building?.takeIf(String::isNotBlank),
                            row.room?.takeIf(String::isNotBlank),
                        ).joinToString(" · ").ifEmpty { null },
                        isCurrent = row.membershipId == currentMembershipId,
                        shape = RoundedCornerShape(top, top, bottom, bottom),
                        onOpen = { onOpenCustomer(row.customer.id, row.membershipId) },
                        onRemove = { removingMember = row },
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                    )
                }
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
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${leads.size} 条线索", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { showCreateDialog = true }) { Text("新增线索") }
                }
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
internal fun CustomerDetailPage(
    customer: CustomerEntity?,
    tasks: List<CustomerListEntity>,
    contactRecords: List<CallRecordEntity>,
    recentTaskContacts: List<TaskContactRecord>,
    customResults: List<String>,
    routeActive: Boolean,
    activeTask: CustomerListEntity?,
    taskMembers: List<TaskCustomerRow>,
    selectedMembershipId: Long?,
    preferredDialSim: String?,
    onPreferredDialSimChange: (String?) -> Unit,
    automaticDialEnabled: Boolean,
    automaticDialDelaySeconds: Int,
    onEdit: (CustomerEntity, String, String) -> Unit,
    onDelete: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenTaskCustomer: (Long, Long) -> Unit,
    onAutomaticDialEnabledChange: (Boolean) -> Unit,
    onAutomaticDialDelayChange: (Int) -> Unit,
    onSaveContact: suspend (Long, ContactStatus, String?, String, Long?, Boolean) -> Boolean,
    workbook: DialWorkbookPushState,
    workbookWidth: Float,
    modifier: Modifier = Modifier,
) {
    var editingCustomer by remember(customer?.id) { mutableStateOf(false) }
    var confirmingDelete by remember(customer?.id) { mutableStateOf(false) }
    var selectedStatus by remember(customer?.id) { mutableStateOf(ContactStatus.NOT_INTERESTED) }
    var customResult by remember(customer?.id) { mutableStateOf<String?>(null) }
    var noteDraft by remember(customer?.id) { mutableStateOf("") }
    var savedDraft by remember(customer?.id) { mutableStateOf<ContactDraft?>(null) }
    var showCustomResult by remember(customer?.id) { mutableStateOf(false) }
    var showDialSettings by remember { mutableStateOf(false) }
    var showCustomerMenu by remember(customer?.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableStateOf<CallReturnCountdown?>(null) }
    var showSimPicker by remember { mutableStateOf(false) }
    var dialSims by remember { mutableStateOf(emptyList<DialSim>()) }
    val context = LocalContext.current
    val phoneDialing = remember(context) { PhoneDialing(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val listState = key(customer?.id, selectedMembershipId) { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentTaskIndex = taskMembers.indexOfFirst { it.membershipId == selectedMembershipId }
    val currentMember = taskMembers.getOrNull(currentTaskIndex)
    val nextMember = if (currentTaskIndex >= 0) taskMembers.getOrNull(currentTaskIndex + 1) else null

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun stopAutomaticDial() {
        attempt = null
        countdown = 0
    }

    fun interruptForIncomingCall() {
        val call = attempt ?: return
        call.onIncomingCall()
        stopAutomaticDial()
        notify("来电已中断自动拨号。")
    }

    fun launchCall(phone: String, armNext: Boolean, automatic: Boolean = false): Boolean {
        val selectedSim = if (preferredDialSim != null) {
            phoneDialing.availableSims().firstOrNull { it.key == preferredDialSim } ?: run {
                stopAutomaticDial()
                notify("默认 SIM 当前不可用，请重新选择。")
                return false
            }
        } else null
        when (val state = phoneDialing.callState()) {
            PhoneCallState.RINGING, PhoneCallState.BUSY -> {
                if (automatic) {
                    if (state == PhoneCallState.RINGING) interruptForIncomingCall()
                    return false
                }
                stopAutomaticDial()
                notify("电话当前占用，已停止拨号。")
                return false
            }
            PhoneCallState.UNKNOWN -> if (automatic) return false
            PhoneCallState.IDLE -> Unit
        }
        stopAutomaticDial()
        if (automaticDialEnabled && armNext) {
            attempt = CallReturnCountdown(SystemClock.elapsedRealtime())
        }
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", phone, null))
            selectedSim?.let { intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it.handle) }
            context.startActivity(intent)
            savedDraft = null
            true
        } catch (_: ActivityNotFoundException) {
            stopAutomaticDial()
            notify("当前设备无法发起电话。")
            false
        } catch (_: SecurityException) {
            stopAutomaticDial()
            notify("需要拨打电话权限才能呼出。")
            false
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && routeActive && customer != null) launchCall(customer.phone, armNext = nextMember != null)
        else if (!granted) notify("需要拨打电话权限才能呼出。")
    }
    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && routeActive) onAutomaticDialEnabledChange(true)
        else if (!granted) notify("开启自动拨号需要电话状态权限。")
    }
    val simPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && routeActive) {
            dialSims = phoneDialing.availableSims()
            showSimPicker = true
        } else if (!granted) notify("选择默认 SIM 需要电话状态权限。")
    }

    suspend fun saveDraft(next: TaskCustomerRow?): Boolean {
        val current = customer ?: return false
        if (saving || !routeActive) return false
        val draft = ContactDraft(selectedStatus, customResult.takeIf { selectedStatus == ContactStatus.CUSTOM }, noteDraft.trim())
        if (next == null && draft == savedDraft) return true
        saving = true
        return try {
            val saved = onSaveContact(current.id, draft.status, draft.customResult, draft.note, next?.membershipId, draft != savedDraft)
            if (saved && next == null) savedDraft = draft
            if (!saved) notify("联系结果保存失败，请重试。")
            saved
        } finally {
            saving = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    attempt?.onPause()
                    countdown = 0
                }
                Lifecycle.Event.ON_RESUME -> dialSims = phoneDialing.availableSims()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context, routeActive, automaticDialEnabled) {
        if (!routeActive || !automaticDialEnabled) return@DisposableEffect onDispose {}
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                    TelephonyManager.EXTRA_STATE_RINGING -> interruptForIncomingCall()
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> attempt?.onOutgoingActivity()
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    val autoDialAllowed by rememberUpdatedState(routeActive && automaticDialEnabled && !showCustomResult && !showSimPicker)
    fun isForeground() = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && view.hasWindowFocus()
    val latestSave by rememberUpdatedState<suspend () -> Boolean> { saveDraft(null) }
    val latestDialNext by rememberUpdatedState<(TaskCustomerRow) -> Boolean> { next ->
        val nextIndex = taskMembers.indexOfFirst { it.membershipId == next.membershipId }
        if (currentTaskIndex < 0 || nextIndex != currentTaskIndex + 1) {
            stopAutomaticDial()
            false
        } else if (launchCall(next.customer.phone, armNext = nextIndex < taskMembers.lastIndex, automatic = true)) {
            onOpenTaskCustomer(next.customer.id, next.membershipId)
            true
        } else false
    }
    LaunchedEffect(attempt, automaticDialEnabled, automaticDialDelaySeconds, nextMember?.membershipId, routeActive) {
        val call = attempt ?: return@LaunchedEffect
        val next = nextMember
        if (!automaticDialEnabled || next == null || !routeActive) {
            stopAutomaticDial()
            return@LaunchedEffect
        }
        while (attempt === call) {
            val phoneState = phoneDialing.callState()
            if (phoneState == PhoneCallState.RINGING) {
                interruptForIncomingCall()
                break
            }
            if (phoneState == PhoneCallState.UNKNOWN) {
                stopAutomaticDial()
                notify("无法读取电话状态，自动拨号已停止。")
                break
            }
            val seconds = call.sample(SystemClock.elapsedRealtime(), isForeground() && autoDialAllowed, phoneState == PhoneCallState.BUSY, automaticDialDelaySeconds)
            countdown = seconds ?: 0
            if (seconds == 0 && !saving) {
                if (!latestSave()) {
                    stopAutomaticDial()
                    break
                }
                if (attempt !== call || call.interrupted) break
                if (isForeground() && autoDialAllowed && latestDialNext(next)) {
                    if (attempt === call) stopAutomaticDial()
                    break
                }
                call.resetCountdown()
            }
            delay(250L)
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val workbookMembers by rememberUpdatedState(taskMembers)
    var advanceProgress by remember(selectedMembershipId) { mutableFloatStateOf(0f) }
    var incomingCustomer by remember(selectedMembershipId) { mutableStateOf<TaskCustomerRow?>(null) }
    var switchDirection by remember(selectedMembershipId) { mutableFloatStateOf(1f) }
    var workbookTargetIndex by remember(selectedMembershipId) { mutableIntStateOf(-1) }
    var advanceAnimation by remember(selectedMembershipId) { mutableStateOf<Job?>(null) }
    var advancing by remember { mutableStateOf(false) }
    var pendingRevealId by remember { mutableStateOf<Long?>(null) }
    var revealProgress by remember(selectedMembershipId) {
        mutableFloatStateOf(if (pendingRevealId != null && pendingRevealId == selectedMembershipId) 0f else 1f)
    }
    val workbookIndex by rememberUpdatedState(workbookTargetIndex.takeIf { it >= 0 } ?: currentTaskIndex)
    val workbookSaving by rememberUpdatedState(saving || advancing || workbookTargetIndex >= 0)
    val density = LocalDensity.current
    LaunchedEffect(selectedMembershipId, customer?.id) {
        if (customer != null && pendingRevealId != null && pendingRevealId == selectedMembershipId) {
            pendingRevealId = null
            animate(0f, 1f, animationSpec = dialDetailArrival) { value, _ ->
                revealProgress = value.coerceIn(0f, 1f)
            }
        }
    }

    fun settleCustomer(
        target: TaskCustomerRow?,
        velocity: Float = 0f,
        save: Boolean = true,
        fromWorkbook: Boolean = false,
    ) {
        if (!routeActive || saving || advancing || workbookTargetIndex >= 0) return
        if (target != null && target.membershipId == selectedMembershipId) return
        if (target != null) {
            val targetIndex = taskMembers.indexOfFirst { it.membershipId == target.membershipId }
            if (targetIndex < 0) return
            switchDirection = dialSwitchDirection(currentTaskIndex, targetIndex)
            incomingCustomer = target
            if (fromWorkbook) workbookTargetIndex = targetIndex
        }
        advanceAnimation?.cancel()
        advanceAnimation = scope.launch {
            if (target != null) {
                stopAutomaticDial()
                focusManager.clearFocus()
                keyboard?.hide()
            }
            animate(
                advanceProgress, if (target != null) 1f else 0f,
                initialVelocity = velocity.coerceIn(-4f, 4f),
                animationSpec = AppMotion.selection(),
            ) { value, _ -> advanceProgress = value.coerceIn(0f, 1f) }
            if (target != null) {
                advancing = true
                pendingRevealId = target.membershipId
                try {
                    if (!save) {
                        onOpenTaskCustomer(target.customer.id, target.membershipId)
                        return@launch
                    }
                    if (saveDraft(target)) return@launch
                    pendingRevealId = null
                    workbookTargetIndex = -1
                    animate(advanceProgress, 0f, animationSpec = AppMotion.selection()) { value, _ ->
                        advanceProgress = value.coerceIn(0f, 1f)
                    }
                } finally {
                    advancing = false
                }
            }
            incomingCustomer = null
        }
    }

    val selectFromWorkbook by rememberUpdatedState<(TaskCustomerRow, Boolean) -> Unit> { member, save ->
        settleCustomer(member, save = save, fromWorkbook = true)
    }

    fun openWorkbook(interactive: Boolean = false): Boolean {
        if (!routeActive || saving || advancing || incomingCustomer != null || currentTaskIndex < 0 || workbook.open) return false
        stopAutomaticDial()
        focusManager.clearFocus()
        keyboard?.hide()
        workbook.show(interactive) { panelModifier ->
            DialWorkbookPanel(
                members = workbookMembers,
                currentIndex = workbookIndex,
                saving = workbookSaving,
                onDismiss = workbook::close,
                onOpen = { selectFromWorkbook(it, false) },
                onSaveAndOpen = { selectFromWorkbook(it, true) },
                modifier = panelModifier,
            )
        }
        return true
    }

    val startWorkbookDrag by rememberUpdatedState { openWorkbook(interactive = true) }
    val finishNextDrag by rememberUpdatedState<(Boolean, Float) -> Unit> { commit, speed ->
        settleCustomer(nextMember.takeIf { commit }, velocity = speed)
    }
    val latestNextMember by rememberUpdatedState(nextMember)
    val swipeModifier = if (routeActive && customer != null && currentTaskIndex >= 0 &&
        !saving && !advancing && workbookTargetIndex < 0 && !workbook.open
    ) {
        var draggingWorkbook = false
        var pageWidth = 0f
        var startingAdvance = 0f
        Modifier.dialHorizontalDrag(
            key = selectedMembershipId to workbookWidth,
            avoidSystemEdge = true,
            onStart = { width, distance ->
                pageWidth = width
                draggingWorkbook = distance > 0f && incomingCustomer == null
                if (draggingWorkbook) {
                    startWorkbookDrag().also { if (it) workbook.dragBy(distance, workbookWidth) }
                } else {
                    val target = latestNextMember
                    if (target == null) false else {
                        advanceAnimation?.cancel()
                        startingAdvance = advanceProgress
                        switchDirection = 1f
                        incomingCustomer = target
                        advanceProgress = (startingAdvance - distance / width).coerceIn(0f, 1f)
                        true
                    }
                }
            },
            onMove = { distance ->
                if (draggingWorkbook) workbook.dragBy(distance, workbookWidth)
                else advanceProgress = (startingAdvance - distance / pageWidth).coerceIn(0f, 1f)
            },
            onFinish = { velocity, released ->
                if (draggingWorkbook) {
                    workbook.finishDrag(velocity, workbookWidth, released)
                } else {
                    val speed = -velocity / pageWidth
                    val threshold = (with(density) { 72.dp.toPx() } / pageWidth).coerceIn(0.15f, 0.35f)
                    val projected = projectedDragPosition(advanceProgress, speed, 0.18f)
                    finishNextDrag(released && projected >= threshold, if (released) speed else 0f)
                }
            },
        )
    } else Modifier

    fun detailsMotion(order: Int): Modifier = Modifier.dialDetailMotion {
        dialDetailVisibility(advanceProgress, revealProgress, order)
    }

    @Composable
    fun DialCard(
        currentCustomer: CustomerEntity,
        member: TaskCustomerRow?,
        hasFollowingCustomer: Boolean,
        preview: Boolean,
        modifier: Modifier = Modifier,
    ) {
        RaisedCard(
            modifier = modifier.fillMaxWidth(),
            motionProfile = CardMotionProfile.Large,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(currentCustomer.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    Box {
                        IconButton(
                            enabled = preview || !advancing,
                            onClick = { if (!preview && incomingCustomer == null) showCustomerMenu = true },
                        ) {
                            Icon(painterResource(R.drawable.ic_more), contentDescription = "客户更多操作")
                        }
                        DropdownMenu(expanded = !preview && showCustomerMenu, onDismissRequest = { showCustomerMenu = false }) {
                            if (member != null) {
                                DropdownMenuItem(
                                    text = { Text("自动拨号设置") },
                                    onClick = {
                                        showCustomerMenu = false
                                        stopAutomaticDial()
                                        showDialSettings = true
                                    },
                                )
                            }
                            DropdownMenuItem(text = { Text("编辑资料") }, onClick = { showCustomerMenu = false; editingCustomer = true })
                            DropdownMenuItem(text = { Text("删除客户", color = MaterialTheme.colorScheme.error) }, onClick = { showCustomerMenu = false; confirmingDelete = true })
                        }
                    }
                }
                Text(
                    currentCustomer.phone,
                    style = DialNumberTypography,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val address = listOfNotNull(member?.building, member?.room).joinToString(" · ")
                if (address.isNotEmpty()) Text("房号  $address", style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    enabled = preview || (!saving && !advancing),
                    onClick = {
                        if (!preview && incomingCustomer == null) {
                            stopAutomaticDial()
                            if (phoneDialing.hasPhoneStatePermission()) {
                                dialSims = phoneDialing.availableSims()
                                showSimPicker = true
                            } else simPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                        }
                    },
                ) {
                    val label = if (preferredDialSim == null) "系统默认"
                    else dialSims.firstOrNull { it.key == preferredDialSim }?.label ?: "SIM 不可用"
                    Text("拨号卡 · $label", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                EditorialButton(
                    label = "拨打当前客户",
                    enabled = preview || (!saving && !advancing),
                    onClick = {
                        if (preview || incomingCustomer != null) return@EditorialButton
                        try {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                launchCall(currentCustomer.phone, armNext = nextMember != null)
                            } else callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                        } catch (_: ActivityNotFoundException) {
                            scope.launch {
                                snackbarHostState.showSnackbar("无法打开系统拨号应用。")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "通话结束后返回应用，才能记录联系结果。自动拨号仅在开启后拨打任务下一位。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (activeTask != null && hasFollowingCustomer) {
                    if (automaticDialEnabled) {
                        Text("自动拨号已开启 · 间隔 ${automaticDialDelaySeconds} 秒", style = MaterialTheme.typography.bodyMedium)
                        if (!preview && countdown > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("${countdown} 秒后拨打下一位", style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = ::stopAutomaticDial) { Text("取消") }
                            }
                        }
                    } else {
                        Text("自动拨号已关闭", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    Box(modifier = modifier.clipToBounds().then(swipeModifier)) {
        LazyColumn(
            state = listState,
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
                item(key = "customer-detail-${currentCustomer.id}", contentType = "dial-card") {
                    DialCard(
                        currentCustomer, currentMember,
                        hasFollowingCustomer = nextMember != null,
                        preview = false,
                        modifier = Modifier.graphicsLayer {
                            // The frame includes the horizontal list insets. Leave those intact
                            // so the preview and the real card land on identical coordinates.
                            translationX = -switchDirection * advanceProgress * (size.width + 40.dp.toPx())
                        }.semantics {
                            if (currentTaskIndex >= 0 && routeActive && !saving) {
                                customActions = buildList {
                                    add(CustomAccessibilityAction("打开客户名单") { openWorkbook() })
                                    if (nextMember != null) {
                                        add(CustomAccessibilityAction("保存并下一位") {
                                            settleCustomer(nextMember)
                                            true
                                        })
                                    }
                                }
                            }
                        },
                    )
                }

                item(key = "contact-result-editor") {
                    RaisedCard(
                        modifier = detailsMotion(0).fillMaxWidth(),
                        motionProfile = CardMotionProfile.Large,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("联系结果", style = MaterialTheme.typography.titleLarge)
                            BoxWithConstraints {
                                val fontScale = LocalDensity.current.fontScale
                                val columns = if (maxWidth < 300.dp || fontScale >= 1.25f) 2 else 3
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ContactStatus.entries.chunked(columns).forEach { rowStatuses ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            rowStatuses.forEach { status ->
                                                ContactStatusChoice(
                                                    selected = selectedStatus == status,
                                                    onClick = {
                                                        if (status == ContactStatus.CUSTOM) {
                                                            showCustomResult = true
                                                        } else selectedStatus = status
                                                    },
                                                    label = if (status == ContactStatus.CUSTOM) customResult ?: status.label else status.label,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
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
                            if (nextMember != null) {
                                TextButton(
                                    enabled = !saving,
                                    onClick = { settleCustomer(nextMember) },
                                ) { Text("保存并下一位") }
                            }
                            EditorialButton(
                                label = "保存联系记录",
                                enabled = !saving && ContactDraft(
                                    selectedStatus, customResult.takeIf { selectedStatus == ContactStatus.CUSTOM }, noteDraft.trim(),
                                ) != savedDraft,
                                onClick = {
                                    scope.launch { saveDraft(null) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                item(key = "contact-history-title") {
                    Text(
                        if (activeTask != null) "名单最近联系记录" else "联系记录",
                        modifier = detailsMotion(1),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (activeTask != null && recentTaskContacts.isNotEmpty()) {
                    items(recentTaskContacts, key = { "task-record-${it.record.id}" }) { entry ->
                        ContactRecordCard(entry.record, "${entry.customerName} · ${entry.phone}", detailsMotion(1))
                    }
                } else if (contactRecords.isEmpty() || activeTask != null) {
                    item(key = "contact-history-empty") {
                        RaisedCard(
                            modifier = detailsMotion(1).fillMaxWidth(),
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
                    items(contactRecords, key = { "record-${it.id}" }) { record ->
                        ContactRecordCard(record = record, modifier = detailsMotion(1))
                    }
                }

                item(key = "customer-tasks-label") {
                    Text("所属任务名单", modifier = detailsMotion(2), style = MaterialTheme.typography.titleMedium)
                }
                if (tasks.isEmpty()) {
                    item(key = "customer-no-tasks") {
                        RaisedCard(
                            modifier = detailsMotion(2).fillMaxWidth(),
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
                    items(tasks, key = { "customer-task-${it.id}" }) { task ->
                        RaisedCard(
                            modifier = detailsMotion(2)
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

        incomingCustomer?.let { incoming ->
            val incomingIndex = taskMembers.indexOfFirst { it.membershipId == incoming.membershipId }
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { translationX = switchDirection * (1f - advanceProgress) * size.width }
                    .clearAndSetSemantics {}
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp),
            ) {
                DialCard(
                    incoming.customer,
                    incoming,
                    hasFollowingCustomer = incomingIndex in 0 until taskMembers.lastIndex,
                    preview = true,
                    modifier = Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true),
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showSimPicker) {
        PreferredDialSimDialog(
            sims = dialSims,
            preferred = preferredDialSim,
            onDismiss = { showSimPicker = false },
            onSelect = { key ->
                onPreferredDialSimChange(key)
                showSimPicker = false
            },
        )
    }
    if (showDialSettings) {
        AutomaticDialSettingsDialog(
            automaticDialEnabled = automaticDialEnabled,
            automaticDialDelaySeconds = automaticDialDelaySeconds,
            onAutomaticDialEnabledChange = { enabled ->
                if (enabled && !phoneDialing.hasPhoneStatePermission()) {
                    phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                } else onAutomaticDialEnabledChange(enabled)
            },
            onAutomaticDialDelayChange = onAutomaticDialDelayChange,
            onDismiss = { showDialSettings = false },
        )
    }
    if (showCustomResult) {
        CustomContactResultDialog(customResult, customResults, onDismiss = { showCustomResult = false }) {
            customResult = it
            selectedStatus = ContactStatus.CUSTOM
            showCustomResult = false
        }
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

private data class ContactDraft(val status: ContactStatus, val customResult: String?, val note: String)

@Composable
private fun PreferredDialSimDialog(
    sims: List<DialSim>,
    preferred: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("默认拨号卡") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("手动和自动拨号共用此设置。", style = MaterialTheme.typography.bodySmall)
                if (sims.isEmpty()) Text("系统暂未返回可用的通话卡。", style = MaterialTheme.typography.bodySmall)
                val choices = listOf(null to "跟随系统默认") + sims.map { it.key to it.label }
                choices.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = key == preferred, role = Role.RadioButton, onClick = { onSelect(key) })
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = key == preferred, onClick = null)
                        Text(label, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun CustomContactResultDialog(
    initial: String?,
    previousResults: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial.orEmpty()) }
    AlertDialog(
        modifier = Modifier.dismissKeyboardOnBlankTap(),
        onDismissRequest = onDismiss,
        title = { Text("自定义联系结果") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(40) },
                    label = { Text("结果名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                previousResults.forEach { result ->
                    TextButton(onClick = { value = result }) { Text(result) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onSelect(value.trim()) }) { Text("使用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ContactStatusChoice(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
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
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ContactRecordCard(
    record: CallRecordEntity,
    customerLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    RaisedCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        motionProfile = CardMotionProfile.Compact,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.customResult ?: record.result.label,
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
            customerLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
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
                Text(customer.phone, style = NumberTypography, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(customer.customResult ?: customer.status.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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

internal val ContactStatus.label: String
    get() = when (this) {
        ContactStatus.NOT_CONTACTED -> "未联系"
        ContactStatus.UNREACHED -> "未接通"
        ContactStatus.CONNECTED -> "已接通"
        ContactStatus.INTERESTED -> "有意向"
        ContactStatus.NOT_INTERESTED -> "暂不考虑"
        ContactStatus.DO_NOT_CONTACT -> "不再联系"
        ContactStatus.CUSTOM -> "自定义"
    }
