package dev.achmad.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TransactionIdentityTest {

    private fun transaction(messageId: String, index: Int) = Transaction(
        accountId = "account",
        extensionId = 1L,
        emailMessageId = messageId,
        index = index,
        threadId = "thread",
        reference = null,
        date = null,
        amount = null,
        currency = null,
        direction = null,
        method = null,
        categoryName = null,
        categorySource = null,
        description = null,
        merchant = null,
        createdAt = 0L,
        updatedAt = 0L,
        editedAt = null,
        deleted = false,
    )

    @Test
    fun `identity is stable for the same message, extension and index`() {
        assertEquals(transaction("message-a", 0).id, transaction("message-a", 0).id)
    }

    @Test
    fun `two messages in one thread keep separate identities`() {
        assertNotEquals(transaction("message-a", 0).id, transaction("message-b", 0).id)
    }

    @Test
    fun `each transaction in one message keeps its own identity`() {
        assertNotEquals(transaction("message-a", 0).id, transaction("message-a", 1).id)
    }

    @Test
    fun `a stored id gives its index back`() {
        assertEquals(3, transactionIndexOf(transaction("message-a", 3).id))
        // A message id with a colon in it: the index is the last field, so it still reads.
        assertEquals(2, transactionIndexOf(transaction("a:b:c", 2).id))
    }

    @Test
    fun `blank thread ids store as nothing`() {
        assertEquals(null, "  ".normalizedThreadId())
        assertEquals(null, null.normalizedThreadId())
        assertEquals("thread", " thread ".normalizedThreadId())
    }
}
