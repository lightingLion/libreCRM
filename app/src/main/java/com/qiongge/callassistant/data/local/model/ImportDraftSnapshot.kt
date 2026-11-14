package com.qiongge.callassistant.data.local.model

import com.qiongge.callassistant.data.local.entity.ImportDraftEntity
import com.qiongge.callassistant.data.local.entity.ImportDraftImageEntity
import com.qiongge.callassistant.data.local.entity.ImportDraftRowEntity

data class ImportDraftSnapshot(
    val draft: ImportDraftEntity,
    val images: List<ImportDraftImageEntity>,
    val rows: List<ImportDraftRowEntity>,
)
