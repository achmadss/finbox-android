package dev.achmad.finbox.extension

import dev.achmad.finbox.extension.core.source.email.EmailSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The registry is generated, so what is worth asserting is that the generator
 * ran and produced something usable — not the contents, which would be this
 * file restating the annotations.
 *
 * The ids are the exception: they are stored on every transaction, so one
 * disappearing from this list is a ledger that no longer knows who parsed it.
 * They are named here so that renaming one has to be deliberate.
 */
class ExtensionsTest {

    @Test
    fun `every shipped extension is collected`() {
        assertEquals(
            listOf("bni", "bri", "jago", "mandiri"),
            Extensions.all.map { it.id },
        )
    }

    @Test
    fun `ids are unique, and each one resolves`() {
        val ids = Extensions.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertNotNull(it, Extensions.byId(it)) }
    }

    @Test
    fun `an unknown id resolves to nothing rather than throwing`() {
        assertEquals(null, Extensions.byId("nosuchbank"))
    }

    @Test
    fun `every extension declares a capability the app can drive`() {
        Extensions.all.forEach {
            assertTrue("${it.id} has no source the app can use", it.source is EmailSource)
            assertNotNull(it.email)
        }
    }

    @Test
    fun `names are shown to the user, so none is blank`() {
        Extensions.all.forEach { assertTrue(it.name.isNotBlank()) }
    }
}
