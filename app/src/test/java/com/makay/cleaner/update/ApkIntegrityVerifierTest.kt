package com.makay.cleaner.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkIntegrityVerifierTest {

    @Test
    fun testTrustedGitHubReleaseUrl() {
        // Test default owner "makaydestek" and repo "makay-cleaner"
        assertTrue(ApkIntegrityVerifier.isTrustedGitHubReleaseUrl("https://github.com/makaydestek/makay-cleaner/releases/download/v1.0.54/app-release.apk"))
        assertTrue(ApkIntegrityVerifier.isTrustedGitHubReleaseUrl("https://objects.githubusercontent.com/some/asset"))
        
        assertFalse(ApkIntegrityVerifier.isTrustedGitHubReleaseUrl("http://github.com/makaydestek/makay-cleaner/releases/download/v1.0.54/app-release.apk"))
        assertFalse(ApkIntegrityVerifier.isTrustedGitHubReleaseUrl("https://evil.com/makaydestek/makay-cleaner/releases/download/v1.0.54/app-release.apk"))
        assertFalse(ApkIntegrityVerifier.isTrustedGitHubReleaseUrl("https://github.com/otherowner/makay-cleaner/releases/download/v1.0.54/app-release.apk"))
    }

    @Test
    fun testParseSha256Fingerprint() {
        val content = """
            abc123def456
            e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  MakayCleaner_v1.0.54.apk
            other text
        """.trimIndent()

        val sha = ApkIntegrityVerifier.parseSha256Fingerprint(content, "MakayCleaner_v1.0.54.apk")
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha)
    }
}
