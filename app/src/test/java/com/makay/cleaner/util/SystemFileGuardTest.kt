package com.makay.cleaner.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemFileGuardTest {

    @Test
    fun systemPathsAreProtected() {
        assertTrue(SystemFileGuard.isProtectedPath("/system/bin/sh"))
        assertTrue(SystemFileGuard.isProtectedPath("/vendor/etc/hosts"))
        assertTrue(SystemFileGuard.isProtectedPath("/data/adb/magisk"))
        assertTrue(SystemFileGuard.isProtectedPath(null))
    }

    @Test
    fun userMediaIsNotProtected() {
        assertFalse(SystemFileGuard.isProtectedPath("/storage/emulated/0/Download/old.apk"))
        assertFalse(SystemFileGuard.isProtectedPath("content://media/external/images/media/1"))
    }

    @Test
    fun oemPackagesAreProtected() {
        assertTrue(SystemFileGuard.isProtectedPackageName("com.android.systemui"))
        assertTrue(SystemFileGuard.isProtectedPackageName("com.miui.securitycenter"))
        assertFalse(SystemFileGuard.isProtectedPackageName("com.whatsapp"))
    }
}
