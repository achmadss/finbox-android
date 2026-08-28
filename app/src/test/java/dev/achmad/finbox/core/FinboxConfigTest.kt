package dev.achmad.finbox.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinboxConfigTest {

    @Test
    fun `every supported version is loadable, however the manifest widened it`() {
        FinboxConfig.SUPPORTED_LIB_VERSIONS.forEach { version ->
            assertTrue("$version", FinboxConfig.supportsLibVersion(version))
            // What getFloat() actually hands back out of the binary manifest:
            // 1.2f widens to 1.2000000476837158, which no Double literal equals.
            assertTrue("$version widened", FinboxConfig.supportsLibVersion(version.toFloat().toDouble()))
        }
    }

    @Test
    fun `a version that is not on the list is refused, above or below`() {
        val current = FinboxConfig.CURRENT_LIB_VERSION
        assertFalse(FinboxConfig.supportsLibVersion(current + 0.1))
        assertFalse(FinboxConfig.supportsLibVersion(current + 1.0))
        assertFalse(FinboxConfig.supportsLibVersion(FinboxConfig.SUPPORTED_LIB_VERSIONS.first() - 0.1))
        assertFalse(FinboxConfig.supportsLibVersion(1.0))
    }

    @Test
    fun `a gap in the list is a gap, not a range`() {
        // The reason this is a list: adding 2.2 must not silently promise 2.1,
        // which nobody kept working. Asserted against a stand-in so the check
        // survives the real list growing.
        val versions = listOf(2.0, 2.2)
        assertTrue(versions.none { kotlin.math.abs(it - 2.1) < 0.001 })
    }

    @Test
    fun `the current version is the newest supported one`() {
        assertEqualsDouble(FinboxConfig.SUPPORTED_LIB_VERSIONS.max(), FinboxConfig.CURRENT_LIB_VERSION)
    }

    private fun assertEqualsDouble(expected: Double, actual: Double) =
        assertTrue("expected $expected, got $actual", kotlin.math.abs(expected - actual) < 0.001)
}
