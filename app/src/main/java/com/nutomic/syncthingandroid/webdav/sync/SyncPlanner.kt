package com.nutomic.syncthingandroid.webdav.sync

import android.util.Log
import com.nutomic.syncthingandroid.webdav.WebDAVClient
import com.nutomic.syncthingandroid.webdav.model.ConflictType
import com.nutomic.syncthingandroid.webdav.model.WebDAVFile
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVFolderConfigEntity
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVSyncEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.abs

class SyncPlanner @Inject constructor() {
    companion object {
        private const val TAG = "SyncPlanner"
        private const val MTIME_TOLERANCE_MS = 2_000L
    }

    suspend fun planFolderSync(
        folderConfig: WebDAVFolderConfigEntity,
        previousEntries: List<WebDAVSyncEntryEntity>,
        webDAVClient: WebDAVClient,
    ): Result<SyncPlan> = withContext(Dispatchers.IO) {
        try {
            val localFiles = scanLocalFiles(folderConfig.localPath)
            val remoteFiles = scanRemoteFiles(folderConfig.remotePath, webDAVClient).getOrThrow()
            val snapshotMap = previousEntries.associateBy { it.relativePath }

            val allPaths = linkedSetOf<String>()
            allPaths.addAll(localFiles.keys)
            allPaths.addAll(remoteFiles.keys)
            allPaths.addAll(snapshotMap.keys)

            val actions = allPaths
                .sorted()
                .mapNotNull { relativePath ->
                    decideAction(
                        folderConfig = folderConfig,
                        relativePath = relativePath,
                        snapshot = snapshotMap[relativePath],
                        local = localFiles[relativePath],
                        remote = remoteFiles[relativePath],
                    )
                }

            Result.success(
                SyncPlan(
                    folderId = folderConfig.id,
                    localFileCount = localFiles.size,
                    remoteFileCount = remoteFiles.size,
                    knownEntryCount = previousEntries.size,
                    actions = actions,
                )
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to plan sync for folder ${folderConfig.id}", t)
            Result.failure(t)
        }
    }

    internal fun decideAction(
        folderConfig: WebDAVFolderConfigEntity,
        relativePath: String,
        snapshot: WebDAVSyncEntryEntity?,
        local: LocalSnapshot?,
        remote: WebDAVFile?,
    ): PlannedSyncAction? {
        if (relativePath.isBlank()) {
            return null
        }

        val remotePath = buildRemotePath(folderConfig.remotePath, relativePath)
        val localPath = buildLocalPath(folderConfig.localPath, relativePath)

        if (snapshot == null) {
            return when {
                local != null && remote == null -> PlannedSyncAction.Upload(
                    relativePath = relativePath,
                    localPath = local.absolutePath,
                    remotePath = remotePath,
                )

                local == null && remote != null -> PlannedSyncAction.Download(
                    relativePath = relativePath,
                    remoteFile = remote,
                    localPath = localPath,
                )

                local != null && remote != null && !isEquivalent(local, remote) -> PlannedSyncAction.Conflict(
                    relativePath = relativePath,
                    localPath = local.absolutePath,
                    remotePath = remote.path,
                    conflictType = ConflictType.BOTH_MODIFIED,
                )

                else -> null
            }
        }

        val localState = compareLocalState(snapshot, local)
        val remoteState = compareRemoteState(snapshot, remote)

        return when {
            localState == EntryState.SAME && remoteState == EntryState.SAME -> null

            localState == EntryState.CHANGED && remoteState == EntryState.SAME -> PlannedSyncAction.Upload(
                relativePath = relativePath,
                localPath = local!!.absolutePath,
                remotePath = remotePath,
            )

            localState == EntryState.SAME && remoteState == EntryState.CHANGED -> PlannedSyncAction.Download(
                relativePath = relativePath,
                remoteFile = remote!!,
                localPath = localPath,
            )

            localState == EntryState.MISSING && remoteState == EntryState.SAME -> PlannedSyncAction.DeleteRemote(
                relativePath = relativePath,
                remotePath = remotePath,
            )

            localState == EntryState.SAME && remoteState == EntryState.MISSING -> PlannedSyncAction.DeleteLocal(
                relativePath = relativePath,
                localPath = localPath,
            )

            localState == EntryState.CHANGED && remoteState == EntryState.CHANGED -> PlannedSyncAction.Conflict(
                relativePath = relativePath,
                localPath = local?.absolutePath,
                remotePath = remote?.path,
                conflictType = ConflictType.BOTH_MODIFIED,
            )

            localState == EntryState.MISSING && remoteState == EntryState.CHANGED -> PlannedSyncAction.Conflict(
                relativePath = relativePath,
                localPath = null,
                remotePath = remote?.path,
                conflictType = ConflictType.LOCAL_DELETED_REMOTE_MODIFIED,
            )

            localState == EntryState.CHANGED && remoteState == EntryState.MISSING -> PlannedSyncAction.Conflict(
                relativePath = relativePath,
                localPath = local?.absolutePath,
                remotePath = null,
                conflictType = ConflictType.LOCAL_MODIFIED_REMOTE_DELETED,
            )

            else -> null
        }
    }

    private fun compareLocalState(snapshot: WebDAVSyncEntryEntity, local: LocalSnapshot?): EntryState {
        if (local == null) {
            return EntryState.MISSING
        }

        val sameSize = snapshot.localSize == local.size
        val sameMtime = snapshot.localMtime != null &&
            abs(snapshot.localMtime - local.lastModified) <= MTIME_TOLERANCE_MS
        return if (sameSize && sameMtime) EntryState.SAME else EntryState.CHANGED
    }

    private fun compareRemoteState(snapshot: WebDAVSyncEntryEntity, remote: WebDAVFile?): EntryState {
        if (remote == null) {
            return EntryState.MISSING
        }

        val sameEtag = !snapshot.remoteEtag.isNullOrBlank() &&
            !remote.etag.isNullOrBlank() &&
            snapshot.remoteEtag == remote.etag
        if (sameEtag) {
            return EntryState.SAME
        }

        val sameSize = snapshot.remoteSize == remote.size
        val sameMtime = snapshot.remoteMtime != null &&
            abs(snapshot.remoteMtime - remote.lastModified) <= MTIME_TOLERANCE_MS
        return if (sameSize && sameMtime) EntryState.SAME else EntryState.CHANGED
    }

    private fun scanLocalFiles(folderPath: String): Map<String, LocalSnapshot> {
        val root = File(folderPath)
        if (!root.exists() || !root.isDirectory) {
            return emptyMap()
        }

        return root.walkTopDown()
            .filter { it.isFile }
            .associate { file ->
                val relativePath = file.relativeTo(root).invariantSeparatorsPath
                relativePath to LocalSnapshot(
                    relativePath = relativePath,
                    absolutePath = file.absolutePath,
                    size = file.length(),
                    lastModified = file.lastModified(),
                )
            }
    }

    private suspend fun scanRemoteFiles(
        remoteRoot: String,
        webDAVClient: WebDAVClient,
    ): Result<Map<String, WebDAVFile>> {
        val results = linkedMapOf<String, WebDAVFile>()
        return scanRemoteDirectory(remoteRoot.trimEnd('/'), remoteRoot.trimEnd('/'), webDAVClient, results)
            .map { results }
    }

    private suspend fun scanRemoteDirectory(
        remoteRoot: String,
        currentPath: String,
        webDAVClient: WebDAVClient,
        collector: MutableMap<String, WebDAVFile>,
    ): Result<Unit> {
        val resources = webDAVClient.listDirectory(currentPath).getOrElse { error ->
            return Result.failure(error)
        }

        resources.forEach { remoteFile ->
            val relativePath = remoteFile.path.removePrefix(remoteRoot).trimStart('/')
            if (relativePath.isBlank()) {
                return@forEach
            }

            if (remoteFile.isDirectory) {
                val nestedResult = scanRemoteDirectory(remoteRoot, remoteFile.path, webDAVClient, collector)
                if (nestedResult.isFailure) {
                    return nestedResult
                }
            } else {
                collector[relativePath] = remoteFile
            }
        }

        return Result.success(Unit)
    }

    private fun isEquivalent(local: LocalSnapshot, remote: WebDAVFile): Boolean {
        return local.size == remote.size &&
            abs(local.lastModified - remote.lastModified) <= MTIME_TOLERANCE_MS
    }

    private fun buildRemotePath(remoteRoot: String, relativePath: String): String {
        return "${remoteRoot.trimEnd('/')}/$relativePath"
    }

    private fun buildLocalPath(localRoot: String, relativePath: String): String {
        return File(localRoot, relativePath).absolutePath
    }

    internal enum class EntryState {
        SAME,
        CHANGED,
        MISSING,
    }
}
