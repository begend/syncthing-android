package com.nutomic.syncthingandroid.webdav.persistence.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "webdav_sync_entry",
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
        Index(value = ["updated_at"]),
    ],
)
data class WebDAVSyncEntryEntity(
    @ColumnInfo(name = "folder_id")
    val folderId: String,
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    @ColumnInfo(name = "local_size")
    val localSize: Long?,
    @ColumnInfo(name = "local_mtime")
    val localMtime: Long?,
    @ColumnInfo(name = "local_fingerprint")
    val localFingerprint: String?,
    @ColumnInfo(name = "remote_etag")
    val remoteEtag: String?,
    @ColumnInfo(name = "remote_size")
    val remoteSize: Long?,
    @ColumnInfo(name = "remote_mtime")
    val remoteMtime: Long?,
    @ColumnInfo(name = "last_sync_time")
    val lastSyncTime: Long,
    @ColumnInfo(name = "exists_local")
    val existsLocal: Boolean,
    @ColumnInfo(name = "exists_remote")
    val existsRemote: Boolean,
    @ColumnInfo(name = "deleted_local")
    val deletedLocal: Boolean,
    @ColumnInfo(name = "deleted_remote")
    val deletedRemote: Boolean,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
