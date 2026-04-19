package com.nutomic.syncthingandroid.webdav.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVFolderConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebDAVFolderConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(config: WebDAVFolderConfigEntity): Long

    @Query("SELECT * FROM webdav_folder_config WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WebDAVFolderConfigEntity?

    @Query("SELECT * FROM webdav_folder_config ORDER BY local_path ASC")
    fun observeAll(): Flow<List<WebDAVFolderConfigEntity>>

    @Query("SELECT * FROM webdav_folder_config WHERE enabled = 1 ORDER BY local_path ASC")
    fun observeEnabledFolders(): Flow<List<WebDAVFolderConfigEntity>>

    @Query("SELECT * FROM webdav_folder_config WHERE enabled = 1 ORDER BY local_path ASC")
    suspend fun getEnabledFolders(): List<WebDAVFolderConfigEntity>

    @Query("DELETE FROM webdav_folder_config WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
