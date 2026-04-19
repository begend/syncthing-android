package com.nutomic.syncthingandroid.webdav.policy

import android.content.Context
import com.nutomic.syncthingandroid.util.EInkUtil
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EInkProfileResolver @Inject constructor() {
    fun resolve(context: Context): EInkProfile {
        val brand = EInkUtil.getEInkBrand(context)
        val explicitBrandMatch = brand in SUPPORTED_EINK_BRANDS
        val isOnyx = EInkUtil.isOnyxBoox(context)
        val supportsPenInput = EInkUtil.supportsPenInput(context)
        val isEInk = explicitBrandMatch || isOnyx

        return resolveProfile(
            brand = brand,
            isEInk = isEInk,
            isOnyx = isOnyx,
            supportsPenInput = supportsPenInput,
        )
    }

    internal fun resolveProfile(
        brand: String,
        isEInk: Boolean,
        isOnyx: Boolean,
        supportsPenInput: Boolean,
    ): EInkProfile {
        if (!isEInk) {
            return EInkProfile.NORMAL
        }

        if (isOnyx) {
            return EInkProfile.ONYX.copy(
                pauseDuringInteraction = true,
                interActionDelayMs = if (supportsPenInput) 500L else EInkProfile.ONYX.interActionDelayMs,
            )
        }

        return when (brand) {
            "hisense" -> EInkProfile.GENERIC_EINK.copy(
                id = "hisense-eink",
                brand = "hisense",
                notificationUpdateIntervalMs = 4_000L,
                interActionDelayMs = 150L,
                preferCharging = false,
                pauseDuringInteraction = false,
            )

            "bigme" -> EInkProfile.GENERIC_EINK.copy(
                id = "bigme-eink",
                brand = "bigme",
                notificationUpdateIntervalMs = 6_000L,
                interActionDelayMs = 350L,
            )

            else -> EInkProfile.GENERIC_EINK.copy(brand = brand)
        }
    }

    companion object {
        private val SUPPORTED_EINK_BRANDS = setOf(
            "onyx",
            "kindle",
            "kobo",
            "pocketbook",
            "tolino",
            "hisense",
            "bigme",
            "remarkable",
        )
    }
}
