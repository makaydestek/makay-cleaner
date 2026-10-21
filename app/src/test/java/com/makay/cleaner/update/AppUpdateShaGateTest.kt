package com.makay.cleaner.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateShaGateTest {

    @Test
    fun normalizeUpdateSha_rejectsMissingOrInvalid() {
        assertNull(AppUpdateRepository.normalizeUpdateSha(null))
        assertNull(AppUpdateRepository.normalizeUpdateSha(""))
        assertNull(AppUpdateRepository.normalizeUpdateSha("abc"))
        assertNull(AppUpdateRepository.normalizeUpdateSha("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85")) // 63
    }

    @Test
    fun normalizeUpdateSha_acceptsHex64() {
        val sha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(sha, AppUpdateRepository.normalizeUpdateSha("  " + sha.uppercase() + "  "))
    }
}
