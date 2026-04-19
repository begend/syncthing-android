package com.nutomic.syncthingandroid.webdav.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVSyncEntryEntity

@Dao
interface WebDAVSyncEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entries: List<WebDAVSyncEntryEntity>): List<Long>

    @Query("SELECT * FROM webdav_sync_entry WHERE folder_id = :folderId ORDER BY relative_path ASC")
    suspend fun getEntriesForFolder(folderId: String): List<WebDAVSyncEntryEntity>

    @Query("DELETE FROM webdav_sync_entry WHERE folder_id = :folderId")
    suspend fun deleteByFolderId(folderId: String): Int

    @Query(
        """
        DELETE FROM webdav_sync_entry
        WHERE folder_id = :folderId
        AND relative_path IN (:relativePaths)
        """
    )
    suspend fun deleteByRelativePaths(folderId: String, relativePaths: List<String>): Int

    @Query(
        """
        DELETE FROM webdav_sync_entry
        WHERE folder_id = :folderId
        AND relative_path NOT IN (:keepPaths)
        """
    )
    suspend fun deleteMissingEntries(folderId: String, keepPaths: List<String>): Int

    @Transaction
    suspend fun replaceFolderEntries(folderId: String, entries: List<WebDAVSyncEntryEntity>) {
        deleteByFolderId(folderId)
        if (entries.isNotEmpty()) {
            upsert(entries)
        }
    }
}
