package com.nutomic.syncthingandroid.webdav.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EInkProfileResolverTest {
    private lateinit var resolver: EInkProfileResolver

    @Before
    fun setUp() {
        resolver = EInkProfileResolver()
    }

    @Test
    fun resolveProfile_returnsOnyxProfileForOnyxDevices() {
        val profile = resolver.resolveProfile(
            brand = "onyx",
            isEInk = true,
            isOnyx = true,
            supportsPenInput = true,
        )

        assertEquals("onyx-boox", profile.id)
        assertTrue(profile.isEInk)
        assertTrue(profile.pauseDuringInteraction)
        assertEquals(1, profile.maxParallelTransfers)
        assertTrue(profile.notificationUpdateIntervalMs >= 8000L)
    }

    @Test
    fun resolveProfile_returnsNormalForUnsupportedBrand() {
        val profile = resolver.resolveProfile(
            brand = "unknown",
            isEInk = false,
            isOnyx = false,
            supportsPenInput = false,
        )

        assertEquals(EInkProfile.NORMAL.id, profile.id)
        assertFalse(profile.isEInk)
    }

    @Test
    fun resolveProfile_returnsGenericEInkForSupportedNonOnyxBrand() {
        val profile = resolver.resolveProfile(
            brand = "hisense",
            isEInk = true,
            isOnyx = false,
            supportsPenInput = false,
        )

        assertTrue(profile.isEInk)
        assertEquals("hisense", profile.brand)
        assertEquals(1, profile.maxParallelTransfers)
        assertTrue(profile.forceMinimalNotifications)
    }
}
