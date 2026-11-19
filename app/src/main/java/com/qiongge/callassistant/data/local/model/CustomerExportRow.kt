package com.qiongge.callassistant.data.local.model

data class CustomerExportRow(
    val name: String,
    val phone: String,
    val status: String,
    val customResult: String?,
    val building: String?,
    val room: String?,
    val note: String?,
    val taskNames: String?,
    val updatedAt: Long,
)
