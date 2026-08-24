package dev.achmad.finbox.features.transaction

import dev.achmad.data.model.CategorySource
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.model.TransactionDirection
import dev.achmad.finbox.features.transaction.detail.applyTo
import dev.achmad.finbox.features.transaction.detail.toDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionDraftTest {

    private val transaction = Transaction(
        accountId = "a1",
        parserId = 1L,
        emailMessageId = "m1",
        index = 0,
        threadId = "th1",
        reference = "REF-1",
        date = 1_700_000_000_000L,
        amount = 24_000L,
        currency = "IDR",
        direction = TransactionDirection.OUTGOING,
        method = "QRIS",
        categoryName = TransactionCategory.FOOD.name,
        categorySource = null,
        description = "Kopi Kenangan",
        merchant = "Kopi Kenangan",
        createdAt = 1L,
        updatedAt = 2L,
        editedAt = null,
        deleted = false,
    )

    @Test
    fun `editing nothing changes nothing`() {
        assertEquals(transaction, transaction.toDraft().applyTo(transaction))
    }

    @Test
    fun `blank fields clear, and what the form does not offer stays put`() {
        val edited = transaction.toDraft()
            .copy(amount = "", category = null, merchant = " ")
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
    fun `filing it yourself makes the category yours`() {
        val edited = transaction.toDraft()
            .copy(category = TransactionCategory.GROCERIES)
            .applyTo(transaction)

        assertEquals(TransactionCategory.GROCERIES, edited.category)
        assertEquals(CategorySource.USER, edited.categorySource)
    }

    @Test
    fun `editing another field leaves the category and its source alone`() {
        val byModel = transaction.copy(categorySource = CategorySource.AI)

        val edited = byModel.toDraft().copy(merchant = "Kopi Kenangan Sudirman").applyTo(byModel)

        assertEquals(TransactionCategory.FOOD, edited.category)
        assertEquals(CategorySource.AI, edited.categorySource)
    }

    @Test
    fun `filling in what UNKNOWN was missing hands the row back to the next pass`() {
        val unclassifiable = transaction.copy(
            categoryName = TransactionCategory.UNKNOWN.name,
            categorySource = null,
            merchant = null,
            description = null,
        )

        val edited = unclassifiable.toDraft().copy(merchant = "Kopi Kenangan").applyTo(unclassifiable)

        // Null, not a guess: the pass looks at it again now there is something
        // to look at.
        assertNull(edited.category)
        assertNull(edited.categorySource)
    }

    @Test
    fun `an edit a classifier would not read leaves UNKNOWN alone`() {
        val unclassifiable = transaction.copy(
            categoryName = TransactionCategory.UNKNOWN.name,
            categorySource = null,
            merchant = null,
            description = null,
        )

        // The amount is not part of a signature, so nothing a classifier reads
        // has changed and asking again would return the same answer.
        val edited = unclassifiable.toDraft().copy(amount = "500").applyTo(unclassifiable)

        assertEquals(TransactionCategory.UNKNOWN, edited.category)
    }

    @Test
    fun `amount stays unsigned, the direction carries the direction`() {
        val edited = transaction.toDraft()
            .copy(amount = "500", direction = TransactionDirection.OUTGOING)
            .applyTo(transaction)

        assertEquals(500L, edited.amount)
        assertEquals(-500L, edited.signedAmount)
    }
}
