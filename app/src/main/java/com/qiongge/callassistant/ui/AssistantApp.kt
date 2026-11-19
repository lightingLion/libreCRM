package com.qiongge.callassistant.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qiongge.callassistant.R
import com.qiongge.callassistant.domain.greeting.GreetingPolicy
import com.qiongge.callassistant.ui.components.dismissKeyboardOnBlankTap
import com.qiongge.callassistant.ui.components.CardRouteMotion
import com.qiongge.callassistant.ui.components.LocalCardRouteMotion
import com.qiongge.callassistant.ui.components.RaisedCard
import com.qiongge.callassistant.ui.navigation.MainDestination
import com.qiongge.callassistant.ui.screens.CustomerDetailPage
import com.qiongge.callassistant.ui.screens.DialWorkbookPushState
import com.qiongge.callassistant.ui.screens.dialHorizontalDrag
import com.qiongge.callassistant.ui.screens.ImportListPage
import com.qiongge.callassistant.ui.screens.LeadsPage
import com.qiongge.callassistant.ui.screens.SettingsDialog
import com.qiongge.callassistant.ui.theme.cardSurface
import com.qiongge.callassistant.ui.components.MainNavigationBar
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
    val dialWorkbook = remember(scope) { DialWorkbookPushState(scope) }
    val screenStateHolder = rememberSaveableStateHolder()
    var selectedDrawerEntry by rememberSaveable { mutableStateOf<String?>(null) }
    val csvDirectoryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::setCsvDirectory)
    }
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
    val isTasksHome = uiState.destination == MainDestination.TASKS && !hasDetailRoute
    BackHandler(enabled = drawerIsOpen || dialWorkbook.open || hasDetailRoute) {
        when {
            drawerIsOpen -> closeDrawer()
            dialWorkbook.open -> dialWorkbook.close()
            else -> viewModel.navigateBack()
        }
    }
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed) {
            viewModel.onDrawerClosed()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
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
        val drawerGestureModifier = if (isTasksHome || drawerState.isOpen) {
            Modifier.pointerInput(isTasksHome, drawerState.isOpen) {
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
                        if (isTasksHome && !drawerWasOpen && totalDrag >= dragThreshold) {
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
            }
        } else {
            Modifier
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().clipToBounds().dismissKeyboardOnBlankTap().then(drawerGestureModifier),
        ) {
            val panelWidth = minOf(320.dp, maxWidth * 0.76f)
            val panelPixels = with(LocalDensity.current) { panelWidth.toPx() }
            val closeWorkbookDrag = if (dialWorkbook.content != null) {
                Modifier.dialHorizontalDrag(
                    key = panelPixels,
                    onStart = { _, distance ->
                        dialWorkbook.beginDrag()
                        dialWorkbook.dragBy(distance, panelPixels)
                        true
                    },
                    onMove = { dialWorkbook.dragBy(it, panelPixels) },
                    onFinish = { velocity, released -> dialWorkbook.finishDrag(velocity, panelPixels, released) },
                )
            } else Modifier
            Box(Modifier.fillMaxSize().then(closeWorkbookDrag)) {
                dialWorkbook.content?.invoke(
                    Modifier.width(panelWidth).fillMaxHeight().graphicsLayer {
                        translationX = panelPixels * (dialWorkbook.progress - 1f)
                    },
                )
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { translationX = panelPixels * dialWorkbook.progress }
                        .then(if (dialWorkbook.open) Modifier.clearAndSetSemantics {} else Modifier),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.navigationBars,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = uiState.customer?.name
                                        ?: uiState.task?.title
                                        ?: uiState.destination.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
                        uiState.selectedCustomerId != null && uiState.selectedMembershipId != null -> "task-dial-${uiState.selectedTaskId}"
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
                                        recentTaskContacts = routeState.recentTaskContacts,
                                        customResults = routeState.customResults,
                                        routeActive = route.key == screenKey,
                                        activeTask = routeState.task,
                                        taskMembers = routeState.taskCustomers,
                                        selectedMembershipId = routeState.selectedMembershipId,
                                        preferredDialSim = routeState.preferredDialSim,
                                        onPreferredDialSimChange = viewModel::setPreferredDialSim,
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
                                        onSaveContact = viewModel::saveContact,
                                        workbook = dialWorkbook,
                                        workbookWidth = panelPixels,
                                        onAutomaticDialEnabledChange = viewModel::setAutomaticDialEnabled,
                                        onAutomaticDialDelayChange = viewModel::setAutomaticDialDelaySeconds,
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
                                            onImportClick = { viewModel.selectDestination(MainDestination.IMPORT) },
                                            onOpenTask = viewModel::openTask,
                                            onContinueTask = viewModel::continueTask,
                                            onRenameTask = viewModel::renameTask,
                                            onDeleteTask = viewModel::deleteTask,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(contentPadding),
                                        )

                                        MainDestination.IMPORT -> ImportListPage(
                                            state = routeState,
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
                if (dialWorkbook.content != null) {
                    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f)
                    Box(
                        Modifier.matchParentSize()
                            .graphicsLayer {
                                translationX = panelPixels * dialWorkbook.progress
                                alpha = dialWorkbook.progress
                            }
                            .background(scrim)
                            .clickable(onClickLabel = "关闭客户名单", onClick = dialWorkbook::close),
                    )
                }
            }
        }
    }

    selectedDrawerEntry?.let { entry ->
        if (entry == "设置") {
            SettingsDialog(
                themeMode = uiState.themeMode,
                directoryLabel = uiState.csvDirectory?.label,
                directoryMessage = uiState.directoryMessage,
                onThemeChange = viewModel::setThemeMode,
                onChooseDirectory = { csvDirectoryLauncher.launch(uiState.csvDirectory?.uri) },
                onClearDirectory = { viewModel.setCsvDirectory(null) },
                onDismiss = { selectedDrawerEntry = null },
            )
        } else if (entry == "数据管理") {
            val directory = uiState.csvDirectory
            val saveCsvAs = { csvExportLauncher.launch("客户资料_${LocalDate.now()}.csv") }
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
                csvDirectoryLabel = directory?.label,
                onExportCsv = { if (directory == null) saveCsvAs() else viewModel.exportCsvToDirectory(directory) },
                onSaveCsvAs = saveCsvAs,
                onExportExcel = { excelExportLauncher.launch("客户资料_${LocalDate.now()}.xlsx") },
                onClearAll = viewModel::clearAllLocalData,
                onDismissMessage = viewModel::dismissDataMessage,
            )
        } else if (entry == "关于作者") {
            AboutAuthorDialog(onDismiss = { selectedDrawerEntry = null })
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
        containerColor = MaterialTheme.colorScheme.cardSurface,
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
    csvDirectoryLabel: String?,
    onExportCsv: () -> Unit,
    onSaveCsvAs: () -> Unit,
    onExportExcel: () -> Unit,
    onClearAll: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    var showClearConfirmation by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("数据管理", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$customerCount 位客户 · $taskCount 份名单。数据只保存在本机。")
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text(if (hasImportDraft) "继续核对当前导入草稿" else "导入 CSV / Excel")
                }
                OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text(if (csvDirectoryLabel == null) "导出 CSV" else "导出 CSV 到默认目录")
                }
                if (csvDirectoryLabel != null) {
                    Text(csvDirectoryLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onSaveCsvAs, enabled = !busy) { Text("CSV 另存为") }
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
        containerColor = MaterialTheme.colorScheme.cardSurface,
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
            .widthIn(max = 320.dp)
            .fillMaxWidth()
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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
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

            Spacer(Modifier.height(32.dp))
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
