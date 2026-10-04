package com.makay.cleaner.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilePurgerTest {

    @Test
    fun otherAppSandboxIsDetected() {
        assertTrue(FilePurger.isOtherAppSandboxPath("/storage/emulated/0/Android/data/com.whatsapp/cache/a"))
        assertTrue(FilePurger.isOtherAppSandboxPath("C:\\Android\\obb\\com.game.app\\main.obb"))
        assertFalse(FilePurger.isOtherAppSandboxPath("/storage/emulated/0/Download/setup.apk"))
        assertFalse(FilePurger.isOtherAppSandboxPath("/storage/emulated/0/DCIM/.thumbnails/1.jpg"))
    }
}
