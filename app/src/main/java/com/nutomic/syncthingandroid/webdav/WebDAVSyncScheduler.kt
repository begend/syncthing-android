package com.nutomic.syncthingandroid.webdav

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.nutomic.syncthingandroid.webdav.policy.EInkProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDAVSyncScheduler @Inject constructor() {
    companion object {
        private const val TAG = "WebDAVSyncScheduler"
        private const val DEFAULT_DELAY_MS = 15 * 60 * 1000L
    }

    fun scheduleRetry(
        context: Context,
        folderId: String,
        triggerReason: String,
        profile: EInkProfile,
        decisionReason: String,
    ) {
        val delayMs = calculateDelayMs(profile, decisionReason)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: run {
            Log.w(TAG, "AlarmManager unavailable; cannot schedule retry for $folderId")
            return
        }

        val retryIntent = Intent(context, WebDAVSyncService::class.java).apply {
            action = WebDAVSyncAction.ACTION_RETRY_FOLDER
            putExtra(WebDAVSyncAction.EXTRA_FOLDER_ID, folderId)
            putExtra(WebDAVSyncAction.EXTRA_TRIGGER_REASON, "retry:$triggerReason")
        }

        val pendingIntent = PendingIntent.getService(
            context,
            folderId.hashCode(),
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val triggerAtMillis = System.currentTimeMillis() + delayMs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }

        Log.i(TAG, "Scheduled retry for folder=$folderId in ${delayMs}ms due to: $decisionReason")
    }

    fun cancelRetry(context: Context, folderId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val retryIntent = Intent(context, WebDAVSyncService::class.java).apply {
            action = WebDAVSyncAction.ACTION_RETRY_FOLDER
            putExtra(WebDAVSyncAction.EXTRA_FOLDER_ID, folderId)
        }
        val pendingIntent = PendingIntent.getService(
            context,
            folderId.hashCode(),
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    internal fun calculateDelayMs(profile: EInkProfile, decisionReason: String): Long {
        val lowerReason = decisionReason.lowercase()
        return when {
            "charging" in lowerReason -> if (profile.isEInk) 30 * 60 * 1000L else 15 * 60 * 1000L
            "active" in lowerReason || "interaction" in lowerReason -> if (profile.isEInk) 10 * 60 * 1000L else 5 * 60 * 1000L
            "network" in lowerReason -> 10 * 60 * 1000L
            "battery" in lowerReason -> 20 * 60 * 1000L
            profile.isEInk -> 15 * 60 * 1000L
            else -> DEFAULT_DELAY_MS
        }
    }
}
