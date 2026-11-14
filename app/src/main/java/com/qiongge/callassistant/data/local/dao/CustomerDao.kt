package com.qiongge.callassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qiongge.callassistant.data.local.entity.CustomerEntity
import com.qiongge.callassistant.data.local.entity.ContactStatus
import com.qiongge.callassistant.data.local.model.CustomerExportRow
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :customerId")
    fun observeById(customerId: Long): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
    suspend fun findById(customerId: Long): CustomerEntity?

    @Query("SELECT * FROM customers ORDER BY id")
    suspend fun getAllOnce(): List<CustomerEntity>

    @Query(
        "SELECT customers.name AS name, customers.phone AS phone, customers.status AS status, " +
            "(SELECT NULLIF(TRIM(call_records.note), '') FROM call_records " +
            "WHERE call_records.customerId = customers.id AND call_records.note IS NOT NULL " +
            "ORDER BY call_records.createdAt DESC, call_records.id DESC LIMIT 1) AS note, " +
            "(SELECT GROUP_CONCAT(customer_lists.title, '、') FROM list_memberships " +
            "INNER JOIN customer_lists ON customer_lists.id = list_memberships.listId " +
            "WHERE list_memberships.customerId = customers.id) AS taskNames, " +
            "customers.updatedAt AS updatedAt FROM customers " +
            "ORDER BY customers.updatedAt DESC, customers.id DESC",
    )
    suspend fun getExportRows(): List<CustomerExportRow>

    @Query(
        "SELECT * FROM customers " +
            "WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' " +
            "ORDER BY updatedAt DESC",
    )
    fun search(query: String): Flow<List<CustomerEntity>>

    @Query(
        "SELECT * FROM customers " +
            "WHERE (:query = '' OR name LIKE '%' || :query || '%' " +
            "OR phone LIKE '%' || :query || '%') " +
            "AND (:status IS NULL OR status = :status) " +
            "ORDER BY updatedAt DESC",
    )
    fun search(query: String, status: String?): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE phone = :phone ORDER BY updatedAt DESC")
    suspend fun findByPhone(phone: String): List<CustomerEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(customer: CustomerEntity): Long

    @Update
    suspend fun update(customer: CustomerEntity)

    @Delete
    suspend fun delete(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :customerId")
    suspend fun deleteById(customerId: Long)
}
