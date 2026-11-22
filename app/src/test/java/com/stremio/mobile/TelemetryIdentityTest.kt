package com.stremio.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryIdentityTest {
    private val loggedOut = ""

    @Test
    fun `never synced and logged out resets once`() {
        assertEquals(IdentityChange(reset = true, identify = null, marker = loggedOut), identityChange(null, null))
    }

    @Test
    fun `never synced and logged in resets then identifies`() {
        assertEquals(IdentityChange(reset = true, identify = "a", marker = "a"), identityChange(null, "a"))
    }

    @Test
    fun `logged out to logged in identifies without reset`() {
        assertEquals(IdentityChange(reset = false, identify = "a", marker = "a"), identityChange(loggedOut, "a"))
    }

    @Test
    fun `logout resets`() {
        assertEquals(IdentityChange(reset = true, identify = null, marker = loggedOut), identityChange("a", null))
    }

    @Test
    fun `account switch resets then identifies`() {
        assertEquals(IdentityChange(reset = true, identify = "b", marker = "b"), identityChange("a", "b"))
    }

    @Test
    fun `unchanged identity does nothing`() {
        assertNull(identityChange("a", "a"))
        assertNull(identityChange(loggedOut, null))
    }
}
