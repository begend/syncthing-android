package com.nutomic.syncthingandroid.webdav.sync

import android.util.Log
import com.nutomic.syncthingandroid.webdav.WebDAVClient
import com.nutomic.syncthingandroid.webdav.WebDAVError
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVFolderConfigEntity
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVSyncEntryEntity
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVTransferCheckpointEntity
import com.nutomic.syncthingandroid.webdav.persistence.repository.SyncStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

data class TransferExecutionResult(
    val successfulActions: Int,
    val failedActions: Int,
    val retryableFailureCount: Int,
    val conflictCount: Int,
    val bytesTransferred: Long,
    val updatedEntries: List<WebDAVSyncEntryEntity>,
    val removedRelativePaths: List<String>,
    val failureMessages: List<String>,
) {
    fun summary(): String {
        return "success=$successfulActions, failed=$failedActions, conflicts=$conflictCount, bytes=$bytesTransferred"
    }
}

class TransferExecutor @Inject constructor(
    private val syncStateRepository: SyncStateRepository,
) {
    companion object {
        private const val TAG = "TransferExecutor"
        private const val PART_SUFFIX = ".part"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_MS = 1_000L
    }

    suspend fun executePlan(
        folderConfig: WebDAVFolderConfigEntity,
        plan: SyncPlan,
        webDAVClient: WebDAVClient,
        interActionDelayMs: Long = 0L,
        onActionProcessed: ((processedCount: Int, totalCount: Int, action: PlannedSyncAction) -> Unit)? = null,
    ): TransferExecutionResult = withContext(Dispatchers.IO) {
        val existingCheckpoints = syncStateRepository.getCheckpointsForFolder(folderConfig.id)
            .associateBy { it.relativePath }
        val updatedEntries = mutableListOf<WebDAVSyncEntryEntity>()
        val removedPaths = mutableListOf<String>()
        val failureMessages = mutableListOf<String>()
        var successCount = 0
        var failedCount = 0
        var retryableFailureCount = 0
        var conflictCount = 0
        var bytesTransferred = 0L
        var processedCount = 0
        val totalCount = plan.actions.size

        for (action in plan.actions) {
            try {
                upsertCheckpoint(folderConfig, action, state = "IN_PROGRESS", retryable = false, errorSummary = null)

                when (action) {
                    is PlannedSyncAction.Upload -> {
                        val result = executeUpload(folderConfig, action, webDAVClient)
                        result.onSuccess { outcome ->
                            successCount += 1
                            bytesTransferred += outcome.bytesTransferred
                            updatedEntries += outcome.entry
                            clearCheckpoint(folderConfig.id, action.relativePath)
                        }.onFailure { error ->
                            failedCount += 1
                            val classified = WebDAVError.classify(error)
                            if (classified.retryable) {
                                retryableFailureCount += 1
                            }
                            checkpointFailure(folderConfig, action, classified)
                            failureMessages += "upload:${action.relativePath}:${error.message}"
                        }
                    }

                    is PlannedSyncAction.Download -> {
                        val result = executeDownload(
                            folderConfig = folderConfig,
                            action = action,
                            webDAVClient = webDAVClient,
                            existingCheckpoint = existingCheckpoints[action.relativePath],
                        )
                        result.onSuccess { outcome ->
                            successCount += 1
                            bytesTransferred += outcome.bytesTransferred
                            updatedEntries += outcome.entry
                            clearCheckpoint(folderConfig.id, action.relativePath)
                        }.onFailure { error ->
                            failedCount += 1
                            val classified = WebDAVError.classify(error)
                            if (classified.retryable) {
                                retryableFailureCount += 1
                            }
                            checkpointFailure(folderConfig, action, classified)
                            failureMessages += "download:${action.relativePath}:${error.message}"
                        }
                    }

                    is PlannedSyncAction.DeleteRemote -> {
                        val result = withRetry("deleteRemote:${action.relativePath}", shouldRetry = {
                                WebDAVError.classify(it).retryable
                            }) {
                                webDAVClient.deleteFile(normalizeRemotePath(action.remotePath))
                            }
                        result.onSuccess {
                            successCount += 1
                            removedPaths += action.relativePath
                            clearCheckpoint(folderConfig.id, action.relativePath)
                        }.onFailure { error ->
                            failedCount += 1
                            val classified = WebDAVError.classify(error)
                            if (classified.retryable) {
                                retryableFailureCount += 1
                            }
                            checkpointFailure(folderConfig, action, classified)
                            failureMessages += "deleteRemote:${action.relativePath}:${error.message}"
                        }
                    }

                    is PlannedSyncAction.DeleteLocal -> {
                        val result = executeDeleteLocal(action)
                        result.onSuccess {
                            successCount += 1
                            removedPaths += action.relativePath
                            clearCheckpoint(folderConfig.id, action.relativePath)
                        }.onFailure { error ->
                            failedCount += 1
                            checkpointFailure(folderConfig, action, WebDAVError.classify(error))
                            failureMessages += "deleteLocal:${action.relativePath}:${error.message}"
                        }
                    }

                    is PlannedSyncAction.Conflict -> {
                        conflictCount += 1
                        clearCheckpoint(folderConfig.id, action.relativePath)
                        Log.w(TAG, "Conflict left unresolved for ${action.relativePath}: ${action.conflictType}")
                    }
                }
            } catch (cancelled: CancellationException) {
                upsertCheckpoint(
                    folderConfig = folderConfig,
                    action = action,
                    state = "INTERRUPTED",
                    retryable = true,
                    errorSummary = cancelled.message ?: "Interrupted",
                )
                throw cancelled
            } catch (t: Throwable) {
                val classified = WebDAVError.classify(t)
                failedCount += 1
                if (classified.retryable) {
                    retryableFailureCount += 1
                }
                checkpointFailure(folderConfig, action, classified)
                failureMessages += "unexpected:${action.relativePath}:${classified.messageText}"
            } finally {
                processedCount += 1
                onActionProcessed?.invoke(processedCount, totalCount, action)
                if (interActionDelayMs > 0 && processedCount < totalCount) {
                    delay(interActionDelayMs)
                }
            }
        }

        TransferExecutionResult(
            successfulActions = successCount,
            failedActions = failedCount,
            retryableFailureCount = retryableFailureCount,
            conflictCount = conflictCount,
            bytesTransferred = bytesTransferred,
            updatedEntries = updatedEntries,
            removedRelativePaths = removedPaths.distinct(),
            failureMessages = failureMessages,
        )
    }

    private suspend fun executeUpload(
        folderConfig: WebDAVFolderConfigEntity,
        action: PlannedSyncAction.Upload,
        webDAVClient: WebDAVClient,
    ): Result<FileOutcome> {
        val localFile = File(action.localPath)
        if (!localFile.exists() || !localFile.isFile) {
            return Result.failure(IllegalStateException("Local file missing: ${action.localPath}"))
        }

        return withRetry("upload:${action.relativePath}", shouldRetry = {
            WebDAVError.classify(it).retryable
        }) {
            webDAVClient.uploadFile(
                localPath = localFile.absolutePath,
                remotePath = normalizeRemotePath(action.remotePath),
            )
        }.mapCatching {
            val fileInfo = webDAVClient.getFileInfo(normalizeRemotePath(action.remotePath)).getOrThrow()
            FileOutcome(
                entry = WebDAVSyncEntryEntity(
                    folderId = folderConfig.id,
                    relativePath = action.relativePath,
                    localSize = localFile.length(),
                    localMtime = localFile.lastModified(),
                    localFingerprint = null,
                    remoteEtag = fileInfo.etag,
                    remoteSize = fileInfo.size,
                    remoteMtime = fileInfo.lastModified,
                    lastSyncTime = System.currentTimeMillis(),
                    existsLocal = true,
                    existsRemote = true,
                    deletedLocal = false,
                    deletedRemote = false,
                    updatedAt = System.currentTimeMillis(),
                ),
                bytesTransferred = localFile.length(),
            )
        }
    }

    private suspend fun executeDownload(
        folderConfig: WebDAVFolderConfigEntity,
        action: PlannedSyncAction.Download,
        webDAVClient: WebDAVClient,
        existingCheckpoint: WebDAVTransferCheckpointEntity?,
    ): Result<FileOutcome> {
        val targetFile = File(action.localPath)
        val tempFile = existingCheckpoint?.tempFilePath?.let(::File) ?: File("${targetFile.absolutePath}$PART_SUFFIX")
        tempFile.parentFile?.mkdirs()

        val existingLength = tempFile.takeIf { it.exists() }?.length() ?: 0L
        if (existingLength > 0L && existingLength == action.remoteFile.size) {
            return runCatching {
                finalizeDownloadedFile(tempFile, targetFile, action.remoteFile.size)
                FileOutcome(
                    entry = WebDAVSyncEntryEntity(
                        folderId = folderConfig.id,
                        relativePath = action.relativePath,
                        localSize = targetFile.length(),
                        localMtime = targetFile.lastModified(),
                        localFingerprint = null,
                        remoteEtag = action.remoteFile.etag,
                        remoteSize = action.remoteFile.size,
                        remoteMtime = action.remoteFile.lastModified,
                        lastSyncTime = System.currentTimeMillis(),
                        existsLocal = true,
                        existsRemote = true,
                        deletedLocal = false,
                        deletedRemote = false,
                        updatedAt = System.currentTimeMillis(),
                    ),
                    bytesTransferred = 0L,
                )
            }
        }

        if (existingLength > 0L && existingLength != action.remoteFile.size && !tempFile.delete()) {
            return Result.failure(IllegalStateException("Failed to reset partial temp file: ${tempFile.absolutePath}"))
        }

        return withRetry("download:${action.relativePath}", shouldRetry = {
            WebDAVError.classify(it).retryable
        }) {
            webDAVClient.downloadFile(
                remotePath = normalizeRemotePath(action.remoteFile.path),
                localPath = tempFile.absolutePath,
            )
        }.mapCatching {
            finalizeDownloadedFile(tempFile, targetFile, action.remoteFile.size)

            FileOutcome(
                entry = WebDAVSyncEntryEntity(
                    folderId = folderConfig.id,
                    relativePath = action.relativePath,
                    localSize = targetFile.length(),
                    localMtime = targetFile.lastModified(),
                    localFingerprint = null,
                    remoteEtag = action.remoteFile.etag,
                    remoteSize = action.remoteFile.size,
                    remoteMtime = action.remoteFile.lastModified,
                    lastSyncTime = System.currentTimeMillis(),
                    existsLocal = true,
                    existsRemote = true,
                    deletedLocal = false,
                    deletedRemote = false,
                    updatedAt = System.currentTimeMillis(),
                ),
                bytesTransferred = targetFile.length(),
            )
        }
    }

    private fun executeDeleteLocal(action: PlannedSyncAction.DeleteLocal): Result<Unit> {
        val localFile = File(action.localPath)
        if (!localFile.exists()) {
            return Result.success(Unit)
        }

        return if (localFile.delete()) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Failed to delete local file: ${action.localPath}"))
        }
    }

    internal fun finalizeDownloadedFile(tempFile: File, targetFile: File, expectedSize: Long) {
        if (expectedSize > 0 && tempFile.length() != expectedSize) {
            error("Downloaded file size mismatch for ${targetFile.absolutePath}")
        }
        targetFile.parentFile?.mkdirs()
        moveTempFileAtomically(tempFile, targetFile)
    }

    internal suspend fun <T> withRetry(
        operationLabel: String,
        shouldRetry: (Throwable) -> Boolean,
        block: suspend () -> Result<T>,
    ): Result<T> {
        var attempt = 0
        var lastError: Throwable? = null

        while (attempt < MAX_RETRY_ATTEMPTS) {
            val result = block()
            if (result.isSuccess) {
                return result
            }

            lastError = result.exceptionOrNull()
            attempt += 1
            val error = lastError ?: break
            if (attempt >= MAX_RETRY_ATTEMPTS || !shouldRetry(error)) {
                return Result.failure(error)
            }

            val backoffMs = calculateBackoffMs(attempt)
            Log.w(TAG, "Retrying $operationLabel after failure ($attempt/$MAX_RETRY_ATTEMPTS): ${error.message}")
            delay(backoffMs)
        }

        return Result.failure(lastError ?: IllegalStateException("Unknown retry failure"))
    }

    internal fun calculateBackoffMs(attempt: Int): Long {
        val multiplier = 1L shl (attempt - 1).coerceAtLeast(0)
        return INITIAL_BACKOFF_MS * multiplier
    }

    private fun normalizeRemotePath(remotePath: String): String {
        return remotePath.trimStart('/')
    }

    private suspend fun checkpointFailure(
        folderConfig: WebDAVFolderConfigEntity,
        action: PlannedSyncAction,
        classifiedError: WebDAVError,
    ) {
        upsertCheckpoint(
            folderConfig = folderConfig,
            action = action,
            state = if (classifiedError.retryable) "RETRY_PENDING" else "FAILED",
            retryable = classifiedError.retryable,
            errorSummary = classifiedError.messageText,
        )
    }

    private suspend fun upsertCheckpoint(
        folderConfig: WebDAVFolderConfigEntity,
        action: PlannedSyncAction,
        state: String,
        retryable: Boolean,
        errorSummary: String?,
    ) {
        val tempFilePath = when (action) {
            is PlannedSyncAction.Download -> "${action.localPath}$PART_SUFFIX"
            else -> null
        }
        syncStateRepository.upsertCheckpoint(
            WebDAVTransferCheckpointEntity(
                folderId = folderConfig.id,
                relativePath = action.relativePath,
                actionType = action.javaClass.simpleName,
                localPath = when (action) {
                    is PlannedSyncAction.Upload -> action.localPath
                    is PlannedSyncAction.Download -> action.localPath
                    is PlannedSyncAction.DeleteLocal -> action.localPath
                    is PlannedSyncAction.Conflict -> action.localPath
                    is PlannedSyncAction.DeleteRemote -> null
                },
                remotePath = when (action) {
                    is PlannedSyncAction.Upload -> action.remotePath
                    is PlannedSyncAction.Download -> action.remoteFile.path
                    is PlannedSyncAction.DeleteRemote -> action.remotePath
                    is PlannedSyncAction.Conflict -> action.remotePath
                    is PlannedSyncAction.DeleteLocal -> null
                },
                tempFilePath = tempFilePath,
                transferredBytes = tempFilePath?.let { path ->
                    File(path).takeIf { it.exists() }?.length()
                },
                totalBytes = when (action) {
                    is PlannedSyncAction.Upload -> File(action.localPath).takeIf { it.exists() }?.length()
                    is PlannedSyncAction.Download -> action.remoteFile.size
                    else -> null
                },
                state = state,
                retryable = retryable,
                errorSummary = errorSummary,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun clearCheckpoint(folderId: String, relativePath: String) {
        syncStateRepository.deleteCheckpoint(folderId, relativePath)
    }

    private fun moveTempFileAtomically(tempFile: File, targetFile: File) {
        val tempPath = tempFile.toPath()
        val targetPath = targetFile.toPath()

        try {
            Files.move(
                tempPath,
                targetPath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                tempPath,
                targetPath,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private data class FileOutcome(
        val entry: WebDAVSyncEntryEntity,
        val bytesTransferred: Long,
    )
}
