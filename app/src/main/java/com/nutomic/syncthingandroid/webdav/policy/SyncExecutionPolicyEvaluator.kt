package com.nutomic.syncthingandroid.webdav.policy

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVFolderConfigEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncExecutionPolicyEvaluator @Inject constructor() {
    fun evaluate(
        context: Context,
        profile: EInkProfile,
        folderConfig: WebDAVFolderConfigEntity,
        triggerReason: String,
    ): SyncExecutionDecision {
        val state = readDeviceState(context)
        return evaluateState(
            profile = profile,
            folderConfig = folderConfig,
            triggerReason = triggerReason,
            isCharging = state.isCharging,
            isUnmeteredNetwork = state.isUnmeteredNetwork,
            isInteractive = state.isInteractive,
            batteryNotLow = state.batteryNotLow,
        )
    }

    internal fun evaluateState(
        profile: EInkProfile,
        folderConfig: WebDAVFolderConfigEntity,
        triggerReason: String,
        isCharging: Boolean,
        isUnmeteredNetwork: Boolean,
        isInteractive: Boolean,
        batteryNotLow: Boolean,
    ): SyncExecutionDecision {
        val isManualTrigger = triggerReason.equals("manual", ignoreCase = true)

        if (folderConfig.chargingOnly && !isCharging) {
            return SyncExecutionDecision(false, "Skipped: charging required")
        }

        if (folderConfig.wifiOnly && !isUnmeteredNetwork) {
            return SyncExecutionDecision(false, "Skipped: unmetered network required")
        }

        if (folderConfig.batteryNotLow && !batteryNotLow) {
            return SyncExecutionDecision(false, "Skipped: battery is low")
        }

        if (!isManualTrigger && profile.preferCharging && !isCharging) {
            return SyncExecutionDecision(false, "Deferred: ${profile.brand} profile prefers charging")
        }

        if (!isManualTrigger && profile.pauseDuringInteraction && isInteractive) {
            return SyncExecutionDecision(false, "Deferred: ${profile.brand} device is active")
        }

        return SyncExecutionDecision(true, "Allowed")
    }

    private fun readDeviceState(context: Context): DeviceRuntimeState {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) level.toFloat() / scale.toFloat() else 1f
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
        val isUnmeteredNetwork = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true ||
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

        val powerManager = context.getSystemService(PowerManager::class.java)
        val isInteractive = powerManager?.isInteractive ?: false

        return DeviceRuntimeState(
            isCharging = isCharging,
            isUnmeteredNetwork = isUnmeteredNetwork,
            isInteractive = isInteractive,
            batteryNotLow = batteryPct > 0.15f,
        )
    }

    private data class DeviceRuntimeState(
        val isCharging: Boolean,
        val isUnmeteredNetwork: Boolean,
        val isInteractive: Boolean,
        val batteryNotLow: Boolean,
    )
}
