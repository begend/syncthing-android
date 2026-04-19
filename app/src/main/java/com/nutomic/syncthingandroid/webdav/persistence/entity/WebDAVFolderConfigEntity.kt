package com.nutomic.syncthingandroid.webdav.persistence.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "webdav_folder_config",
    foreignKeys = [
        ForeignKey(
            entity = WebDAVServerConfigEntity::class,
            parentColumns = ["id"],
            childColumns = ["server_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["server_id"]),
        Index(value = ["enabled"]),
    ],
)
data class WebDAVFolderConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "server_id")
    val serverId: String,
    @ColumnInfo(name = "local_path")
    val localPath: String,
    @ColumnInfo(name = "remote_path")
    val remotePath: String,
    @ColumnInfo(name = "sync_mode")
    val syncMode: String,
    @ColumnInfo(name = "conflict_strategy")
    val conflictStrategy: String,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean,
    @ColumnInfo(name = "wifi_only")
    val wifiOnly: Boolean,
    @ColumnInfo(name = "charging_only")
    val chargingOnly: Boolean,
    @ColumnInfo(name = "battery_not_low")
    val batteryNotLow: Boolean,
    @ColumnInfo(name = "max_parallel_transfers")
    val maxParallelTransfers: Int,
    @ColumnInfo(name = "large_file_threshold_bytes")
    val largeFileThresholdBytes: Long,
    @ColumnInfo(name = "profile_id")
    val profileId: String?,
    @ColumnInfo(name = "last_sync_attempt_at")
    val lastSyncAttemptAt: Long?,
    @ColumnInfo(name = "last_sync_success_at")
    val lastSyncSuccessAt: Long?,
)
