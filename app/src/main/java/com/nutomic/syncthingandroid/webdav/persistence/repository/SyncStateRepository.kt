package com.nutomic.syncthingandroid.webdav.persistence.repository

import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVSyncEntryDao
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVSyncRunDao
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVTransferCheckpointDao
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVSyncEntryEntity
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVSyncRunEntity
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVTransferCheckpointEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncStateRepository @Inject constructor(
    private val transferCheckpointDao: WebDAVTransferCheckpointDao,
    private val syncEntryDao: WebDAVSyncEntryDao,
    private val syncRunDao: WebDAVSyncRunDao,
) {
    suspend fun upsertCheckpoint(checkpoint: WebDAVTransferCheckpointEntity) {
        transferCheckpointDao.upsert(checkpoint)
    }

    suspend fun getCheckpointsForFolder(folderId: String): List<WebDAVTransferCheckpointEntity> {
        return transferCheckpointDao.getForFolder(folderId)
    }

    fun observeCheckpointCountForFolder(folderId: String): Flow<Int> {
        return transferCheckpointDao.observeCountForFolder(folderId)
    }

    suspend fun markInProgressCheckpointsInterrupted(folderId: String, errorSummary: String) {
        transferCheckpointDao.markState(
            folderId = folderId,
            fromState = "IN_PROGRESS",
            newState = "INTERRUPTED",
            errorSummary = errorSummary,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun deleteCheckpoint(folderId: String, relativePath: String) {
        transferCheckpointDao.delete(folderId, relativePath)
    }

    suspend fun deleteCheckpointsForFolder(folderId: String) {
        transferCheckpointDao.deleteForFolder(folderId)
    }

    suspend fun getEntriesForFolder(folderId: String): List<WebDAVSyncEntryEntity> {
        return syncEntryDao.getEntriesForFolder(folderId)
    }

    suspend fun upsertEntries(entries: List<WebDAVSyncEntryEntity>) {
        if (entries.isNotEmpty()) {
            syncEntryDao.upsert(entries)
        }
    }

    suspend fun replaceFolderEntries(folderId: String, entries: List<WebDAVSyncEntryEntity>) {
        syncEntryDao.replaceFolderEntries(folderId, entries)
    }

    suspend fun deleteMissingEntries(folderId: String, keepPaths: List<String>) {
        if (keepPaths.isEmpty()) {
            syncEntryDao.deleteByFolderId(folderId)
        } else {
            syncEntryDao.deleteMissingEntries(folderId, keepPaths)
        }
    }

    suspend fun deleteEntries(folderId: String, relativePaths: List<String>) {
        if (relativePaths.isNotEmpty()) {
            syncEntryDao.deleteByRelativePaths(folderId, relativePaths)
        }
    }

    suspend fun createOrUpdateRun(run: WebDAVSyncRunEntity) {
        syncRunDao.insertOrReplace(run)
    }

    suspend fun getRun(runId: String): WebDAVSyncRunEntity? = syncRunDao.getById(runId)

    fun observeRunsForFolder(folderId: String): Flow<List<WebDAVSyncRunEntity>> {
        return syncRunDao.observeRunsForFolder(folderId)
    }

    fun observeLatestRunForFolder(folderId: String): Flow<WebDAVSyncRunEntity?> {
        return syncRunDao.observeLatestRunForFolder(folderId)
    }

    suspend fun updateRunState(runId: String, state: String, endedAt: Long?, errorSummary: String?) {
        syncRunDao.updateState(runId, state, endedAt, errorSummary)
    }
}
