package com.nutomic.syncthingandroid.webdav.persistence.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "webdav_transfer_checkpoint",
    primaryKeys = ["folder_id", "relative_path"],
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
        Index(value = ["updated_at"]),
    ],
)
data class WebDAVTransferCheckpointEntity(
    @ColumnInfo(name = "folder_id")
    val folderId: String,
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    @ColumnInfo(name = "action_type")
    val actionType: String,
    @ColumnInfo(name = "local_path")
    val localPath: String?,
    @ColumnInfo(name = "remote_path")
    val remotePath: String?,
    @ColumnInfo(name = "temp_file_path")
    val tempFilePath: String?,
    @ColumnInfo(name = "transferred_bytes")
    val transferredBytes: Long?,
    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long?,
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "retryable")
    val retryable: Boolean,
    @ColumnInfo(name = "error_summary")
    val errorSummary: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
