package com.qiongge.callassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.qiongge.callassistant.data.local.entity.CallRecordEntity
import com.qiongge.callassistant.data.local.entity.CustomerListEntity
import com.qiongge.callassistant.data.local.entity.FollowUpEntity
import com.qiongge.callassistant.data.local.entity.ImportRecordEntity
import com.qiongge.callassistant.data.local.entity.ListMembershipEntity
import com.qiongge.callassistant.data.local.entity.TaskProgressEntity
import com.qiongge.callassistant.data.local.model.TaskCustomerRow
import com.qiongge.callassistant.data.local.model.TaskSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        "SELECT customer_lists.*, " +
            "COUNT(list_memberships.id) AS customerCount, " +
            "COALESCE(SUM(CASE WHEN customers.status IS NOT NULL " +
            "AND customers.status != 'NOT_CONTACTED' THEN 1 ELSE 0 END), 0) " +
            "AS contactedCount, task_progress.currentMembershipId AS currentMembershipId, " +
            "current_membership.customerId AS currentCustomerId " +
            "FROM customer_lists " +
            "LEFT JOIN list_memberships ON list_memberships.listId = customer_lists.id " +
            "LEFT JOIN customers ON customers.id = list_memberships.customerId " +
            "LEFT JOIN task_progress ON task_progress.listId = customer_lists.id " +
            "LEFT JOIN list_memberships AS current_membership " +
            "ON current_membership.id = task_progress.currentMembershipId " +
            "GROUP BY customer_lists.id " +
            "ORDER BY customer_lists.updatedAt DESC, customer_lists.id DESC",
    )
    fun observeTaskSummaries(): Flow<List<TaskSummary>>

    @Query("SELECT * FROM customer_lists WHERE id = :listId")
    fun observeList(listId: Long): Flow<CustomerListEntity?>

    @Query(
        "SELECT list_memberships.id AS membershipId, " +
            "list_memberships.sortOrder AS sortOrder, " +
            "customers.id AS customer_id, customers.name AS customer_name, " +
            "customers.phone AS customer_phone, customers.status AS customer_status, " +
            "customers.createdAt AS customer_createdAt, " +
            "customers.updatedAt AS customer_updatedAt " +
            "FROM list_memberships " +
            "INNER JOIN customers ON customers.id = list_memberships.customerId " +
            "WHERE list_memberships.listId = :listId " +
            "ORDER BY list_memberships.sortOrder, list_memberships.id",
    )
    fun observeCustomersForList(listId: Long): Flow<List<TaskCustomerRow>>

    @Query(
        "SELECT customer_lists.* FROM customer_lists " +
            "INNER JOIN list_memberships ON list_memberships.listId = customer_lists.id " +
            "WHERE list_memberships.customerId = :customerId " +
            "ORDER BY customer_lists.updatedAt DESC",
    )
    fun observeListsForCustomer(customerId: Long): Flow<List<CustomerListEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertList(list: CustomerListEntity): Long

    @Update
    suspend fun updateList(list: CustomerListEntity)

    @Query("DELETE FROM customer_lists WHERE id = :listId")
    suspend fun deleteList(listId: Long)

    @Query("UPDATE customer_lists SET updatedAt = :updatedAt WHERE id = :listId")
    suspend fun touchList(listId: Long, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMembership(membership: ListMembershipEntity): Long

    @Query("SELECT * FROM list_memberships WHERE listId = :listId ORDER BY sortOrder, id")
    suspend fun getMemberships(listId: Long): List<ListMembershipEntity>

    @Query("SELECT * FROM list_memberships WHERE listId = :listId AND customerId = :customerId")
    suspend fun getMembership(listId: Long, customerId: Long): ListMembershipEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM list_memberships WHERE listId = :listId")
    suspend fun nextSortOrder(listId: Long): Int

    @Query("DELETE FROM list_memberships WHERE listId = :listId AND customerId = :customerId")
    suspend fun deleteMembership(listId: Long, customerId: Long)

    @Query("SELECT * FROM list_memberships WHERE customerId = :customerId ORDER BY listId")
    suspend fun getMembershipsForCustomer(customerId: Long): List<ListMembershipEntity>

    @Query(
        "SELECT * FROM list_memberships WHERE listId = :listId " +
            "ORDER BY sortOrder, id",
    )
    fun observeMemberships(listId: Long): Flow<List<ListMembershipEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCallRecord(record: CallRecordEntity): Long

    @Query(
        "SELECT * FROM call_records WHERE customerId = :customerId " +
            "ORDER BY createdAt DESC, id DESC",
    )
    fun observeCallRecords(customerId: Long): Flow<List<CallRecordEntity>>

    @Query("UPDATE customers SET status = :status, updatedAt = :updatedAt WHERE id = :customerId")
    suspend fun updateCustomerContactStatus(
        customerId: Long,
        status: String,
        updatedAt: Long,
    )

    @Transaction
    suspend fun recordContact(record: CallRecordEntity) {
        updateCustomerContactStatus(
            customerId = record.customerId,
            status = record.result.name,
            updatedAt = record.createdAt,
        )
        insertCallRecord(record)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFollowUp(followUp: FollowUpEntity): Long

    @Query("SELECT * FROM follow_ups WHERE customerId = :customerId ORDER BY id DESC")
    fun observeFollowUps(customerId: Long): Flow<List<FollowUpEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImportRecord(record: ImportRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: TaskProgressEntity)

    @Query("SELECT * FROM task_progress WHERE listId = :listId")
    suspend fun getProgress(listId: Long): TaskProgressEntity?

    @Query("SELECT * FROM task_progress WHERE listId = :listId")
    fun observeProgress(listId: Long): Flow<TaskProgressEntity?>
}
