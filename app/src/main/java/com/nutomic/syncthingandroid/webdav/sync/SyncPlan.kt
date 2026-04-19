package com.nutomic.syncthingandroid.webdav.sync

import com.nutomic.syncthingandroid.webdav.model.ConflictType
import com.nutomic.syncthingandroid.webdav.model.WebDAVFile

data class LocalSnapshot(
    val relativePath: String,
    val absolutePath: String,
    val size: Long,
    val lastModified: Long,
)

sealed class PlannedSyncAction {
    abstract val relativePath: String

    data class Upload(
        override val relativePath: String,
        val localPath: String,
        val remotePath: String,
    ) : PlannedSyncAction()

    data class Download(
        override val relativePath: String,
        val remoteFile: WebDAVFile,
        val localPath: String,
    ) : PlannedSyncAction()

    data class DeleteRemote(
        override val relativePath: String,
        val remotePath: String,
    ) : PlannedSyncAction()

    data class DeleteLocal(
        override val relativePath: String,
        val localPath: String,
    ) : PlannedSyncAction()

    data class Conflict(
        override val relativePath: String,
        val localPath: String?,
        val remotePath: String?,
        val conflictType: ConflictType,
    ) : PlannedSyncAction()
}

data class SyncPlan(
    val folderId: String,
    val localFileCount: Int,
    val remoteFileCount: Int,
    val knownEntryCount: Int,
    val actions: List<PlannedSyncAction>,
) {
    val uploadCount: Int
        get() = actions.count { it is PlannedSyncAction.Upload }

    val downloadCount: Int
        get() = actions.count { it is PlannedSyncAction.Download }

    val deleteRemoteCount: Int
        get() = actions.count { it is PlannedSyncAction.DeleteRemote }

    val deleteLocalCount: Int
        get() = actions.count { it is PlannedSyncAction.DeleteLocal }

    val conflictCount: Int
        get() = actions.count { it is PlannedSyncAction.Conflict }

    fun summary(): String {
        return "uploads=$uploadCount, downloads=$downloadCount, deleteRemote=$deleteRemoteCount, deleteLocal=$deleteLocalCount, conflicts=$conflictCount"
    }
}
