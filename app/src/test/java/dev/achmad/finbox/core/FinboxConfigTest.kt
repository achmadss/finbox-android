package dev.achmad.finbox.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinboxConfigTest {

    @Test
    fun `the version this app ships is loadable, however the manifest widened it`() {
        assertTrue(FinboxConfig.supportsLibVersion(FinboxConfig.LIB_VERSION))
        // What getFloat() actually hands back out of the binary manifest: 1.3f
        // widens to 1.2000000476837158, which no Double literal here equals.
        assertTrue(FinboxConfig.supportsLibVersion(FinboxConfig.LIB_VERSION.toFloat().toDouble()))
        assertTrue(FinboxConfig.supportsLibVersion(FinboxConfig.MIN_LIB_VERSION.toFloat().toDouble()))
        assertTrue(FinboxConfig.supportsLibVersion(FinboxConfig.MIN_LIB_VERSION))
    }

    @Test
    fun `an extension built for a newer API is refused`() {
        assertFalse(FinboxConfig.supportsLibVersion(FinboxConfig.LIB_VERSION + 0.1))
        assertFalse(FinboxConfig.supportsLibVersion(2.0))
    }

    @Test
    fun `an extension below the floor is refused`() {
        assertFalse(FinboxConfig.supportsLibVersion(FinboxConfig.MIN_LIB_VERSION - 0.1))
        assertFalse(FinboxConfig.supportsLibVersion(1.0))
    }

    @Test
    fun `raising the shipped version alone keeps published extensions loadable`() {
        // The whole point of a floor: this is what an already-installed
        // extension looks like after the app moves on without it.
        assertTrue(FinboxConfig.MIN_LIB_VERSION <= FinboxConfig.LIB_VERSION)
        assertTrue(FinboxConfig.supportsLibVersion(FinboxConfig.MIN_LIB_VERSION))
    }
}
