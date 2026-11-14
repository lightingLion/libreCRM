package com.qiongge.callassistant.data.local.model

data class CustomerExportRow(
    val name: String,
    val phone: String,
    val status: String,
    val note: String?,
    val taskNames: String?,
    val updatedAt: Long,
)
