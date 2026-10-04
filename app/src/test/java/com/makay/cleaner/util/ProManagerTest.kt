package com.makay.cleaner.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProManagerTest {

    @Test
    fun testProGatingDisabledByDefault() {
        // Since PRO_GATING_ENABLED is false, all features are free and unlocked
        assertFalse(ProManager.PRO_GATING_ENABLED)
        assertFalse(ProManager.isProUiVisible())
    }
}
