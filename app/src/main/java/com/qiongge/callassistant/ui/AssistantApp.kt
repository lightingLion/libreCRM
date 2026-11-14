package com.qiongge.callassistant.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qiongge.callassistant.R
import com.qiongge.callassistant.domain.greeting.GreetingPolicy
import com.qiongge.callassistant.ui.components.CardRouteMotion
import com.qiongge.callassistant.ui.components.LocalCardRouteMotion
import com.qiongge.callassistant.ui.components.RaisedCard
import com.qiongge.callassistant.ui.navigation.MainDestination
import com.qiongge.callassistant.ui.screens.CustomerDetailPage
import com.qiongge.callassistant.ui.screens.ImportListPage
import com.qiongge.callassistant.ui.screens.LeadsPage
import com.qiongge.callassistant.ui.screens.TaskDetailPage
import com.qiongge.callassistant.ui.screens.TasksPage
import com.qiongge.callassistant.ui.theme.G2CornerShape
import com.qiongge.callassistant.data.repository.DataExportFormat
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

private data class DrawerEntry(
    val title: String,
    val iconRes: Int,
)

private data class RouteContentState(
    val key: String,
    val uiState: MainUiState,
) {
    val navigationOrder: Int
        get() {
            val destinationOrder = when (uiState.destination) {
                MainDestination.TASKS -> 0
                MainDestination.IMPORT -> 1
                MainDestination.LEADS -> 2
            }
            val detailDepth = when {
                uiState.selectedCustomerId != null && uiState.selectedTaskId != null -> 2
                uiState.selectedCustomerId != null || uiState.selectedTaskId != null -> 1
                else -> 0
            }
            return destinationOrder * 10 + detailDepth
        }
}

private val drawerEntries = listOf(
    DrawerEntry("设置", R.drawable.ic_settings),
    DrawerEntry("数据管理", R.drawable.ic_storage),
    DrawerEntry("关于作者", R.drawable.ic_info),
)

