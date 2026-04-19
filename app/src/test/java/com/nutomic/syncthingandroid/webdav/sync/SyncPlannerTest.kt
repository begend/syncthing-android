package com.nutomic.syncthingandroid.webdav.sync

import com.nutomic.syncthingandroid.webdav.model.ConflictType
import com.nutomic.syncthingandroid.webdav.model.WebDAVFile
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVFolderConfigEntity
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVSyncEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncPlannerTest {
    private lateinit var planner: SyncPlanner
    private lateinit var folderConfig: WebDAVFolderConfigEntity

    @Before
    fun setUp() {
        planner = SyncPlanner()
        folderConfig = WebDAVFolderConfigEntity(
            id = "folder-1",
            serverId = "server-1",
            localPath = "/tmp/books",
            remotePath = "/remote/books",
            syncMode = "BIDIRECTIONAL",
            conflictStrategy = "KEEP_BOTH",
            enabled = true,
            wifiOnly = false,
            chargingOnly = false,
            batteryNotLow = false,
            maxParallelTransfers = 2,
            largeFileThresholdBytes = 256L * 1024L * 1024L,
            profileId = null,
            lastSyncAttemptAt = null,
            lastSyncSuccessAt = null,
        )
    }

    @Test
    fun decideAction_uploadWhenOnlyLocalExistsWithoutSnapshot() {
        val action = planner.decideAction(
            folderConfig = folderConfig,
            relativePath = "a.txt",
            snapshot = null,
            local = localFile("a.txt", size = 10, lastModified = 1000),
            remote = null,
        )

        assertTrue(action is PlannedSyncAction.Upload)
        assertEquals("/remote/books/a.txt", (action as PlannedSyncAction.Upload).remotePath)
    }

    @Test
    fun decideAction_downloadWhenOnlyRemoteExistsWithoutSnapshot() {
        val action = planner.decideAction(
            folderConfig = folderConfig,
            relativePath = "b.txt",
            snapshot = null,
            local = null,
            remote = remoteFile("b.txt", size = 20, lastModified = 2000),
        )

        assertTrue(action is PlannedSyncAction.Download)
        assertEquals("/tmp/books/b.txt", (action as PlannedSyncAction.Download).localPath)
    }

    @Test
    fun decideAction_deleteRemoteWhenLocallyDeleted() {
        val action = planner.decideAction(
            folderConfig = folderConfig,
            relativePath = "c.txt",
            snapshot = snapshot("c.txt", localSize = 30, localMtime = 3000, remoteSize = 30, remoteMtime = 3000),
            local = null,
            remote = remoteFile("c.txt", size = 30, lastModified = 3000),
        )

        assertTrue(action is PlannedSyncAction.DeleteRemote)
    }

    @Test
    fun decideAction_deleteLocalWhenRemotelyDeleted() {
        val action = planner.decideAction(
            folderConfig = folderConfig,
            relativePath = "d.txt",
            snapshot = snapshot("d.txt", localSize = 40, localMtime = 4000, remoteSize = 40, remoteMtime = 4000),
            local = localFile("d.txt", size = 40, lastModified = 4000),
            remote = null,
        )

        assertTrue(action is PlannedSyncAction.DeleteLocal)
    }

    @Test
    fun decideAction_conflictWhenBothSidesChanged() {
        val action = planner.decideAction(
            folderConfig = folderConfig,
            relativePath = "e.txt",
            snapshot = snapshot("e.txt", localSize = 50, localMtime = 5000, remoteSize = 50, remoteMtime = 5000),
            local = localFile("e.txt", size = 60, lastModified = 6000),
            remote = remoteFile("e.txt", size = 55, lastModified = 5500),
        )

        assertTrue(action is PlannedSyncAction.Conflict)
        assertEquals(ConflictType.BOTH_MODIFIED, (action as PlannedSyncAction.Conflict).conflictType)
    }

    private fun localFile(relativePath: String, size: Long, lastModified: Long): LocalSnapshot {
        return LocalSnapshot(
            relativePath = relativePath,
            absolutePath = "/tmp/books/$relativePath",
            size = size,
            lastModified = lastModified,
        )
    }

    private fun remoteFile(relativePath: String, size: Long, lastModified: Long): WebDAVFile {
        return WebDAVFile(
            name = relativePath.substringAfterLast('/'),
            path = "/remote/books/$relativePath",
            isDirectory = false,
            size = size,
            lastModified = lastModified,
            etag = "etag-$relativePath-$size-$lastModified",
        )
    }

    private fun snapshot(
        relativePath: String,
        localSize: Long,
        localMtime: Long,
        remoteSize: Long,
        remoteMtime: Long,
    ): WebDAVSyncEntryEntity {
        return WebDAVSyncEntryEntity(
            folderId = "folder-1",
            relativePath = relativePath,
            localSize = localSize,
            localMtime = localMtime,
            localFingerprint = null,
            remoteEtag = null,
            remoteSize = remoteSize,
            remoteMtime = remoteMtime,
            lastSyncTime = localMtime,
            existsLocal = true,
            existsRemote = true,
            deletedLocal = false,
            deletedRemote = false,
            updatedAt = localMtime,
        )
    }
}
