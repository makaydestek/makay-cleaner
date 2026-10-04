package com.makay.cleaner.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PinHashTest {

    @Test
    fun saltedHashIsStableAndDependsOnSalt() {
        val first = PinHash.withSalt("1234", "abcd")
        assertEquals(first, PinHash.withSalt("1234", "abcd"))
        assertNotEquals(first, PinHash.withSalt("1234", "efgh"))
        assertNotEquals(first, PinHash.withSalt("5678", "abcd"))
        assertEquals(64, first.length)
    }

    @Test
    fun legacyHashIgnoresSalt() {
        val legacy = PinHash.legacy("1234")
        assertEquals(legacy, PinHash.legacy("1234"))
        assertNotEquals(legacy, PinHash.withSalt("1234", "abcd"))
    }
}
