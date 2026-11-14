package com.qiongge.callassistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "import_drafts")
data class ImportDraftEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(
    tableName = "import_draft_images",
    foreignKeys = [
        ForeignKey(
            entity = ImportDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["draftId"])],
)
data class ImportDraftImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val draftId: Int = ImportDraftEntity.SINGLETON_ID,
    val displayName: String,
    val localPath: String,
    val sortOrder: Int,
    val width: Int,
    val height: Int,
)

@Entity(
    tableName = "import_draft_rows",
    foreignKeys = [
        ForeignKey(
            entity = ImportDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ImportDraftImageEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceImageId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["draftId"]), Index(value = ["sourceImageId"])],
)
data class ImportDraftRowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val draftId: Int = ImportDraftEntity.SINGLETON_ID,
    val sourceImageId: Long?,
    val sortOrder: Int,
    val name: String,
    val phone: String,
    val sourceText: String,
    val sourceCells: String? = null,
    val sourceFileName: String? = null,
    val sourceLeft: Float?,
    val sourceTop: Float?,
    val sourceRight: Float?,
    val sourceBottom: Float?,
    val confidence: Float?,
    val reviewed: Boolean,
    val skipped: Boolean,
    val duplicateMode: String?,
    val duplicateCustomerId: Long?,
    val skipReason: String? = null,
)

object ImportSkipReason {
    const val INCOMPLETE = "INCOMPLETE"
    const val MANUAL = "MANUAL"
    const val DUPLICATE_PHONE = "DUPLICATE_PHONE"
}
