package com.nutomic.syncthingandroid.webdav

import com.nutomic.syncthingandroid.webdav.policy.EInkProfile
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WebDAVSyncSchedulerTest {
    private lateinit var scheduler: WebDAVSyncScheduler

    @Before
    fun setUp() {
        scheduler = WebDAVSyncScheduler()
    }

    @Test
    fun calculateDelayMs_usesLongerDelayForEInkChargingPreference() {
        val delay = scheduler.calculateDelayMs(EInkProfile.ONYX, "Deferred: onyx profile prefers charging")

        assertEquals(30 * 60 * 1000L, delay)
    }

    @Test
    fun calculateDelayMs_usesShorterDelayForInteractionDeferralOnNormalDevices() {
        val delay = scheduler.calculateDelayMs(EInkProfile.NORMAL, "Deferred: device is active")

        assertEquals(5 * 60 * 1000L, delay)
    }

    @Test
    fun calculateDelayMs_usesNetworkDelayForNetworkReason() {
        val delay = scheduler.calculateDelayMs(EInkProfile.GENERIC_EINK, "Skipped: unmetered network required")

        assertEquals(10 * 60 * 1000L, delay)
    }
}
