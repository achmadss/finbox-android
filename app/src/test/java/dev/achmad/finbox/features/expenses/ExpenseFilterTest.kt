package dev.achmad.finbox.features.expenses

import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class ExpenseFilterTest {

    private fun transaction(
        id: String,
        type: TransactionType = TransactionType.EXPENSE,
        amount: Long? = 1_000,
        date: Long = 0,
        accountId: String = "account",
        sourceId: Long = 1,
        description: String? = null,
    ) = Transaction(
        id = id,
        accountId = accountId,
        sourceId = sourceId,
        emailMessageId = "m$id",
        threadId = null,
        reference = null,
        date = date,
        amount = amount,
        currency = "IDR",
        type = type,
        category = null,
        description = description,
        merchant = null,
        createdAt = date,
        updatedAt = date,
        deleted = false,
    )

    private val transactions = listOf(
        transaction("a", amount = 300, date = 100, description = "Bakso"),
        transaction("b", amount = null, date = 300, description = "Cendol", type = TransactionType.INCOME),
        transaction("c", amount = 200, date = 200, description = "Ayam", sourceId = 2, accountId = "other"),
    )

    @Test
    fun `the default filter keeps everything, newest first`() {
        val filter = ExpenseFilter()
        assertFalse(filter.isActive)
        assertEquals(listOf("b", "c", "a"), filter.applyTo(transactions).map { it.id })
    }

    @Test
    fun `an empty set means no restriction, a populated one restricts`() {
        assertEquals(3, ExpenseFilter(types = emptySet()).applyTo(transactions).size)
        assertEquals(
            listOf("b"),
            ExpenseFilter(types = setOf(TransactionType.INCOME)).applyTo(transactions).map { it.id },
        )
        assertEquals(
            listOf("c"),
            ExpenseFilter(sourceIds = setOf(2)).applyTo(transactions).map { it.id },
        )
        assertEquals(
            listOf("c"),
            ExpenseFilter(accountIds = setOf("other")).applyTo(transactions).map { it.id },
        )
    }

    @Test
    fun `the restrictions stack instead of widening each other`() {
        val filter = ExpenseFilter(
            types = setOf(TransactionType.EXPENSE),
            accountIds = setOf("other"),
        )
        assertEquals(listOf("c"), filter.applyTo(transactions).map { it.id })
    }

    @Test
    fun `sorting by amount treats a missing amount as zero`() {
        val ascending = ExpenseFilter(sort = ExpenseSort.AMOUNT, descending = false)
        assertEquals(listOf("b", "c", "a"), ascending.applyTo(transactions).map { it.id })
        assertEquals(
            listOf("a", "c", "b"),
            ascending.copy(descending = true).applyTo(transactions).map { it.id },
        )
    }

    @Test
    fun `a non-default sort or direction counts as an active filter`() {
        assertTrue(ExpenseFilter(sort = ExpenseSort.AMOUNT).isActive)
        assertTrue(ExpenseFilter(descending = false).isActive)
        assertTrue(ExpenseFilter(accountIds = setOf("account")).isActive)
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
