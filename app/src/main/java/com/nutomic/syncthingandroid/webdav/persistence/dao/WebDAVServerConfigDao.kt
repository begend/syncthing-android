package com.nutomic.syncthingandroid.webdav.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVServerConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebDAVServerConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(config: WebDAVServerConfigEntity): Long

    @Query("SELECT * FROM webdav_server_config WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WebDAVServerConfigEntity?

    @Query("SELECT * FROM webdav_server_config ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<WebDAVServerConfigEntity>>

    @Query("SELECT COUNT(*) FROM webdav_server_config")
    fun getCount(): Int

    @Query("DELETE FROM webdav_server_config WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
