package com.qiongge.callassistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qiongge.callassistant.data.local.entity.CallRecordEntity
import com.qiongge.callassistant.data.local.entity.ContactStatus
import com.qiongge.callassistant.data.local.entity.CustomerEntity
import com.qiongge.callassistant.data.local.entity.CustomerListEntity
import com.qiongge.callassistant.data.local.entity.TaskProgressEntity
import com.qiongge.callassistant.data.local.model.TaskCustomerRow
import com.qiongge.callassistant.data.local.model.TaskSummary
import com.qiongge.callassistant.data.local.model.ImportDraftSnapshot
import com.qiongge.callassistant.data.preferences.UserPreferencesRepository
import com.qiongge.callassistant.data.repository.CustomerRepository
import com.qiongge.callassistant.data.repository.ImportWorkflowException
import com.qiongge.callassistant.data.repository.LocalOcrImportRepository
import com.qiongge.callassistant.data.repository.LocalDataManagementRepository
import com.qiongge.callassistant.data.repository.DataExportFormat
import com.qiongge.callassistant.data.repository.OcrImportProgress
import com.qiongge.callassistant.data.repository.TaskRepository
import com.qiongge.callassistant.domain.greeting.GreetingPolicy
import com.qiongge.callassistant.ui.navigation.MainDestination
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.net.Uri
import com.qiongge.callassistant.data.local.entity.ImportDraftRowEntity
import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime
import java.time.LocalDate
import java.io.File

