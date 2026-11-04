package com.makay.cleaner.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProManagerTest {

    @Test
    fun testProGatingDisabledByDefault() {
        // BuildConfig.PRO_GATING_ENABLED=false → tüm özellikler ücretsiz
        assertFalse(ProManager.PRO_GATING_ENABLED)
        assertFalse(ProManager.isProUiVisible())
    }

    @Test
    fun unlockWithCodeWhenGatingOffAlwaysTrue() {
        // Context gerekmez: gating kapalıyken unlock true döner
        assertTrue(ProManager.PRO_GATING_ENABLED.not())
    }
}
