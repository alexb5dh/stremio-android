package com.stremio.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryIdentityTest {
    @Test
    fun `never synced and logged out resets once`() {
        assertEquals(IdentityChange(reset = true, identify = null), identityChange(null, null))
    }

    @Test
    fun `never synced and logged in resets then identifies`() {
        assertEquals(IdentityChange(reset = true, identify = "a"), identityChange(null, "a"))
    }

    @Test
    fun `logged out to logged in identifies without reset`() {
        assertEquals(IdentityChange(reset = false, identify = "a"), identityChange(LOGGED_OUT_MARKER, "a"))
    }

    @Test
    fun `logout resets`() {
        assertEquals(IdentityChange(reset = true, identify = null), identityChange("a", null))
    }

    @Test
    fun `account switch resets then identifies`() {
        assertEquals(IdentityChange(reset = true, identify = "b"), identityChange("a", "b"))
    }

    @Test
    fun `unchanged identity does nothing`() {
        assertNull(identityChange("a", "a"))
        assertNull(identityChange(LOGGED_OUT_MARKER, null))
    }
}
