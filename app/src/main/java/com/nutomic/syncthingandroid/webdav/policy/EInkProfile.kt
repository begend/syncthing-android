package com.nutomic.syncthingandroid.webdav.policy

data class EInkProfile(
    val id: String,
    val isEInk: Boolean,
    val brand: String,
    val maxParallelTransfers: Int,
    val notificationUpdateIntervalMs: Long,
    val uiUpdateIntervalMs: Long,
    val interActionDelayMs: Long,
    val preferCharging: Boolean,
    val pauseDuringInteraction: Boolean,
    val forceMinimalNotifications: Boolean,
) {
    companion object {
        val NORMAL = EInkProfile(
            id = "normal",
            isEInk = false,
            brand = "lcd",
            maxParallelTransfers = 2,
            notificationUpdateIntervalMs = 1_000L,
            uiUpdateIntervalMs = 500L,
            interActionDelayMs = 0L,
            preferCharging = false,
            pauseDuringInteraction = false,
            forceMinimalNotifications = false,
        )

        val GENERIC_EINK = EInkProfile(
            id = "generic-eink",
            isEInk = true,
            brand = "generic",
            maxParallelTransfers = 1,
            notificationUpdateIntervalMs = 5_000L,
            uiUpdateIntervalMs = 3_000L,
            interActionDelayMs = 250L,
            preferCharging = true,
            pauseDuringInteraction = true,
            forceMinimalNotifications = true,
        )

        val ONYX = EInkProfile(
            id = "onyx-boox",
            isEInk = true,
            brand = "onyx",
            maxParallelTransfers = 1,
            notificationUpdateIntervalMs = 8_000L,
            uiUpdateIntervalMs = 3_000L,
            interActionDelayMs = 400L,
            preferCharging = true,
            pauseDuringInteraction = true,
            forceMinimalNotifications = true,
        )
    }
}
