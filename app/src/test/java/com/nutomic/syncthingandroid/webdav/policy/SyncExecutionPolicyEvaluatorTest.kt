package com.nutomic.syncthingandroid.webdav.policy

import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVFolderConfigEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncExecutionPolicyEvaluatorTest {
    private lateinit var evaluator: SyncExecutionPolicyEvaluator
    private lateinit var folderConfig: WebDAVFolderConfigEntity

    @Before
    fun setUp() {
        evaluator = SyncExecutionPolicyEvaluator()
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
    fun evaluateState_skipsAutomaticOnyxSyncWhenNotCharging() {
        val decision = evaluator.evaluateState(
            profile = EInkProfile.ONYX,
            folderConfig = folderConfig,
            triggerReason = "sync_all",
            isCharging = false,
            isUnmeteredNetwork = true,
            isInteractive = false,
            batteryNotLow = true,
        )

        assertFalse(decision.shouldRun)
        assertTrue(decision.reason.contains("charging", ignoreCase = true))
    }

    @Test
    fun evaluateState_allowsManualOnyxSyncEvenWhenNotCharging() {
        val decision = evaluator.evaluateState(
            profile = EInkProfile.ONYX,
            folderConfig = folderConfig,
            triggerReason = "manual",
            isCharging = false,
            isUnmeteredNetwork = true,
            isInteractive = true,
            batteryNotLow = true,
        )

        assertTrue(decision.shouldRun)
    }

    @Test
    fun evaluateState_respectsFolderWifiOnlyConstraint() {
        val decision = evaluator.evaluateState(
            profile = EInkProfile.NORMAL,
            folderConfig = folderConfig.copy(wifiOnly = true),
            triggerReason = "manual",
            isCharging = true,
            isUnmeteredNetwork = false,
            isInteractive = false,
            batteryNotLow = true,
        )

        assertFalse(decision.shouldRun)
        assertTrue(decision.reason.contains("network", ignoreCase = true))
    }

    @Test
    fun evaluateState_skipsAutomaticEInkSyncDuringInteraction() {
        val decision = evaluator.evaluateState(
            profile = EInkProfile.GENERIC_EINK,
            folderConfig = folderConfig,
            triggerReason = "retry",
            isCharging = true,
            isUnmeteredNetwork = true,
            isInteractive = true,
            batteryNotLow = true,
        )

        assertFalse(decision.shouldRun)
        assertTrue(decision.reason.contains("active", ignoreCase = true))
    }
}
