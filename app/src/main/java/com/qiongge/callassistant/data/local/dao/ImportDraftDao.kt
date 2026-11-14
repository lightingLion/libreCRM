package com.qiongge.callassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qiongge.callassistant.data.local.entity.ImportDraftEntity
import com.qiongge.callassistant.data.local.entity.ImportDraftImageEntity
import com.qiongge.callassistant.data.local.entity.ImportDraftRowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportDraftDao {
    @Query("SELECT * FROM import_drafts WHERE id = 1 LIMIT 1")
    fun observeDraft(): Flow<ImportDraftEntity?>

    @Query("SELECT * FROM import_drafts WHERE id = 1 LIMIT 1")
    suspend fun getDraft(): ImportDraftEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDraft(draft: ImportDraftEntity)

    @Update
    suspend fun updateDraft(draft: ImportDraftEntity)

    @Query("SELECT * FROM import_draft_images WHERE draftId = 1 ORDER BY sortOrder, id")
    fun observeImages(): Flow<List<ImportDraftImageEntity>>

    @Query("SELECT * FROM import_draft_images WHERE draftId = 1 ORDER BY sortOrder, id")
    suspend fun getImages(): List<ImportDraftImageEntity>

    @Query("SELECT * FROM import_draft_images WHERE id = :imageId AND draftId = 1 LIMIT 1")
    suspend fun getImage(imageId: Long): ImportDraftImageEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImage(image: ImportDraftImageEntity): Long

    @Query("SELECT * FROM import_draft_rows WHERE draftId = 1 ORDER BY sortOrder, id")
    fun observeRows(): Flow<List<ImportDraftRowEntity>>

    @Query("SELECT * FROM import_draft_rows WHERE draftId = 1 ORDER BY sortOrder, id")
    suspend fun getRows(): List<ImportDraftRowEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM import_draft_rows WHERE draftId = 1")
    suspend fun nextRowOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRows(rows: List<ImportDraftRowEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRow(row: ImportDraftRowEntity): Long

    @Update
    suspend fun updateRow(row: ImportDraftRowEntity)

    @Query("DELETE FROM import_draft_rows WHERE id = :rowId AND draftId = 1")
    suspend fun deleteRow(rowId: Long)

    @Query("DELETE FROM import_draft_rows WHERE sourceImageId = :imageId AND draftId = 1")
    suspend fun deleteRowsForImage(imageId: Long)

    @Query("DELETE FROM import_drafts WHERE id = 1")
    suspend fun deleteDraft()
}
