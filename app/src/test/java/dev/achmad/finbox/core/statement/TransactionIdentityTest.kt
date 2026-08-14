package dev.achmad.finbox.core.statement

import dev.achmad.finbox.core.gmail.model.MessageRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TransactionIdentityTest {

    @Test
    fun `selects the newest message once per thread`() {
        val selected = selectNewestPerThread(
            refs = listOf(
                MessageRef(id = "new", threadId = "thread-a"),
                MessageRef(id = "old", threadId = "thread-a"),
                MessageRef(id = "other", threadId = "thread-b"),
                MessageRef(id = "message-only", threadId = null),
                MessageRef(id = "message-only", threadId = null),
            ),
            existingThreadIds = emptySet(),
        )

        assertEquals(listOf("new", "other", "message-only"), selected.map { it.id })
    }

    @Test
    fun `skips threads that already have a transaction`() {
        val selected = selectNewestPerThread(
            refs = listOf(
                MessageRef(id = "duplicate", threadId = "known"),
                MessageRef(id = "new", threadId = "new-thread"),
            ),
            existingThreadIds = setOf("known"),
        )

        assertEquals(listOf("new"), selected.map { it.id })
    }

    @Test
    fun `reference identity is stable across messages and threads`() {
        val first = transactionId(
            accountId = "account",
            provider = "BRI",
            reference = " 123 ",
            threadId = "thread-a",
            messageId = "message-a",
            sourceId = 1L,
            index = 0,
        )
        val second = transactionId(
            accountId = "account",
            provider = "bri",
            reference = "123",
            threadId = "thread-b",
            messageId = "message-b",
            sourceId = 2L,
            index = 0,
        )

        assertEquals(first, second)
    }

    @Test
    fun `thread identity is stable when reference is absent`() {
        val first = transactionId(
            accountId = "account",
            provider = "jago",
            reference = null,
            threadId = "thread-a",
            messageId = "message-a",
            sourceId = 1L,
            index = 0,
        )
        val second = transactionId(
            accountId = "account",
            provider = "jago",
            reference = null,
            threadId = "thread-a",
            messageId = "message-b",
            sourceId = 2L,
            index = 0,
        )

        assertEquals(first, second)
    }

    @Test
    fun `missing thread falls back to the message and parser`() {
        val first = transactionId(
            accountId = "account",
            provider = "jago",
            reference = null,
            threadId = " ",
            messageId = "message-a",
            sourceId = 1L,
            index = 0,
        )
        val second = transactionId(
            accountId = "account",
            provider = "jago",
            reference = null,
            threadId = null,
            messageId = "message-b",
            sourceId = 1L,
            index = 0,
        )

        assertNotEquals(first, second)
    }
}
