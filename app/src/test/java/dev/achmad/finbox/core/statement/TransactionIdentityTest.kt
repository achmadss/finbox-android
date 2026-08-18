package dev.achmad.finbox.core.statement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TransactionIdentityTest {

    @Test
    fun `identity is stable for the same message, source and index`() {
        val first = transactionId(
            accountId = "account",
            messageId = "message-a",
            sourceId = 1L,
            index = 0,
        )
        val second = transactionId(
            accountId = "account",
            messageId = "message-a",
            sourceId = 1L,
            index = 0,
        )

        assertEquals(first, second)
    }

    @Test
    fun `two messages in one thread keep separate identities`() {
        val first = transactionId(
            accountId = "account",
            messageId = "message-a",
            sourceId = 1L,
            index = 0,
        )
        val second = transactionId(
            accountId = "account",
            messageId = "message-b",
            sourceId = 1L,
            index = 0,
        )

        assertNotEquals(first, second)
    }

    @Test
    fun `each transaction in one message keeps its own identity`() {
        val first = transactionId(
            accountId = "account",
            messageId = "message-a",
            sourceId = 1L,
            index = 0,
        )
        val second = transactionId(
            accountId = "account",
            messageId = "message-a",
            sourceId = 1L,
            index = 1,
        )

        assertNotEquals(first, second)
    }
}
