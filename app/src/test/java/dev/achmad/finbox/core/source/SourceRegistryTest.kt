package dev.achmad.finbox.core.source

import dev.achmad.finbox.source.GeneratedSources
import dev.achmad.finbox.source.core.email.EmailSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The registry is assembled by a processor across module boundaries, so what is
 * worth asserting is that the assembly happened — not the contents, which would
 * be this file restating the source classes.
 *
 * The ids are the exception. They are stored on every transaction, so a source
 * dropping out of this list is a ledger that no longer knows what parsed it.
 * They are named here so that losing one has to be deliberate.
 */
class SourceRegistryTest {

    @Test
    fun `every source module is collected`() {
        assertEquals(
            listOf("bni", "bri", "jago", "mandiri"),
            GeneratedSources.all.map { it.id }.sorted(),
        )
    }

    @Test
    fun `ids are unique`() {
        val ids = GeneratedSources.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every source declares a capability the app can drive`() {
        GeneratedSources.all.forEach {
            assertTrue("${it.id} reads nothing the app can ask for", it is EmailSource)
        }
    }

    @Test
    fun `every source is presentable`() {
        GeneratedSources.all.forEach {
            assertTrue(it.name.isNotBlank())
            // A resource id, so anything but zero. The drawable itself is a
            // compile error in that source's module if it is missing.
            assertTrue("${it.id} has no icon", it.icon != 0)
        }
    }
}
