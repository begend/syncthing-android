package com.nutomic.syncthingandroid.webdav.persistence.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "webdav_server_config")
data class WebDAVServerConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "base_url")
    val baseUrl: String,
    @ColumnInfo(name = "username")
    val username: String,
    @ColumnInfo(name = "password_alias")
    val passwordAlias: String,
    @ColumnInfo(name = "auth_type")
    val authType: String,
    @ColumnInfo(name = "connect_timeout_ms")
    val connectTimeoutMs: Int,
    @ColumnInfo(name = "read_timeout_ms")
    val readTimeoutMs: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
