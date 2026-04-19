package com.nutomic.syncthingandroid.webdav.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVTransferCheckpointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebDAVTransferCheckpointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkpoint: WebDAVTransferCheckpointEntity): Long

    @Query("SELECT * FROM webdav_transfer_checkpoint WHERE folder_id = :folderId ORDER BY updated_at DESC")
    suspend fun getForFolder(folderId: String): List<WebDAVTransferCheckpointEntity>

    @Query("SELECT COUNT(*) FROM webdav_transfer_checkpoint WHERE folder_id = :folderId")
    fun observeCountForFolder(folderId: String): Flow<Int>

    @Query(
        """
        UPDATE webdav_transfer_checkpoint
        SET state = :newState, error_summary = :errorSummary, updated_at = :updatedAt
        WHERE folder_id = :folderId AND state = :fromState
        """
    )
    suspend fun markState(folderId: String, fromState: String, newState: String, errorSummary: String?, updatedAt: Long): Int

    @Query("DELETE FROM webdav_transfer_checkpoint WHERE folder_id = :folderId AND relative_path = :relativePath")
    suspend fun delete(folderId: String, relativePath: String): Int

    @Query("DELETE FROM webdav_transfer_checkpoint WHERE folder_id = :folderId")
    suspend fun deleteForFolder(folderId: String): Int
}
