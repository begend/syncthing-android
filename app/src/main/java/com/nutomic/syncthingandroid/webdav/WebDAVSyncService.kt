package com.nutomic.syncthingandroid.webdav

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.SyncthingApp
import com.nutomic.syncthingandroid.activities.MainActivity
import com.nutomic.syncthingandroid.webdav.policy.EInkProfile
import com.nutomic.syncthingandroid.webdav.policy.EInkProfileResolver
import com.nutomic.syncthingandroid.webdav.policy.SyncExecutionPolicyEvaluator
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVSyncRunEntity
import com.nutomic.syncthingandroid.webdav.persistence.repository.SyncStateRepository
import com.nutomic.syncthingandroid.webdav.persistence.repository.WebDAVConfigRepository
import com.nutomic.syncthingandroid.webdav.sync.SyncPlanner
import com.nutomic.syncthingandroid.webdav.sync.PlannedSyncAction
import com.nutomic.syncthingandroid.webdav.sync.TransferExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class WebDAVSyncService : Service() {
    companion object {
        private const val TAG = "WebDAVSyncService"
        private const val CHANNEL_ID = "04_webdav_sync"
        private const val NOTIFICATION_ID = 2002

        private const val STATE_QUEUED = "QUEUED"
        private const val STATE_RUNNING = "RUNNING"
        private const val STATE_COMPLETED = "COMPLETED"
        private const val STATE_CANCELLED = "CANCELLED"
        private const val STATE_FAILED = "FAILED"
        private const val STATE_SKIPPED = "SKIPPED"

        private const val DEFAULT_TRIGGER_REASON = "manual"
    }

    @Inject
    lateinit var configRepository: WebDAVConfigRepository

    @Inject
    lateinit var syncStateRepository: SyncStateRepository

    @Inject
    lateinit var syncPlanner: SyncPlanner

    @Inject
    lateinit var transferExecutor: TransferExecutor

    @Inject
    lateinit var eInkProfileResolver: EInkProfileResolver

    @Inject
    lateinit var syncExecutionPolicyEvaluator: SyncExecutionPolicyEvaluator

    @Inject
    lateinit var webDAVSyncScheduler: WebDAVSyncScheduler

    private val binder = WebDAVSyncServiceBinder(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeFolderJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val activeRunIds = ConcurrentHashMap<String, String>()
    private val lastNotificationUpdateByFolder = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        (application as SyncthingApp).component().inject(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            WebDAVSyncAction.ACTION_SYNC_FOLDER,
            WebDAVSyncAction.ACTION_RETRY_FOLDER -> {
                val folderId = intent.getStringExtra(WebDAVSyncAction.EXTRA_FOLDER_ID)
                if (folderId.isNullOrBlank()) {
                    Log.w(TAG, "Missing folder id for action ${intent.action}")
                    stopSelfResult(startId)
                    return Service.START_NOT_STICKY
                }
                val triggerReason = intent.getStringExtra(WebDAVSyncAction.EXTRA_TRIGGER_REASON)
                    ?: DEFAULT_TRIGGER_REASON
                startFolderSync(folderId, triggerReason)
            }

            WebDAVSyncAction.ACTION_SYNC_ALL -> startAllEligibleFolders()

            WebDAVSyncAction.ACTION_CANCEL_FOLDER -> {
                val folderId = intent.getStringExtra(WebDAVSyncAction.EXTRA_FOLDER_ID)
                if (!folderId.isNullOrBlank()) {
                    cancelFolderSync(folderId)
                } else {
                    Log.w(TAG, "Missing folder id for cancel action")
                }
            }

            else -> Log.d(TAG, "Ignoring unsupported action: ${intent?.action}")
        }

        return Service.START_NOT_STICKY
    }

    fun cancelFolderSync(folderId: String) {
        activeFolderJobs.remove(folderId)?.cancel()
    }

    private fun startAllEligibleFolders() {
        serviceScope.launch {
            val enabledFolders = configRepository.getEnabledFolders()
            if (enabledFolders.isEmpty()) {
                Log.i(TAG, "No enabled WebDAV folders available for sync")
                stopServiceIfIdle()
                return@launch
            }

            enabledFolders.forEach { folder ->
                startFolderSync(folder.id, "sync_all")
            }
        }
    }

    private fun startFolderSync(folderId: String, triggerReason: String) {
        if (activeFolderJobs.containsKey(folderId)) {
            Log.i(TAG, "Skipping duplicate sync request for folder $folderId")
            return
        }

        webDAVSyncScheduler.cancelRetry(applicationContext, folderId)

        val runId = UUID.randomUUID().toString()
        val job = serviceScope.launch {
            activeRunIds[folderId] = runId
            startForeground(NOTIFICATION_ID, buildNotification(folderId, "Queued"))
            var webDAVClient: WebDAVClient? = null

            syncStateRepository.createOrUpdateRun(
                WebDAVSyncRunEntity(
                    runId = runId,
                    folderId = folderId,
                    triggerReason = triggerReason,
                    state = STATE_QUEUED,
                    startedAt = System.currentTimeMillis(),
                    endedAt = null,
                    successCount = 0,
                    failureCount = 0,
                    conflictCount = 0,
                    bytesTransferred = 0L,
                    errorSummary = null,
                )
            )

            try {
                syncStateRepository.markInProgressCheckpointsInterrupted(
                    folderId = folderId,
                    errorSummary = "Recovered on next launch",
                )
                val folder = configRepository.getFolderConfig(folderId)
                    ?: error("Folder config not found for $folderId")
                val server = configRepository.getServerConfig(folder.serverId)
                    ?: error("Server config not found for ${folder.serverId}")
                val eInkProfile = eInkProfileResolver.resolve(applicationContext)
                val pendingCheckpoints = syncStateRepository.getCheckpointsForFolder(folderId)
                val executionDecision = syncExecutionPolicyEvaluator.evaluate(
                    context = applicationContext,
                    profile = eInkProfile,
                    folderConfig = folder,
                    triggerReason = triggerReason,
                )

                if (!executionDecision.shouldRun) {
                    val skippedAt = System.currentTimeMillis()
                    syncStateRepository.createOrUpdateRun(
                        WebDAVSyncRunEntity(
                            runId = runId,
                            folderId = folderId,
                            triggerReason = triggerReason,
                            state = STATE_SKIPPED,
                            startedAt = syncStateRepository.getRun(runId)?.startedAt ?: skippedAt,
                            endedAt = skippedAt,
                            successCount = 0,
                            failureCount = 0,
                            conflictCount = 0,
                            bytesTransferred = 0L,
                            errorSummary = executionDecision.reason,
                        )
                    )
                    updateForegroundNotification(
                        folderId = folderId,
                        contentText = executionDecision.reason,
                        profile = eInkProfile,
                        force = true,
                    )
                    webDAVSyncScheduler.scheduleRetry(
                        context = applicationContext,
                        folderId = folderId,
                        triggerReason = triggerReason,
                        profile = eInkProfile,
                        decisionReason = executionDecision.reason,
                    )
                    Log.i(TAG, "Skipping WebDAV sync for folder=$folderId: ${executionDecision.reason}")
                    return@launch
                }

                webDAVClient = WebDAVClient(applicationContext)

                syncStateRepository.updateRunState(
                    runId = runId,
                    state = STATE_RUNNING,
                    endedAt = null,
                    errorSummary = null,
                )

                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(
                        folderId,
                        if (pendingCheckpoints.isEmpty()) {
                            "Preparing sync for ${folder.localPath}"
                        } else {
                            "Recovering ${pendingCheckpoints.size} pending transfers"
                        }
                    )
                )

                val existingEntries = syncStateRepository.getEntriesForFolder(folderId)
                val authType = runCatching {
                    WebDAVClient.AuthType.valueOf(server.authType)
                }.getOrDefault(WebDAVClient.AuthType.BASIC)

                webDAVClient.connect(
                    WebDAVClient.ConnectionConfig(
                        serverUrl = server.baseUrl,
                        username = server.username,
                        // Phase 1 keeps this field as a placeholder until secure credential storage lands.
                        password = server.passwordAlias,
                        authType = authType,
                        connectTimeoutMs = server.connectTimeoutMs,
                        readTimeoutMs = server.readTimeoutMs,
                    )
                )
                    .getOrThrow()

                val syncPlan = syncPlanner.planFolderSync(folder, existingEntries, webDAVClient).getOrThrow()
                Log.i(TAG, "WebDAV sync plan for folder=${folder.id}: ${syncPlan.summary()}")
                if (pendingCheckpoints.isNotEmpty()) {
                    Log.i(TAG, "Recovered ${pendingCheckpoints.size} checkpoints for folder=${folder.id}")
                }
                Log.i(
                    TAG,
                    "Using E-Ink profile ${eInkProfile.id} for folder=${folder.id}, isEInk=${eInkProfile.isEInk}, brand=${eInkProfile.brand}, preferCharging=${eInkProfile.preferCharging}"
                )
                updateForegroundNotification(
                    folderId = folderId,
                    contentText = "Executing ${syncPlan.actions.size} sync actions",
                    profile = eInkProfile,
                    force = true,
                )

                val executionResult = transferExecutor.executePlan(
                    folderConfig = folder,
                    plan = syncPlan,
                    webDAVClient = webDAVClient,
                    interActionDelayMs = eInkProfile.interActionDelayMs,
                    onActionProcessed = { processedCount, totalCount, action ->
                        updateForegroundNotification(
                            folderId = folderId,
                            contentText = buildProgressText(processedCount, totalCount, action, eInkProfile),
                            profile = eInkProfile,
                            force = processedCount == totalCount,
                        )
                    },
                )
                val completedAt = System.currentTimeMillis()
                val finalState = if (executionResult.failedActions > 0) STATE_FAILED else STATE_COMPLETED

                syncStateRepository.upsertEntries(executionResult.updatedEntries)
                syncStateRepository.deleteEntries(folderId, executionResult.removedRelativePaths)

                syncStateRepository.createOrUpdateRun(
                    WebDAVSyncRunEntity(
                        runId = runId,
                        folderId = folderId,
                        triggerReason = triggerReason,
                        state = finalState,
                        startedAt = syncStateRepository.getRun(runId)?.startedAt ?: completedAt,
                        endedAt = completedAt,
                        successCount = executionResult.successfulActions,
                        failureCount = executionResult.failedActions,
                        conflictCount = executionResult.conflictCount,
                        bytesTransferred = executionResult.bytesTransferred,
                        errorSummary = buildString {
                            append(syncPlan.summary())
                            append("; ")
                            append(executionResult.summary())
                            if (executionResult.failureMessages.isNotEmpty()) {
                                append("; failures=")
                                append(executionResult.failureMessages.joinToString("|"))
                            }
                        },
                    )
                )
                if (finalState == STATE_COMPLETED) {
                    syncStateRepository.deleteCheckpointsForFolder(folderId)
                    webDAVSyncScheduler.cancelRetry(applicationContext, folderId)
                } else if (executionResult.retryableFailureCount > 0) {
                    webDAVSyncScheduler.scheduleRetry(
                        context = applicationContext,
                        folderId = folderId,
                        triggerReason = triggerReason,
                        profile = eInkProfile,
                        decisionReason = "retryable execution failure",
                    )
                }
                updateForegroundNotification(
                    folderId = folderId,
                    contentText = "Executed ${executionResult.successfulActions}/${syncPlan.actions.size} actions",
                    profile = eInkProfile,
                    force = true,
                )
            } catch (cancelled: CancellationException) {
                syncStateRepository.markInProgressCheckpointsInterrupted(
                    folderId = folderId,
                    errorSummary = cancelled.message ?: "Cancelled",
                )
                syncStateRepository.updateRunState(
                    runId = runId,
                    state = STATE_CANCELLED,
                    endedAt = System.currentTimeMillis(),
                    errorSummary = cancelled.message,
                )
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(folderId, "Sync cancelled")
                )
                throw cancelled
            } catch (t: Throwable) {
                Log.e(TAG, "WebDAV sync failed for folder $folderId", t)
                val classifiedError = WebDAVError.classify(t)
                syncStateRepository.markInProgressCheckpointsInterrupted(
                    folderId = folderId,
                    errorSummary = classifiedError.messageText,
                )
                syncStateRepository.updateRunState(
                    runId = runId,
                    state = STATE_FAILED,
                    endedAt = System.currentTimeMillis(),
                    errorSummary = classifiedError.messageText,
                )
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(folderId, "Sync failed")
                )
                val eInkProfile = runCatching { eInkProfileResolver.resolve(applicationContext) }
                    .getOrDefault(EInkProfile.NORMAL)
                if (classifiedError.retryable) {
                    webDAVSyncScheduler.scheduleRetry(
                        context = applicationContext,
                        folderId = folderId,
                        triggerReason = triggerReason,
                        profile = eInkProfile,
                        decisionReason = classifiedError.messageText,
                    )
                }
            } finally {
                webDAVClient?.disconnect()
                activeFolderJobs.remove(folderId)
                activeRunIds.remove(folderId)
                lastNotificationUpdateByFolder.remove(folderId)
                stopServiceIfIdle()
            }
        }

        activeFolderJobs[folderId] = job
    }

    private fun stopServiceIfIdle() {
        if (activeFolderJobs.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WebDAV Sync",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "WebDAV synchronization progress"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(folderId: String, contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("WebDAV [$folderId] $contentText")
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateForegroundNotification(
        folderId: String,
        contentText: String,
        profile: EInkProfile,
        force: Boolean,
    ) {
        val now = System.currentTimeMillis()
        val lastUpdate = lastNotificationUpdateByFolder[folderId] ?: 0L
        if (!force && profile.isEInk && (now - lastUpdate) < profile.notificationUpdateIntervalMs) {
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification(folderId, contentText))
        lastNotificationUpdateByFolder[folderId] = now
    }

    private fun buildProgressText(
        processedCount: Int,
        totalCount: Int,
        action: PlannedSyncAction,
        profile: EInkProfile,
    ): String {
        val base = when (action) {
            is PlannedSyncAction.Upload -> "Uploading ${action.relativePath}"
            is PlannedSyncAction.Download -> "Downloading ${action.relativePath}"
            is PlannedSyncAction.DeleteRemote -> "Deleting remote ${action.relativePath}"
            is PlannedSyncAction.DeleteLocal -> "Deleting local ${action.relativePath}"
            is PlannedSyncAction.Conflict -> "Conflict ${action.relativePath}"
        }

        return if (profile.forceMinimalNotifications) {
            "Step $processedCount/$totalCount"
        } else {
            "$base ($processedCount/$totalCount)"
        }
    }

    override fun onDestroy() {
        activeFolderJobs.values.forEach { it.cancel() }
        activeFolderJobs.clear()
        activeRunIds.clear()
        lastNotificationUpdateByFolder.clear()
        serviceScope.cancel()
        super.onDestroy()
    }
}
