package com.qiongge.callassistant.data.local.model

import androidx.room.Embedded
import com.qiongge.callassistant.data.local.entity.CallRecordEntity
import com.qiongge.callassistant.data.local.entity.CustomerEntity
import com.qiongge.callassistant.data.local.entity.CustomerListEntity

data class TaskSummary(
    @Embedded val task: CustomerListEntity,
    val customerCount: Int,
    val contactedCount: Int,
    val currentMembershipId: Long?,
    val currentCustomerId: Long?,
)

data class TaskCustomerRow(
    val membershipId: Long,
    val sortOrder: Int,
    @Embedded(prefix = "customer_") val customer: CustomerEntity,
    val building: String?,
    val room: String?,
)

data class TaskContactRecord(
    @Embedded val record: CallRecordEntity,
    val customerName: String,
    val phone: String,
)