private const val AUTHOR_EMAIL = "hjian9792@gmail.com"

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AssistantApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val screenStateHolder = rememberSaveableStateHolder()
    var selectedDrawerEntry by remember { mutableStateOf<String?>(null) }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportCustomerData(it, DataExportFormat.CSV) }
    }
    val excelExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri ->
        uri?.let { viewModel.exportCustomerData(it, DataExportFormat.XLSX) }
    }

    val openDrawer: () -> Unit = {
        if (!drawerState.isOpen) {
            viewModel.onDrawerOpened()
            scope.launch { drawerState.open() }
        }
    }
    val closeDrawer: () -> Unit = {
        scope.launch { drawerState.close() }
    }

    val drawerIsOpen = drawerState.currentValue == DrawerValue.Open ||
        drawerState.targetValue == DrawerValue.Open
    val hasDetailRoute = uiState.selectedCustomerId != null || uiState.selectedTaskId != null
    BackHandler(enabled = drawerIsOpen || hasDetailRoute) {
        if (drawerIsOpen) closeDrawer() else viewModel.navigateBack()
    }
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed) {
            viewModel.onDrawerClosed()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        scrimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
        drawerContent = {
            AssistantDrawer(
                greeting = uiState.drawerGreeting
                    ?: GreetingPolicy.regularGreeting(LocalDateTime.now(), uiState.displayName),
                onEntryClick = { entry ->
                    selectedDrawerEntry = entry
                    closeDrawer()
                },
                onCloseGesture = closeDrawer,
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(drawerState.isOpen) {
                    val edgeInset = 36.dp.toPx()
                    val dragThreshold = 72.dp.toPx()
                    val drawerWasOpen = drawerState.isOpen
                    var canTrackDrag = false
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { position ->
                            // Leave Android's left and right system-back regions untouched.
                            canTrackDrag =
                                position.x >= edgeInset &&
                                    position.x <= size.width - edgeInset
                            totalDrag = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!canTrackDrag) return@detectHorizontalDragGestures
                            totalDrag += dragAmount
                            if (!drawerWasOpen && totalDrag >= dragThreshold) {
                                canTrackDrag = false
                                openDrawer()
                            } else if (drawerWasOpen && totalDrag <= -dragThreshold) {
                                canTrackDrag = false
                                closeDrawer()
                            }
                        },
                        onDragEnd = { canTrackDrag = false },
                        onDragCancel = { canTrackDrag = false },
                    )
                },
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.navigationBars,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = uiState.customer?.name
                                    ?: uiState.task?.title
                                    ?: uiState.destination.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    if (hasDetailRoute) viewModel.navigateBack() else openDrawer()
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (hasDetailRoute) R.drawable.ic_arrow_back else R.drawable.ic_menu,
                                    ),
                                    contentDescription = if (hasDetailRoute) "返回" else "打开侧边菜单",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                },
                bottomBar = {
                    MainNavigationBar(
                        selected = uiState.destination,
                        onSelect = viewModel::selectDestination,
                    )
                },
            ) { contentPadding ->
                val screenKey = when {
                    uiState.selectedCustomerId != null -> "customer-${uiState.selectedCustomerId}"
                    uiState.selectedTaskId != null -> "task-${uiState.selectedTaskId}"
                    else -> "destination-${uiState.destination.name}"
                }
                val routeTarget = RouteContentState(screenKey, uiState)
                val routeTransition = updateTransition(
                    targetState = routeTarget,
                    label = "mainRouteTransition",
                )
                routeTransition.AnimatedContent(
                    contentKey = { it.key },
                    transitionSpec = {
                        val duration = 180
                        val movesForward = targetState.navigationOrder >= initialState.navigationOrder
                        val enterFrom = if (movesForward) 1 else -1
                        val exitTo = -enterFrom
                        (
                            (slideInHorizontally(
                                animationSpec = tween(duration, easing = FastOutSlowInEasing),
                            ) { width -> width * enterFrom / 5 } + fadeIn(tween(duration))) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(duration, easing = FastOutSlowInEasing),
                                ) { width -> width * exitTo / 14 } + fadeOut(tween(duration)))
                            ).using(SizeTransform(clip = false))
                    },
                ) { route ->
                    val routeState = route.uiState
                    val routeIsTransitioning = routeTransition.currentState.key != routeTransition.targetState.key
                    CompositionLocalProvider(
                        LocalCardRouteMotion provides CardRouteMotion(
                            routeKey = route.key,
                            enabled = routeIsTransitioning,
                            entering = route.key == routeTransition.targetState.key,
                        ),
                    ) {
                        screenStateHolder.SaveableStateProvider(route.key) {
                            when {
                                routeState.selectedCustomerId != null -> CustomerDetailPage(
                                    customer = routeState.customer,
                                    tasks = routeState.customerTasks,
                                    contactRecords = routeState.customerContactRecords,
                                    activeTask = routeState.task,
                                    taskMembers = routeState.taskCustomers,
                                    selectedMembershipId = routeState.selectedMembershipId,
                                    automaticDialEnabled = routeState.automaticDialEnabled,
                                    automaticDialDelaySeconds = routeState.automaticDialDelaySeconds,
                                    onEdit = viewModel::updateCustomer,
                                    onDelete = viewModel::deleteCustomer,
                                    onOpenTask = viewModel::openTask,
                                    onOpenTaskCustomer = { customerId, membershipId ->
                                        routeState.selectedTaskId?.let { taskId ->
                                            viewModel.openCustomer(customerId, taskId, membershipId)
                                        }
                                    },
                                    onRecordContact = viewModel::recordContact,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(contentPadding),
                                )

                                routeState.selectedTaskId != null -> {
                                    val taskId = routeState.selectedTaskId
                                    TaskDetailPage(
                                        task = routeState.task,
                                        members = routeState.taskCustomers,
                                        allCustomers = routeState.allCustomers,
                                        progress = routeState.taskProgress,
                                        onCreateCustomer = { name, phone ->
                                            viewModel.addNewCustomerToTask(taskId, name, phone)
                                        },
                                        onAddExistingCustomer = { customerId ->
                                            viewModel.addExistingCustomerToTask(taskId, customerId)
                                        },
                                        onOpenCustomer = { customerId, membershipId ->
                                            viewModel.openCustomer(customerId, taskId, membershipId)
                                        },
                                        onContinueTask = { customerId, membershipId ->
                                            viewModel.openCustomer(customerId, taskId, membershipId)
                                        },
                                        onRemoveCustomer = { customerId ->
                                            viewModel.removeCustomerFromTask(taskId, customerId)
                                        },
                                        onRenameTask = viewModel::renameTask,
                                        onDeleteTask = viewModel::deleteTask,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(contentPadding),
                                    )
                                }

                                else -> when (routeState.destination) {
                                    MainDestination.TASKS -> TasksPage(
                                        tasks = routeState.tasks,
                                        onCreateTask = viewModel::createTask,
                                        onOpenTask = viewModel::openTask,
                                        onContinueTask = viewModel::continueTask,
                                        onRenameTask = viewModel::renameTask,
                                        onDeleteTask = viewModel::deleteTask,
                                        automaticDialEnabled = routeState.automaticDialEnabled,
                                        automaticDialDelaySeconds = routeState.automaticDialDelaySeconds,
                                        onAutomaticDialEnabledChange = viewModel::setAutomaticDialEnabled,
                                        onAutomaticDialDelayChange = viewModel::setAutomaticDialDelaySeconds,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(contentPadding),
                                    )

                                    MainDestination.IMPORT -> ImportListPage(
                                        state = routeState,
                                        onTasksClick = { viewModel.selectDestination(MainDestination.TASKS) },
                                        onAddImages = viewModel::addImportImages,
                                        onAddTabularFile = viewModel::importTabularFile,
                                        onUpdateTitle = viewModel::updateImportTitle,
                                        onAddManualRow = viewModel::addManualImportRow,
                                        onUpdateRow = viewModel::updateImportRow,
                                        onDeleteRow = viewModel::deleteImportRow,
                                        onRetryImage = viewModel::retryImportImage,
                                        onApplyFieldMapping = viewModel::applyImportFieldMapping,
                                        onApplyFileFieldMapping = viewModel::applyImportFileFieldMapping,
                                        onDiscardDraft = viewModel::discardImportDraft,
                                        onCommitDraft = viewModel::commitImportDraft,
                                        onDismissMessage = viewModel::dismissImportMessage,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(contentPadding),
                                    )

                                    MainDestination.LEADS -> LeadsPage(
                                        leads = routeState.leads,
                                        query = routeState.leadQuery,
                                        statusFilter = routeState.leadStatusFilter,
                                        onQueryChange = viewModel::setLeadQuery,
                                        onStatusFilter = viewModel::setLeadStatusFilter,
                                        onAddCustomer = viewModel::createCustomer,
                                        onOpenCustomer = viewModel::openCustomer,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(contentPadding),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedDrawerEntry?.let { entry ->
        if (entry == "数据管理") {
            DataManagementDialog(
                customerCount = uiState.allCustomers.size,
                taskCount = uiState.tasks.size,
                hasImportDraft = uiState.importDraft != null,
                busy = uiState.isDataProcessing,
                message = uiState.dataMessage,
                onDismiss = { selectedDrawerEntry = null },
                onImport = {
                    selectedDrawerEntry = null
                    viewModel.selectDestination(MainDestination.IMPORT)
                },
                onExportCsv = { csvExportLauncher.launch("客户资料_${LocalDate.now()}.csv") },
                onExportExcel = { excelExportLauncher.launch("客户资料_${LocalDate.now()}.xlsx") },
                onClearAll = viewModel::clearAllLocalData,
                onDismissMessage = viewModel::dismissDataMessage,
            )
        } else if (entry == "关于作者") {
            AboutAuthorDialog(onDismiss = { selectedDrawerEntry = null })
        } else {
            AlertDialog(
                onDismissRequest = { selectedDrawerEntry = null },
                title = { Text(entry, style = MaterialTheme.typography.titleLarge) },
                text = {
                    Text(
                        text = "这个入口将在后续阶段开放。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { selectedDrawerEntry = null }) {
                        Text("知道了")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

@Composable
private fun AboutAuthorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于作者", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("穷哥们电销助手，帮助整理客户名单和联系进度。")
                Text("招聘与工作机会交流，欢迎发邮件联系作者。")
                TextButton(
                    onClick = {
                        val emailIntent = Intent(
                            Intent.ACTION_SENDTO,
                            Uri.fromParts("mailto", AUTHOR_EMAIL, null),
                        ).putExtra(Intent.EXTRA_SUBJECT, "工作机会交流")
                        context.startActivity(emailIntent)
                    },
                ) {
                    Text(AUTHOR_EMAIL)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun DataManagementDialog(
    customerCount: Int,
    taskCount: Int,
    hasImportDraft: Boolean,
    busy: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onExportCsv: () -> Unit,
    onExportExcel: () -> Unit,
    onClearAll: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    var showClearConfirmation by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("数据管理", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$customerCount 位客户 · $taskCount 份名单。数据只保存在本机。")
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text(if (hasImportDraft) "继续核对当前导入草稿" else "导入 CSV / Excel")
                }
                OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text("导出 CSV")
                }
                OutlinedButton(onClick = onExportExcel, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text("导出 Excel（.xlsx）")
                }
                OutlinedButton(onClick = { showClearConfirmation = true }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text("清空本机数据", color = MaterialTheme.colorScheme.error)
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Text("正在处理…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                message?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onDismissMessage, modifier = Modifier.align(Alignment.End)) { Text("关闭提示") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("完成") } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("清空本机所有数据？") },
            text = { Text("客户、名单、联系记录和未完成的导入草稿都会删除，且无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    onClearAll()
                }) { Text("确认清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun AssistantDrawer(
    greeting: String,
    onEntryClick: (String) -> Unit,
    onCloseGesture: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier
            .width(320.dp)
            .pointerInput(onCloseGesture) {
                val systemBackEdge = 36.dp.toPx()
                val closeThreshold = 64.dp.toPx()
                var canTrackDrag = false
                var totalDrag = 0f

                detectHorizontalDragGestures(
                    onDragStart = { position ->
                        // Keep the left system-back edge available for predictive Back.
                        canTrackDrag = position.x >= systemBackEdge
                        totalDrag = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (!canTrackDrag) return@detectHorizontalDragGestures
                        totalDrag += dragAmount
                        if (totalDrag <= -closeThreshold) {
                            canTrackDrag = false
                            onCloseGesture()
                        }
                    },
                    onDragEnd = { canTrackDrag = false },
                    onDragCancel = { canTrackDrag = false },
                )
            },
        drawerShape = G2CornerShape(
            topStart = 0.dp,
            topEnd = 26.dp,
            bottomEnd = 26.dp,
            bottomStart = 0.dp,
        ),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            RaisedCard(
                modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 22.dp),
            ) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }

            drawerEntries.forEach { entry ->
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                    selected = false,
                    onClick = { onEntryClick(entry.title) },
                    icon = {
                        Icon(
                            painter = painterResource(entry.iconRes),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.padding(vertical = 3.dp),
                    colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = MaterialTheme.colorScheme.surface,
                        unselectedIconColor = MaterialTheme.colorScheme.secondary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }

            Spacer(Modifier.weight(1f))
            Text(
                text = "穷哥们电销助手",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MainNavigationBar(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
) {
    val destinations = listOf(
        Triple(MainDestination.TASKS, "已有任务", R.drawable.ic_folder_open),
        Triple(MainDestination.IMPORT, "导入名单", R.drawable.ic_image_add),
        Triple(MainDestination.LEADS, "线索管理", R.drawable.ic_people),
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars,
    ) {
        destinations.forEach { (destination, label, iconRes) ->
            val isSelected = destination == selected
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(destination) },
                icon = {
                    NavigationIconWell(
                        iconRes = iconRes,
                        label = label,
                        selected = isSelected,
                        pressed = pressed,
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.surface,
                ),
                interactionSource = interactionSource,
            )
        }
    }
}

@Composable
private fun NavigationIconWell(
    iconRes: Int,
    label: String,
    selected: Boolean,
    pressed: Boolean,
) {
    val selectionProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "navigationSelection",
    )
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "navigationPress",
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (selected && !pressed) 0.8.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "navigationShadow",
    )
    val surfaceMix = (selectionProgress * 0.76f + pressProgress * 0.16f).coerceIn(0f, 1f)
    val insetMix = (selectionProgress * 0.52f + pressProgress * 0.3f).coerceIn(0f, 0.72f)
    val shape = G2CornerShape(15.dp)

    Box(
        modifier = Modifier
            .size(38.dp)
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
            )
            .background(
                color = lerp(
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.surfaceVariant,
                    surfaceMix,
                ),
                shape = shape,
            )
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = insetMix),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = lerp(
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.colorScheme.primary,
                selectionProgress,
            ),
            modifier = Modifier.size(22.dp),
        )
    }
}
