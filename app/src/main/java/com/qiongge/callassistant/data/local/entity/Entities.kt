package com.qiongge.callassistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [Index(value = ["phone"])],
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val status: ContactStatus = ContactStatus.NOT_CONTACTED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val customResult: String? = null,
)

@Entity(tableName = "customer_lists")
data class CustomerListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "list_memberships",
    foreignKeys = [
        ForeignKey(
            entity = CustomerListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["listId", "customerId"], unique = true),
        Index(value = ["customerId"]),
    ],
)
data class ListMembershipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val customerId: Long,
    val sortOrder: Int,
    val building: String? = null,
    val room: String? = null,
)

@Entity(
    tableName = "call_records",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CustomerListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["customerId"]), Index(value = ["listId"])],
)
data class CallRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val listId: Long?,
    val result: ContactStatus,
    val note: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val customResult: String? = null,
)

@Entity(
    tableName = "follow_ups",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["customerId"])],
)
data class FollowUpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val content: String,
    val plannedAt: Long?,
    val completedAt: Long?,
)

@Entity(tableName = "import_records")
data class ImportRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String,
    val sourceName: String?,
    val recognizedCount: Int,
    val confirmedCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "task_progress",
    foreignKeys = [
        ForeignKey(
            entity = CustomerListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TaskProgressEntity(
    @PrimaryKey val listId: Long,
    val currentMembershipId: Long?,
    val updatedAt: Long = System.currentTimeMillis(),
)
