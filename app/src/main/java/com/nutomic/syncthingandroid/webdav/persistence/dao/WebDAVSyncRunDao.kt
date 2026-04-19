package com.nutomic.syncthingandroid.webdav.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVSyncRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebDAVSyncRunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(run: WebDAVSyncRunEntity): Long

    @Query("SELECT * FROM webdav_sync_run WHERE run_id = :runId LIMIT 1")
    suspend fun getById(runId: String): WebDAVSyncRunEntity?

    @Query("SELECT * FROM webdav_sync_run WHERE folder_id = :folderId ORDER BY started_at DESC")
    fun observeRunsForFolder(folderId: String): Flow<List<WebDAVSyncRunEntity>>

    @Query("SELECT * FROM webdav_sync_run WHERE folder_id = :folderId ORDER BY started_at DESC LIMIT 1")
    fun observeLatestRunForFolder(folderId: String): Flow<WebDAVSyncRunEntity?>

    @Query("UPDATE webdav_sync_run SET state = :state, ended_at = :endedAt, error_summary = :errorSummary WHERE run_id = :runId")
    suspend fun updateState(runId: String, state: String, endedAt: Long?, errorSummary: String?): Int
}
