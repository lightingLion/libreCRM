package com.qiongge.callassistant.data.repository

import com.qiongge.callassistant.data.local.dao.CustomerDao
import com.qiongge.callassistant.data.local.dao.TaskDao
import com.qiongge.callassistant.data.local.entity.CustomerEntity
import com.qiongge.callassistant.data.local.entity.CustomerListEntity
import com.qiongge.callassistant.data.local.entity.ContactStatus
import com.qiongge.callassistant.data.local.entity.CallRecordEntity
import com.qiongge.callassistant.data.local.entity.ListMembershipEntity
import com.qiongge.callassistant.data.local.entity.TaskProgressEntity
import com.qiongge.callassistant.data.local.model.TaskCustomerRow
import com.qiongge.callassistant.data.local.model.TaskSummary
import kotlinx.coroutines.flow.Flow

interface CustomerRepository {
    fun observeCustomers(): Flow<List<CustomerEntity>>
    fun observeCustomer(customerId: Long): Flow<CustomerEntity?>
    fun searchCustomers(query: String, status: ContactStatus?): Flow<List<CustomerEntity>>
    suspend fun findCustomersByPhone(phone: String): List<CustomerEntity>
    suspend fun saveCustomer(customer: CustomerEntity): Long
    suspend fun updateCustomer(customer: CustomerEntity)
    suspend fun deleteCustomer(customerId: Long)
}

class RoomCustomerRepository(
    private val customerDao: CustomerDao,
) : CustomerRepository {
    override fun observeCustomers(): Flow<List<CustomerEntity>> = customerDao.observeAll()

    override fun observeCustomer(customerId: Long): Flow<CustomerEntity?> =
        customerDao.observeById(customerId)

    override fun searchCustomers(
        query: String,
        status: ContactStatus?,
    ): Flow<List<CustomerEntity>> = customerDao.search(query.trim(), status?.name)

    override suspend fun findCustomersByPhone(phone: String): List<CustomerEntity> =
        customerDao.findByPhone(phone.trim())

    override suspend fun saveCustomer(customer: CustomerEntity): Long =
        customerDao.insert(customer)

    override suspend fun updateCustomer(customer: CustomerEntity) =
        customerDao.update(customer)

    override suspend fun deleteCustomer(customerId: Long) =
        customerDao.deleteById(customerId)
}

interface TaskRepository {
    fun observeTasks(): Flow<List<TaskSummary>>
    fun observeTask(taskId: Long): Flow<CustomerListEntity?>
    fun observeTaskCustomers(taskId: Long): Flow<List<TaskCustomerRow>>
    fun observeListsForCustomer(customerId: Long): Flow<List<CustomerListEntity>>
    fun observeProgress(taskId: Long): Flow<TaskProgressEntity?>
    fun observeContactRecords(customerId: Long): Flow<List<CallRecordEntity>>
    suspend fun createTask(task: CustomerListEntity): Long
    suspend fun updateTask(task: CustomerListEntity)
    suspend fun deleteTask(taskId: Long)
    suspend fun addCustomerToTask(taskId: Long, customerId: Long): Long
    suspend fun removeCustomerFromTask(taskId: Long, customerId: Long)
    suspend fun removeCustomerFromAllTasks(customerId: Long)
    suspend fun saveProgress(progress: TaskProgressEntity)
    suspend fun loadProgress(taskId: Long): TaskProgressEntity?
    suspend fun recordContact(
        customerId: Long,
        listId: Long?,
        result: ContactStatus,
        note: String?,
    )
}

class RoomTaskRepository(
    private val taskDao: TaskDao,
) : TaskRepository {
    override fun observeTasks(): Flow<List<TaskSummary>> = taskDao.observeTaskSummaries()

    override fun observeTask(taskId: Long): Flow<CustomerListEntity?> =
        taskDao.observeList(taskId)

    override fun observeTaskCustomers(taskId: Long): Flow<List<TaskCustomerRow>> =
        taskDao.observeCustomersForList(taskId)

    override fun observeListsForCustomer(customerId: Long): Flow<List<CustomerListEntity>> =
        taskDao.observeListsForCustomer(customerId)

    override fun observeProgress(taskId: Long): Flow<TaskProgressEntity?> =
        taskDao.observeProgress(taskId)

    override fun observeContactRecords(customerId: Long): Flow<List<CallRecordEntity>> =
        taskDao.observeCallRecords(customerId)

    override suspend fun createTask(task: CustomerListEntity): Long =
        taskDao.insertList(task)

    override suspend fun updateTask(task: CustomerListEntity) = taskDao.updateList(task)

    override suspend fun deleteTask(taskId: Long) = taskDao.deleteList(taskId)

    override suspend fun addCustomerToTask(taskId: Long, customerId: Long): Long {
        taskDao.getMembership(taskId, customerId)?.let { return it.id }
        val membershipId = taskDao.insertMembership(
            ListMembershipEntity(
                listId = taskId,
                customerId = customerId,
                sortOrder = taskDao.nextSortOrder(taskId),
            ),
        )
        taskDao.touchList(taskId, System.currentTimeMillis())
        return membershipId
    }

    override suspend fun removeCustomerFromTask(taskId: Long, customerId: Long) {
        val membership = taskDao.getMembership(taskId, customerId) ?: return
        taskDao.deleteMembership(taskId, customerId)
        val progress = taskDao.getProgress(taskId)
        if (progress?.currentMembershipId == membership.id) {
            val nextMembershipId = taskDao.getMemberships(taskId).firstOrNull()?.id
            taskDao.saveProgress(
                TaskProgressEntity(
                    listId = taskId,
                    currentMembershipId = nextMembershipId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        taskDao.touchList(taskId, System.currentTimeMillis())
    }

    override suspend fun removeCustomerFromAllTasks(customerId: Long) {
        taskDao.getMembershipsForCustomer(customerId).forEach { membership ->
            removeCustomerFromTask(membership.listId, customerId)
        }
    }

    override suspend fun saveProgress(progress: TaskProgressEntity) =
        taskDao.saveProgress(progress)

    override suspend fun loadProgress(taskId: Long): TaskProgressEntity? =
        taskDao.getProgress(taskId)

    override suspend fun recordContact(
        customerId: Long,
        listId: Long?,
        result: ContactStatus,
        note: String?,
    ) {
        taskDao.recordContact(
            CallRecordEntity(
                customerId = customerId,
                listId = listId,
                result = result,
                note = note?.trim()?.takeIf(String::isNotEmpty),
            ),
        )
    }
}
