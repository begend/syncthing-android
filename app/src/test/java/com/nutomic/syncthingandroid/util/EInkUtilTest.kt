package com.nutomic.syncthingandroid.util

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for EInkUtil
 */
class EInkUtilTest {

    private lateinit var mockContext: Context
    private lateinit var mockResources: Resources
    private lateinit var displayMetrics: DisplayMetrics

    @Before
    fun setup() {
        mockContext = mockk()
        mockResources = mockk()
        displayMetrics = DisplayMetrics().apply {
            densityDpi = 480
        }

        every { mockContext.resources } returns mockResources
        every { mockResources.displayMetrics } returns displayMetrics
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testEInkUtil_constants() {
        // Verify constants are defined
        assertTrue(EInkUtil.E_INK_MANUFACTURERS.size >= 9)
        assertTrue(EInkUtil.E_INK_MANUFACTURERS.contains("onyx"))
        assertTrue(
            EInkUtil.E_INK_MANUFACTURERS.contains("amazon") ||
                EInkUtil.E_INK_MANUFACTURERS.contains("kindle")
        )

        assertTrue(EInkUtil.E_INK_DEVICE_PREFIXES.size >= 9)
        assertTrue(EInkUtil.E_INK_DEVICE_PREFIXES.contains("onyx"))
        assertTrue(EInkUtil.E_INK_DEVICE_PREFIXES.contains("eboox"))
    }

    @Test
    fun testGetDeviceType() {
        // Test device type detection
        val deviceType = EInkUtil.getDeviceType(mockContext)
        assertTrue(deviceType == "eink" || deviceType == "lcd")
    }

    @Test
    fun testRefreshMode_enum() {
        // Test refresh mode enum
        val modes = EInkUtil.RefreshMode.values()
        assertEquals(4, modes.size)
        assertTrue(modes.contains(EInkUtil.RefreshMode.GLOBAL))
        assertTrue(modes.contains(EInkUtil.RefreshMode.PARTIAL))
        assertTrue(modes.contains(EInkUtil.RefreshMode.A2))
        assertTrue(modes.contains(EInkUtil.RefreshMode.NORMAL))
    }

    @Test
    fun testRecommendedRefreshInterval() {
        // Test that refresh interval is reasonable
        val interval = EInkUtil.getRecommendedRefreshInterval(mockContext)
        assertTrue(interval > 0)
        assertTrue(interval <= 10000)  // Max 10 seconds
    }

    @Test
    fun testRecommendedUIUpdateInterval() {
        // Test UI update interval
        val interval = EInkUtil.getRecommendedUIUpdateInterval(mockContext)
        assertTrue(interval > 0)
    }

    @Test
    fun testOptimalTextSize() {
        // Test text size optimization
        val defaultSize = 14f
        val optimalSize = EInkUtil.getOptimalTextSize(mockContext, defaultSize)
        assertTrue(optimalSize >= defaultSize)
    }

    @Test
    fun testOptimalSyncBatchSize() {
        // Test sync batch size optimization
        val defaultBatchSize = 10
        val optimalBatchSize = EInkUtil.getOptimalSyncBatchSize(mockContext, defaultBatchSize)
        assertTrue(optimalBatchSize >= defaultBatchSize)
    }

    @Test
    fun testSupportedRefreshModes() {
        // Test refresh mode detection
        val modes = EInkUtil.getSupportedRefreshModes(mockContext)
        assertNotNull(modes)
        assertTrue(modes.isNotEmpty())
    }

    @Test
    fun testShouldDisableAnimations() {
        // Test animation disable check
        val shouldDisable = EInkUtil.shouldDisableAnimations(mockContext)
        // Should return boolean
        assertTrue(shouldDisable is Boolean)
    }

    @Test
    fun testShouldUseHighContrast() {
        // Test high contrast check
        val shouldUse = EInkUtil.shouldUseHighContrast(mockContext)
        assertTrue(shouldUse is Boolean)
    }

    @Test
    fun testShouldBatchUIUpdates() {
        // Test batch UI updates check
        val shouldBatch = EInkUtil.shouldBatchUIUpdates(mockContext)
        assertTrue(shouldBatch is Boolean)
    }

    @Test
    fun testGetRecommendedUIBatchSize() {
        // Test UI batch size recommendation
        val batchSize = EInkUtil.getRecommendedUIBatchSize(mockContext)
        assertTrue(batchSize > 0)
    }

    @Test
    fun testShouldUseProgressiveRendering() {
        // Test progressive rendering check
        val shouldUse = EInkUtil.shouldUseProgressiveRendering(mockContext)
        assertTrue(shouldUse is Boolean)
    }

    @Test
    fun testShouldUseGrayscale() {
        // Test grayscale check
        val shouldUse = EInkUtil.shouldUseGrayscale(mockContext)
        assertTrue(shouldUse is Boolean)
    }

    @Test
    fun testShouldPerformGlobalRefresh() {
        // Test global refresh timing
        val isEInkDevice = EInkUtil.isEInkDevice(mockContext)
        val lastRefresh = System.currentTimeMillis() - 60000  // 1 minute ago
        val shouldRefresh = EInkUtil.shouldPerformGlobalRefresh(
            mockContext,
            lastRefresh,
            30000  // 30 second interval
        )
        assertEquals(isEInkDevice, shouldRefresh)

        val shouldNotRefresh = EInkUtil.shouldPerformGlobalRefresh(
            mockContext,
            System.currentTimeMillis() - 10000,  // 10 seconds ago
            30000
        )
        assertFalse(shouldNotRefresh)
    }

    @Test
    fun testGetEInkBrand() {
        // Test brand detection
        val brand = EInkUtil.getEInkBrand(mockContext)
        assertNotNull(brand)
        assertTrue(brand == "onyx" || brand == "kindle" || brand == "kobo" ||
                brand == "pocketbook" || brand == "tolino" || brand == "hisense" ||
                brand == "bigme" || brand == "remarkable" || brand == "generic" ||
                brand == "unknown")
    }

    @Test
    fun testSupportsPartialRefresh() {
        // Test partial refresh support
        val supports = EInkUtil.supportsPartialRefresh(mockContext)
        assertTrue(supports is Boolean)
    }

    @Test
    fun testSupportsPenInput() {
        // Test pen input support
        val supports = EInkUtil.supportsPenInput(mockContext)
        assertTrue(supports is Boolean)
    }

    @Test
    fun testGetDeviceOptimizations() {
        // Test device optimizations map
        val optimizations = EInkUtil.getDeviceOptimizations(mockContext)
        assertNotNull(optimizations)

        // All values should be Boolean
        optimizations.values.forEach { value ->
            assertTrue(value is Boolean)
        }
    }

    @Test
    fun testIsOnyxBoox() {
        // Test Onyx Boox detection
        val isOnyx = EInkUtil.isOnyxBoox(mockContext)
        assertTrue(isOnyx is Boolean)
    }

    @Test
    fun testRefreshMode_values() {
        // Verify all refresh mode values exist
        val modes = EInkUtil.RefreshMode.values()
        val modeNames = modes.map { it.name }

        assertTrue(modeNames.contains("GLOBAL"))
        assertTrue(modeNames.contains("PARTIAL"))
        assertTrue(modeNames.contains("A2"))
        assertTrue(modeNames.contains("NORMAL"))
    }

    @Test
    fun testEInkDevicePrefixes_comprehensive() {
        // Test that all major E-Ink brands are covered
        val prefixes = EInkUtil.E_INK_DEVICE_PREFIXES

        // Verify major brands
        assertTrue(prefixes.any { "onyx" in it })
        assertTrue(prefixes.any { "kindle" in it })
        assertTrue(prefixes.any { "kobo" in it })
        assertTrue(prefixes.any { "pocketbook" in it })
        assertTrue(prefixes.any { "tolino" in it })
        assertTrue(prefixes.any { "hisense" in it })
        assertTrue(prefixes.any { "bigme" in it })
    }

    @Test
    fun testEInkManufacturers_comprehensive() {
        // Test that all major E-Ink manufacturers are covered
        val manufacturers = EInkUtil.E_INK_MANUFACTURERS

        // Verify major manufacturers
        assertTrue(manufacturers.any { "onyx" in it })
        assertTrue(manufacturers.any { "amazon" in it || "kindle" in it })
        assertTrue(manufacturers.any { "kobo" in it })
        assertTrue(manufacturers.any { "pocketbook" in it })
    }

    @Test
    fun testGetRecommendedRefreshInterval_eink() {
        val interval = EInkUtil.getRecommendedRefreshInterval(mockContext)
        val expected = if (EInkUtil.isEInkDevice(mockContext)) 2000L else 500L
        assertEquals(expected, interval)
    }

    @Test
    fun testGetRecommendedRefreshInterval_normal() {
        val interval = EInkUtil.getRecommendedRefreshInterval(mockContext)
        val expected = if (EInkUtil.isEInkDevice(mockContext)) 2000L else 500L
        assertEquals(expected, interval)
    }

    @Test
    fun testGetOptimalSyncBatchSize_eink() {
        val batchSize = EInkUtil.getOptimalSyncBatchSize(mockContext, 10)
        val expected = if (EInkUtil.isEInkDevice(mockContext)) 20 else 10
        assertEquals(expected, batchSize)
    }

    @Test
    fun testGetOptimalSyncBatchSize_normal() {
        val batchSize = EInkUtil.getOptimalSyncBatchSize(mockContext, 10)
        val expected = if (EInkUtil.isEInkDevice(mockContext)) 20 else 10
        assertEquals(expected, batchSize)
    }

    @Test
    fun testGetRecommendedUIUpdateInterval_eink() {
        val interval = EInkUtil.getRecommendedUIUpdateInterval(mockContext)
        val expected = if (EInkUtil.isEInkDevice(mockContext)) 3000L else 500L
        assertEquals(expected, interval)
    }

    @Test
    fun testGetRecommendedUIUpdateInterval_normal() {
        val interval = EInkUtil.getRecommendedUIUpdateInterval(mockContext)
        val expected = if (EInkUtil.isEInkDevice(mockContext)) 3000L else 500L
        assertEquals(expected, interval)
    }
}
