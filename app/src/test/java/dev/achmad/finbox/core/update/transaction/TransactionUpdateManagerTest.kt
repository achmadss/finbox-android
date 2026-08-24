package dev.achmad.finbox.core.update.transaction

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionUpdateManagerTest {

    private val parseOnly = setOf(TransactionUpdateWork.PARSE_ONLY_TAG)
    private val full = emptySet<String>()

    @Test
    fun `a full update takes over a running re-read`() {
        assertTrue(supersedes(parseOnly = false, ongoingTags = listOf(parseOnly)))
    }

    @Test
    fun `a full update waits for another full update`() {
        assertFalse(supersedes(parseOnly = false, ongoingTags = listOf(full)))
        assertFalse(supersedes(parseOnly = false, ongoingTags = listOf(parseOnly, full)))
    }

    @Test
    fun `a re-read never takes over anything`() {
        assertFalse(supersedes(parseOnly = true, ongoingTags = listOf(parseOnly)))
        assertFalse(supersedes(parseOnly = true, ongoingTags = listOf(full)))
    }
}
