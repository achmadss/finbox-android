package dev.achmad.finbox.features.transaction

import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionType
import dev.achmad.finbox.features.transaction.detail.applyTo
import dev.achmad.finbox.features.transaction.detail.toDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionDraftTest {

    private val transaction = Transaction(
        accountId = "a1",
        sourceId = 1L,
        emailMessageId = "m1",
        index = 0,
        threadId = "th1",
        reference = "REF-1",
        date = 1_700_000_000_000L,
        amount = 24_000L,
        currency = "IDR",
        type = TransactionType.EXPENSE,
        kind = "QRIS",
        category = "Coffee",
        description = "Kopi Kenangan",
        merchant = "Kopi Kenangan",
        createdAt = 1L,
        updatedAt = 2L,
        deleted = false,
    )

    @Test
    fun `editing nothing changes nothing`() {
        assertEquals(transaction, transaction.toDraft().applyTo(transaction))
    }

    @Test
    fun `blank fields clear, and what the form does not offer stays put`() {
        val edited = transaction.toDraft()
            .copy(amount = "", category = "", merchant = " ")
            .applyTo(transaction)

        assertNull(edited.amount)
        assertNull(edited.category)
        assertNull(edited.merchant)
        // Not on the form, so the row keeps them.
        assertEquals(transaction.currency, edited.currency)
        assertEquals(transaction.reference, edited.reference)
        assertEquals(transaction.id, edited.id)
        assertEquals(transaction.accountId, edited.accountId)
        assertEquals(transaction.createdAt, edited.createdAt)
        assertEquals(transaction.updatedAt, edited.updatedAt)
        assertEquals(false, edited.deleted)
    }

    @Test
    fun `amount stays unsigned, the type carries the direction`() {
        val edited = transaction.toDraft()
            .copy(amount = "500", type = TransactionType.EXPENSE)
            .applyTo(transaction)

        assertEquals(500L, edited.amount)
        assertEquals(-500L, edited.signedAmount)
    }
}
