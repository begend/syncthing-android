package com.nutomic.syncthingandroid.webdav.persistence.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "webdav_sync_run",
    foreignKeys = [
        ForeignKey(
            entity = WebDAVFolderConfigEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["folder_id"]),
        Index(value = ["state"]),
    ],
)
data class WebDAVSyncRunEntity(
    @PrimaryKey
    @ColumnInfo(name = "run_id")
    val runId: String,
    @ColumnInfo(name = "folder_id")
    val folderId: String,
    @ColumnInfo(name = "trigger_reason")
    val triggerReason: String,
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "ended_at")
    val endedAt: Long?,
    @ColumnInfo(name = "success_count")
    val successCount: Int,
    @ColumnInfo(name = "failure_count")
    val failureCount: Int,
    @ColumnInfo(name = "conflict_count")
    val conflictCount: Int,
    @ColumnInfo(name = "bytes_transferred")
    val bytesTransferred: Long,
    @ColumnInfo(name = "error_summary")
    val errorSummary: String?,
)
