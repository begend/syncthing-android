package com.nutomic.syncthingandroid.webdav.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVFolderConfigDao
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVServerConfigDao
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVTransferCheckpointDao
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVSyncEntryDao
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVSyncRunDao
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVFolderConfigEntity
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVServerConfigEntity
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVTransferCheckpointEntity
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVSyncEntryEntity
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVSyncRunEntity

@Database(
    entities = [
        WebDAVServerConfigEntity::class,
        WebDAVFolderConfigEntity::class,
        WebDAVTransferCheckpointEntity::class,
        WebDAVSyncEntryEntity::class,
        WebDAVSyncRunEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class WebDAVDatabase : RoomDatabase() {
    abstract fun serverConfigDao(): WebDAVServerConfigDao
    abstract fun folderConfigDao(): WebDAVFolderConfigDao
    abstract fun transferCheckpointDao(): WebDAVTransferCheckpointDao
    abstract fun syncEntryDao(): WebDAVSyncEntryDao
    abstract fun syncRunDao(): WebDAVSyncRunDao

    companion object {
        const val DATABASE_NAME = "webdav_sync.db"
    }
}
