package com.makay.cleaner.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** PIN hash policy: biyometrik-only kilidi desteklenmez (AppLockManager PIN zorunlu). */
class AppLockPolicyTest {

    @Test
    fun pinHashStableForLockFallback() {
        val a = PinHash.withSalt("4829", "aa")
        val b = PinHash.withSalt("4829", "aa")
        assertEquals(a, b)
        assertNotEquals(a, PinHash.legacy("4829"))
    }
}