data class MainUiState(
    val destination: MainDestination = MainDestination.TASKS,
    val displayName: String = "",
    val automaticDialEnabled: Boolean = false,
    val automaticDialDelaySeconds: Int = 5,
    val drawerGreeting: String? = null,
    val tasks: List<TaskSummary> = emptyList(),
    val allCustomers: List<CustomerEntity> = emptyList(),
    val leads: List<CustomerEntity> = emptyList(),
    val leadQuery: String = "",
    val leadStatusFilter: ContactStatus? = null,
    val selectedTaskId: Long? = null,
    val task: CustomerListEntity? = null,
    val taskCustomers: List<TaskCustomerRow> = emptyList(),
    val taskProgress: TaskProgressEntity? = null,
    val selectedCustomerId: Long? = null,
    val selectedMembershipId: Long? = null,
    val customer: CustomerEntity? = null,
    val customerTasks: List<CustomerListEntity> = emptyList(),
    val customerContactRecords: List<CallRecordEntity> = emptyList(),
    val importDraft: ImportDraftSnapshot? = null,
    val importReviewOpen: Boolean = false,
    val isOcrProcessing: Boolean = false,
    val isImportCommitting: Boolean = false,
    val ocrProgress: OcrImportProgress? = null,
    val importMessage: String? = null,
    val isDataProcessing: Boolean = false,
    val dataMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val customerRepository: CustomerRepository,
    private val taskRepository: TaskRepository,
    private val localOcrImportRepository: LocalOcrImportRepository,
    private val localDataManagementRepository: LocalDataManagementRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val importDraftMutationMutex = Mutex()

    private val leadQuery = MutableStateFlow("")
    private val leadStatusFilter = MutableStateFlow<ContactStatus?>(null)
    private var drawerRequestId = 0
    private var taskObservation: Job? = null
    private var customerObservation: Job? = null

    init {
        viewModelScope.launch {
            localOcrImportRepository.cleanupOrphanedFiles()
            importDraftMutationMutex.withLock { localOcrImportRepository.prepareImportReview() }
        }
        viewModelScope.launch {
            localOcrImportRepository.observeDraft().collect { draft ->
                _uiState.update { it.copy(importDraft = draft) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.displayName.collect { name ->
                _uiState.update { it.copy(displayName = name) }
            }
        }
        viewModelScope.launch {
            combine(preferencesRepository.automaticDialEnabled, preferencesRepository.automaticDialDelaySeconds) { enabled, seconds -> enabled to seconds }
                .collect { (enabled, seconds) -> _uiState.update { it.copy(automaticDialEnabled = enabled, automaticDialDelaySeconds = seconds) } }
        }
        viewModelScope.launch {
            taskRepository.observeTasks().collect { tasks ->
                _uiState.update { it.copy(tasks = tasks) }
            }
        }
        viewModelScope.launch {
            customerRepository.observeCustomers().collect { customers ->
                _uiState.update { it.copy(allCustomers = customers) }
            }
        }
        viewModelScope.launch {
            combine(leadQuery, leadStatusFilter) { query, status -> query to status }
                .flatMapLatest { (query, status) ->
                    customerRepository.searchCustomers(query, status).map { customers ->
                        Triple(query, status, customers)
                    }
                }
                .collect { (query, status, customers) ->
                    _uiState.update {
                        it.copy(
                            leadQuery = query,
                            leadStatusFilter = status,
                            leads = customers,
                        )
                    }
                }
        }
    }

    fun selectDestination(destination: MainDestination) {
        if (destination != _uiState.value.destination) {
            clearDetailRoutes()
        }
        _uiState.update { it.copy(destination = destination) }
    }

    fun navigateBack() {
        when {
            _uiState.value.selectedCustomerId != null -> closeCustomer()
            _uiState.value.selectedTaskId != null -> closeTask()
        }
    }

    fun setLeadQuery(query: String) {
        leadQuery.value = query
    }

    fun setAutomaticDialEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutomaticDialEnabled(enabled) }
    }

    fun setAutomaticDialDelaySeconds(seconds: Int) {
        viewModelScope.launch { preferencesRepository.setAutomaticDialDelaySeconds(seconds) }
    }

    fun setLeadStatusFilter(status: ContactStatus?) {
        leadStatusFilter.value = status
    }

    fun openTask(taskId: Long) {
        customerObservation?.cancel()
        taskObservation?.cancel()
        _uiState.update {
            it.copy(
                selectedTaskId = taskId,
                task = null,
                taskCustomers = emptyList(),
                taskProgress = null,
                selectedCustomerId = null,
                selectedMembershipId = null,
                customer = null,
                customerTasks = emptyList(),
                customerContactRecords = emptyList(),
            )
        }
        taskObservation = viewModelScope.launch {
            combine(
                taskRepository.observeTask(taskId),
                taskRepository.observeTaskCustomers(taskId),
                taskRepository.observeProgress(taskId),
            ) { task, customers, progress -> Triple(task, customers, progress) }
                .collect { (task, customers, progress) ->
                    _uiState.update {
                        it.copy(
                            task = task,
                            taskCustomers = customers,
                            taskProgress = progress,
                        )
                    }
                    if (task == null && _uiState.value.selectedTaskId == taskId) {
                        closeTask()
                    }
                }
        }
    }

    fun continueTask(taskId: Long, customerId: Long, membershipId: Long) {
        openTask(taskId)
        openCustomer(customerId, taskId, membershipId)
    }

    fun closeTask() {
        taskObservation?.cancel()
        taskObservation = null
        _uiState.update {
            it.copy(
                selectedTaskId = null,
                task = null,
                taskCustomers = emptyList(),
                taskProgress = null,
                selectedMembershipId = null,
            )
        }
    }

    fun createTask(title: String) {
        val cleanedTitle = title.trim()
        if (cleanedTitle.isEmpty()) return
        viewModelScope.launch {
            val id = taskRepository.createTask(CustomerListEntity(title = cleanedTitle))
            selectDestination(MainDestination.TASKS)
            openTask(id)
        }
    }

    fun renameTask(task: CustomerListEntity, title: String) {
        val cleanedTitle = title.trim()
        if (cleanedTitle.isEmpty()) return
        viewModelScope.launch {
            taskRepository.updateTask(
                task.copy(
                    title = cleanedTitle,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            taskRepository.deleteTask(taskId)
            if (_uiState.value.selectedTaskId == taskId) closeTask()
        }
    }

    fun addNewCustomerToTask(taskId: Long, name: String, phone: String) {
        val cleanedName = name.trim()
        val cleanedPhone = phone.trim()
        if (cleanedName.isEmpty() || cleanedPhone.isEmpty()) return
        viewModelScope.launch {
            val customerId = customerRepository.saveCustomer(
                CustomerEntity(name = cleanedName, phone = cleanedPhone),
            )
            taskRepository.addCustomerToTask(taskId, customerId)
        }
    }

    fun addExistingCustomerToTask(taskId: Long, customerId: Long) {
        viewModelScope.launch {
            taskRepository.addCustomerToTask(taskId, customerId)
        }
    }

    fun removeCustomerFromTask(taskId: Long, customerId: Long) {
        viewModelScope.launch {
            taskRepository.removeCustomerFromTask(taskId, customerId)
        }
    }

    fun createCustomer(name: String, phone: String) {
        val cleanedName = name.trim()
        val cleanedPhone = phone.trim()
        if (cleanedName.isEmpty() || cleanedPhone.isEmpty()) return
        viewModelScope.launch {
            customerRepository.saveCustomer(
                CustomerEntity(name = cleanedName, phone = cleanedPhone),
            )
        }
    }

    fun updateCustomer(customer: CustomerEntity, name: String, phone: String) {
        val cleanedName = name.trim()
        val cleanedPhone = phone.trim()
        if (cleanedName.isEmpty() || cleanedPhone.isEmpty()) return
        viewModelScope.launch {
            customerRepository.updateCustomer(
                customer.copy(
                    name = cleanedName,
                    phone = cleanedPhone,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun deleteCustomer(customerId: Long) {
        viewModelScope.launch {
            taskRepository.removeCustomerFromAllTasks(customerId)
            customerRepository.deleteCustomer(customerId)
            if (_uiState.value.selectedCustomerId == customerId) closeCustomer()
        }
    }

    fun openCustomer(customerId: Long, fromTaskId: Long? = null, membershipId: Long? = null) {
        customerObservation?.cancel()
        if (fromTaskId != null && membershipId != null) {
            viewModelScope.launch {
                taskRepository.saveProgress(
                    TaskProgressEntity(
                        listId = fromTaskId,
                        currentMembershipId = membershipId,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        _uiState.update {
            it.copy(
                selectedCustomerId = customerId,
                selectedTaskId = fromTaskId,
                selectedMembershipId = membershipId,
                customer = null,
                customerTasks = emptyList(),
                customerContactRecords = emptyList(),
            )
        }
        customerObservation = viewModelScope.launch {
            combine(
                customerRepository.observeCustomer(customerId),
                taskRepository.observeListsForCustomer(customerId),
                taskRepository.observeContactRecords(customerId),
            ) { customer, tasks, records -> Triple(customer, tasks, records) }
                .collect { (customer, tasks, records) ->
                    _uiState.update {
                        it.copy(
                            customer = customer,
                            customerTasks = tasks,
                            customerContactRecords = records,
                        )
                    }
                    if (customer == null && _uiState.value.selectedCustomerId == customerId) {
                        closeCustomer()
                    }
                }
        }
    }

    fun closeCustomer() {
        customerObservation?.cancel()
        customerObservation = null
        _uiState.update {
            it.copy(
                selectedCustomerId = null,
                selectedMembershipId = null,
                customer = null,
                customerTasks = emptyList(),
                customerContactRecords = emptyList(),
            )
        }
    }

    fun recordContact(customerId: Long, result: ContactStatus, note: String) {
        val customer = _uiState.value.customer ?: return
        if (customer.id != customerId) return
        val cleanedNote = note.trim().takeIf(String::isNotEmpty)
        if (result == customer.status && cleanedNote == null) return
        val listId = _uiState.value.selectedTaskId
        viewModelScope.launch {
            taskRepository.recordContact(
                customerId = customerId,
                listId = listId,
                result = result,
                note = cleanedNote,
            )
        }
    }

    fun openImportReview() {
        viewModelScope.launch {
            try {
                importDraftMutationMutex.withLock { localOcrImportRepository.prepareImportReview() }
                _uiState.update { it.copy(importReviewOpen = true, importMessage = null) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        importReviewOpen = true,
                        importMessage = (exception as? ImportWorkflowException)?.message ?: "导入草稿暂时没能整理，请稍后重试。",
                    )
                }
            }
        }
    }

    fun closeImportReview() {
        if (!_uiState.value.isOcrProcessing && !_uiState.value.isImportCommitting) {
            _uiState.update { it.copy(importReviewOpen = false, importMessage = null) }
        }
    }

    fun addImportImages(uris: List<Uri>, temporaryFiles: List<File> = emptyList()) {
        if (uris.isEmpty() || _uiState.value.isOcrProcessing) {
            temporaryFiles.forEach(File::delete)
            return
        }
        _uiState.update {
            it.copy(
                importReviewOpen = true,
                isOcrProcessing = true,
                ocrProgress = OcrImportProgress(0, uris.size),
                importMessage = null,
            )
        }
        viewModelScope.launch {
            try {
                val result = localOcrImportRepository.appendImages(
                    uris = uris,
                    defaultTitle = "导入名单 ${LocalDate.now()}",
                    onProgress = { progress ->
                        _uiState.update { it.copy(ocrProgress = progress) }
                    },
                )
                val message = when {
                    result.failedImageCount > 0 -> "部分图片没有识别好，可以重试，或手动补充客户。"
                    result.recognizedRowCount == 0 && result.skippedRowCount > 0 -> "识别到的 ${result.skippedRowCount} 行因缺项、号码无效或重复已自动跳过；可查看跳过记录，或手动补充客户。"
                    result.recognizedRowCount == 0 -> "没有识别出名单行，可以重试，或手动补充客户。"
                    else -> "已识别 ${result.recognizedRowCount} 条可用行；缺项、无效和后续重复号码已自动跳过。"
                }
                _uiState.update { it.copy(importMessage = message) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(importMessage = (exception as? ImportWorkflowException)?.message ?: "图片暂时没处理好，请重试或手动录入。")
                }
            } finally {
                temporaryFiles.forEach(File::delete)
                _uiState.update { it.copy(isOcrProcessing = false, ocrProgress = null) }
            }
        }
    }

    fun retryImportImage(imageId: Long) {
        if (_uiState.value.isOcrProcessing) return
        _uiState.update { it.copy(isOcrProcessing = true, importMessage = null) }
        viewModelScope.launch {
            try {
                val count = localOcrImportRepository.retryImage(imageId)
                _uiState.update {
                    it.copy(importMessage = if (count == 0) "这张图片暂时没识别好，可以手动补充。" else "重新识别出 $count 条可用行；缺项、无效和后续重复号码已自动跳过。")
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(importMessage = (exception as? ImportWorkflowException)?.message ?: "这张图片暂时没识别好，可以重试或手动录入。")
                }
            } finally {
                _uiState.update { it.copy(isOcrProcessing = false) }
            }
        }
    }

    fun importTabularFile(uri: Uri) {
        if (_uiState.value.isOcrProcessing || _uiState.value.isImportCommitting) return
        _uiState.update {
            it.copy(
                destination = MainDestination.IMPORT,
                importReviewOpen = true,
                isOcrProcessing = true,
                ocrProgress = null,
                importMessage = "正在读取 CSV / Excel 文件…",
            )
        }
        viewModelScope.launch {
            try {
                val result = localOcrImportRepository.importTabularFile(uri)
                _uiState.update {
                    it.copy(
                        importMessage = "${result.fileName} 已读取：${result.readyRowCount} 行可导入，${result.skippedRowCount} 行因缺项、号码无效或重复已自动跳过。",
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(importMessage = (exception as? ImportWorkflowException)?.message ?: "文件没有读取成功，请检查格式后重试。")
                }
            } finally {
                _uiState.update { it.copy(isOcrProcessing = false, ocrProgress = null) }
            }
        }
    }

    fun applyImportFileFieldMapping(fileName: String, nameColumn: Int, phoneColumn: Int) {
        if (_uiState.value.isOcrProcessing || _uiState.value.isImportCommitting) return
        _uiState.update {
            it.copy(isOcrProcessing = true, ocrProgress = null, importMessage = "正在按所选列更新文件行…")
        }
        viewModelScope.launch {
            try {
                val count = importDraftMutationMutex.withLock {
                    localOcrImportRepository.applyFileFieldMapping(fileName, nameColumn, phoneColumn)
                }
                _uiState.update {
                    it.copy(importMessage = "已将第${nameColumn + 1}列设为姓名、第${phoneColumn + 1}列设为手机号，应用到文件 $count 行；请继续核对。")
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(importMessage = (exception as? ImportWorkflowException)?.message ?: "字段没有应用成功，请重试。")
                }
            } finally {
                _uiState.update { it.copy(isOcrProcessing = false, ocrProgress = null) }
            }
        }
    }

    fun exportCustomerData(uri: Uri, format: DataExportFormat) {
        if (_uiState.value.isDataProcessing) return
        _uiState.update { it.copy(isDataProcessing = true, dataMessage = null) }
        viewModelScope.launch {
            try {
                val count = localDataManagementRepository.exportCustomers(uri, format)
                val label = if (format == DataExportFormat.CSV) "CSV" else "Excel"
                _uiState.update { it.copy(dataMessage = "已导出 $count 位客户的 $label 文件。") }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(dataMessage = (exception as? ImportWorkflowException)?.message ?: "文件导出失败，请重新选择保存位置。") }
            } finally {
                _uiState.update { it.copy(isDataProcessing = false) }
            }
        }
    }

    fun clearAllLocalData() {
        if (_uiState.value.isDataProcessing || _uiState.value.isOcrProcessing || _uiState.value.isImportCommitting) return
        _uiState.update { it.copy(isDataProcessing = true, dataMessage = null) }
        viewModelScope.launch {
            try {
                importDraftMutationMutex.withLock { localOcrImportRepository.discardDraft() }
                localDataManagementRepository.clearAllData()
                clearDetailRoutes()
                _uiState.update { it.copy(importDraft = null, dataMessage = "本机客户、名单、联系记录和导入草稿已清空。") }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(dataMessage = "清理没有完成，请稍后重试。") }
            } finally {
                _uiState.update { it.copy(isDataProcessing = false) }
            }
        }
    }

    fun dismissDataMessage() {
        _uiState.update { it.copy(dataMessage = null) }
    }

    fun applyImportFieldMapping(imageId: Long, nameColumn: Int, phoneColumn: Int) {
        if (_uiState.value.isOcrProcessing || _uiState.value.isImportCommitting) return
        _uiState.update {
            it.copy(
                isOcrProcessing = true,
                ocrProgress = null,
                importMessage = "正在按所选列更新本表…",
            )
        }
        viewModelScope.launch {
            try {
                val count = importDraftMutationMutex.withLock {
                    localOcrImportRepository.applyFieldMapping(imageId, nameColumn, phoneColumn)
                }
                _uiState.update {
                    it.copy(importMessage = "已将第${nameColumn + 1}列设为姓名、第${phoneColumn + 1}列设为手机号，应用到本表 $count 行；请继续核对。")
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(importMessage = (exception as? ImportWorkflowException)?.message ?: "字段没有应用成功，请重试。")
                }
            } finally {
                _uiState.update { it.copy(isOcrProcessing = false, ocrProgress = null) }
            }
        }
    }

    fun updateImportTitle(title: String) {
        viewModelScope.launch { importDraftMutationMutex.withLock { localOcrImportRepository.updateTitle(title) } }
    }

    fun updateImportRow(row: ImportDraftRowEntity) {
        viewModelScope.launch { importDraftMutationMutex.withLock { localOcrImportRepository.updateRow(row) } }
    }

    fun addManualImportRow() {
        viewModelScope.launch {
            try {
                importDraftMutationMutex.withLock { localOcrImportRepository.addManualRow() }
                _uiState.update { it.copy(importReviewOpen = true, importMessage = null) }
            } catch (exception: Exception) {
                _uiState.update { it.copy(importMessage = "无法新增待核对客户，请稍后重试。") }
            }
        }
    }

    fun deleteImportRow(rowId: Long) {
        viewModelScope.launch { importDraftMutationMutex.withLock { localOcrImportRepository.deleteRow(rowId) } }
    }

    fun dismissImportMessage() {
        _uiState.update { it.copy(importMessage = null) }
    }

    fun discardImportDraft() {
        if (_uiState.value.isOcrProcessing || _uiState.value.isImportCommitting) return
        viewModelScope.launch {
            try {
                importDraftMutationMutex.withLock { localOcrImportRepository.discardDraft() }
                _uiState.update {
                    it.copy(importDraft = null, importReviewOpen = false, importMessage = null)
                }
            } catch (exception: Exception) {
                _uiState.update { it.copy(importMessage = "草稿暂时没能删除，请重试。") }
            }
        }
    }

    fun commitImportDraft() {
        if (_uiState.value.isImportCommitting || _uiState.value.isOcrProcessing) return
        _uiState.update { it.copy(isImportCommitting = true, importMessage = null) }
        viewModelScope.launch {
            try {
                val result = importDraftMutationMutex.withLock { localOcrImportRepository.commitDraft() }
                _uiState.update {
                    it.copy(
                        importDraft = null,
                        importReviewOpen = false,
                        destination = MainDestination.TASKS,
                        isImportCommitting = false,
                    )
                }
                clearDetailRoutes()
                continueTask(result.listId, result.firstCustomerId, result.firstMembershipId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isImportCommitting = false,
                        importMessage = (exception as? ImportWorkflowException)?.message ?: "名单没有保存成功，请检查后重试。",
                    )
                }
            }
        }
    }

    private fun clearDetailRoutes() {
        customerObservation?.cancel()
        taskObservation?.cancel()
        customerObservation = null
        taskObservation = null
        _uiState.update {
            it.copy(
                selectedTaskId = null,
                task = null,
                taskCustomers = emptyList(),
                taskProgress = null,
                selectedCustomerId = null,
                selectedMembershipId = null,
                customer = null,
                customerTasks = emptyList(),
                customerContactRecords = emptyList(),
            )
        }
    }

    fun onDrawerOpened() {
        val requestId = ++drawerRequestId
        val now = LocalDateTime.now()
        _uiState.update {
            it.copy(
                drawerGreeting = GreetingPolicy.regularGreeting(now, it.displayName),
            )
        }
        viewModelScope.launch {
            val greeting = preferencesRepository.greetingForMenu(LocalDateTime.now())
            if (requestId == drawerRequestId) {
                _uiState.update { it.copy(drawerGreeting = greeting) }
            }
        }
    }

    fun onDrawerClosed() {
        drawerRequestId += 1
        _uiState.update { it.copy(drawerGreeting = null) }
    }
}

class MainViewModelFactory(
    private val preferencesRepository: UserPreferencesRepository,
    private val customerRepository: CustomerRepository,
    private val taskRepository: TaskRepository,
    private val localOcrImportRepository: LocalOcrImportRepository,
    private val localDataManagementRepository: LocalDataManagementRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(
                preferencesRepository = preferencesRepository,
                customerRepository = customerRepository,
                taskRepository = taskRepository,
                localOcrImportRepository = localOcrImportRepository,
                localDataManagementRepository = localDataManagementRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
    }
}
