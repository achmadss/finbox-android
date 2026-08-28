package dev.achmad.finbox.features.transaction

import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionDirection
import dev.achmad.finbox.features.transaction.list.TransactionFilter
import dev.achmad.finbox.features.transaction.list.TransactionSort
import dev.achmad.finbox.features.transaction.list.monthRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class TransactionFilterTest {

    private fun transaction(
        id: String,
        direction: TransactionDirection = TransactionDirection.OUTGOING,
        amount: Long? = 1_000,
        date: Long = 0,
        accountId: String = "account",
        parserId: Long = 1,
        description: String? = null,
    ) = Transaction(
        accountId = accountId,
        parserId = parserId,
        emailMessageId = "m$id",
        index = 0,
        threadId = null,
        reference = null,
        date = date,
        amount = amount,
        currency = "IDR",
        direction = direction,
        method = null,
        categoryName = null,
        categorySource = null,
        description = description,
        merchant = null,
        createdAt = date,
        updatedAt = date,
        editedAt = null,
        deleted = false,
    )

    /** The fixture's own short name; the derived id is unreadable here. */
    private val Transaction.label: String get() = emailMessageId.removePrefix("m")

    private val transactions = listOf(
        transaction("a", amount = 300, date = 100, description = "Bakso"),
        transaction("b", amount = null, date = 300, description = "Cendol", direction = TransactionDirection.INCOMING),
        transaction("c", amount = 200, date = 200, description = "Ayam", parserId = 2, accountId = "other"),
    )

    @Test
    fun `the default filter keeps everything, newest first`() {
        val filter = TransactionFilter()
        assertFalse(filter.isActive)
        assertEquals(listOf("b", "c", "a"), filter.applyTo(transactions).map { it.label })
    }

    @Test
    fun `an empty set means no restriction, a populated one restricts`() {
        assertEquals(3, TransactionFilter(directions = emptySet()).applyTo(transactions).size)
        assertEquals(
            listOf("b"),
            TransactionFilter(directions = setOf(TransactionDirection.INCOMING)).applyTo(transactions).map { it.label },
        )
        assertEquals(
            listOf("c"),
            TransactionFilter(parserIds = setOf(2)).applyTo(transactions).map { it.label },
        )
        assertEquals(
            listOf("c"),
            TransactionFilter(accountIds = setOf("other")).applyTo(transactions).map { it.label },
        )
    }

    @Test
    fun `the restrictions stack instead of widening each other`() {
        val filter = TransactionFilter(
            directions = setOf(TransactionDirection.OUTGOING),
            accountIds = setOf("other"),
        )
        assertEquals(listOf("c"), filter.applyTo(transactions).map { it.label })
    }

    @Test
    fun `sorting by amount treats a missing amount as zero`() {
        val ascending = TransactionFilter(sort = TransactionSort.AMOUNT, descending = false)
        assertEquals(listOf("b", "c", "a"), ascending.applyTo(transactions).map { it.label })
        assertEquals(
            listOf("a", "c", "b"),
            ascending.copy(descending = true).applyTo(transactions).map { it.label },
        )
    }

    @Test
    fun `a non-default sort or direction counts as an active filter`() {
        assertTrue(TransactionFilter(sort = TransactionSort.AMOUNT).isActive)
        assertTrue(TransactionFilter(descending = false).isActive)
        assertTrue(TransactionFilter(accountIds = setOf("account")).isActive)
    }

    @Test
    fun `the month range spans the data, gaps included, and always reaches now`() {
        val now = YearMonth.of(2026, 8)
        assertEquals(listOf(now), monthRange(emptySet(), now, now))
        assertEquals(
            listOf(YearMonth.of(2026, 5), YearMonth.of(2026, 6), YearMonth.of(2026, 7), now),
            monthRange(setOf(YearMonth.of(2026, 5), YearMonth.of(2026, 7)), now, now),
        )
        // A month picked outside the data is still somewhere the pager can sit.
        assertEquals(
            listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10)),
            monthRange(emptySet(), YearMonth.of(2026, 9), YearMonth.of(2026, 10)),
        )
    }
}
