package dev.achmad.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.model.CategorySource
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.model.TransactionDirection
import dev.achmad.data.model.signature
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransactionRepositoryTest {

    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FinboxDatabase.Schema.create(driver)
        repository = TransactionRepository(FinboxDatabase(driver))
    }

    @Test
    fun `a re-parse refreshes an untouched row`() = runBlocking {
        repository.upsertAll(listOf(transaction(merchant = "KOPI KENANGAN", amount = 25_000)))
        repository.upsertAll(listOf(transaction(merchant = "Kopi Kenangan", amount = 27_000)))

        val stored = repository.all().single()
        assertEquals(27_000L, stored.amount)
        assertEquals("Kopi Kenangan", stored.merchant)
    }

    @Test
    fun `a re-parse leaves a hand-edited row alone`() = runBlocking {
        repository.upsertAll(listOf(transaction(merchant = "GRABFOOD", amount = 25_000)))
        val parsed = repository.all().single()

        repository.update(parsed.copy(amount = 30_000, merchant = "GrabFood"))

        // The same source reading the same message again, and disagreeing.
        repository.upsertAll(listOf(transaction(merchant = "GRABFOOD", amount = 25_000)))

        val stored = repository.all().single()
        assertEquals(30_000L, stored.amount)
        assertEquals("GrabFood", stored.merchant)
        assertTrue(stored.edited)
    }

    @Test
    fun `setting a category by hand marks the row as edited`() = runBlocking {
        repository.upsertAll(listOf(transaction(merchant = "GRABFOOD")))
        val id = repository.all().single().id

        repository.setCategoryByUser(listOf(id), TransactionCategory.FOOD)

        val stored = repository.all().single()
        assertEquals(TransactionCategory.FOOD, stored.category)
        assertEquals(CategorySource.USER, stored.categorySource)
        assertTrue(stored.edited)
    }

    @Test
    fun `a pass writing a category is not a hand edit but a rule is`() = runBlocking {
        repository.upsertAll(listOf(transaction(merchant = "GRABFOOD")))
        val id = repository.all().single().id

        repository.setCategory(id, TransactionCategory.FOOD, CategorySource.RULE)

        val stored = repository.all().single()
        assertEquals(CategorySource.RULE, stored.categorySource)
        assertNull(stored.editedAt)
    }

    @Test
    fun `filing a selection by hand writes every row in it and nothing else`() = runBlocking {
        repository.upsertAll(
            listOf(
                transaction(index = 0, merchant = "Indomaret"),
                transaction(index = 1, merchant = "Alfamart"),
                transaction(index = 2, merchant = "Kopi Kenangan"),
            ),
        )
        val all = repository.all().sortedBy { it.id }

        repository.setCategoryByUser(all.take(2).map { it.id }, TransactionCategory.GROCERIES)

        val stored = repository.all().sortedBy { it.id }
        assertEquals(TransactionCategory.GROCERIES, stored[0].category)
        assertEquals(TransactionCategory.GROCERIES, stored[1].category)
        assertNull(stored[2].category)
        assertNull(stored[2].editedAt)
    }

    @Test
    fun `matching rows are the ones a classifier would read identically`() = runBlocking {
        repository.upsertAll(
            listOf(
                transaction(index = 0, merchant = "Indomaret", description = "Purchase"),
                transaction(index = 1, merchant = " indomaret ", description = "PURCHASE"),
                // Same merchant, different description: still the same place, so
                // still the same classification question.
                transaction(index = 2, merchant = "Indomaret", description = "Top up"),
                transaction(index = 3, merchant = "Alfamart", description = "Purchase"),
            ),
        )
        val rows = repository.all().sortedBy { it.id }

        val matching = repository.withSignature(rows[0].signature(), excludingId = rows[0].id)

        assertEquals(listOf(rows[1].id, rows[2].id), matching.map { it.id })
    }

    @Test
    fun `UNKNOWN, null and OTHER stay three different things`() = runBlocking {
        repository.upsertAll(
            listOf(
                transaction(index = 0, merchant = "Alfamart"),
                transaction(index = 1, merchant = "Sedekah"),
                transaction(index = 2, merchant = "Biaya lain"),
            ),
        )
        val (untouched, noPurpose, miscellaneous) = repository.all().sortedBy { it.id }

        // Nothing has looked at the first row. The second was looked at and the
        // receipt did not say what the money was for. The third was looked at
        // and genuinely is miscellaneous.
        repository.setCategory(noPurpose.id, TransactionCategory.UNKNOWN, source = null)
        repository.setCategory(miscellaneous.id, TransactionCategory.OTHER, CategorySource.RULE)

        val stored = repository.all().sortedBy { it.id }
        assertNull(stored[0].category)
        assertEquals(TransactionCategory.UNKNOWN, stored[1].category)
        assertEquals(TransactionCategory.OTHER, stored[2].category)
    }

    @Test
    fun `an imported row lands uncategorized`() = runBlocking {
        // What the import path writes: no category, no source. Nothing that
        // runs at import time can slow or fail it with classification work,
        // because no classification runs.
        repository.upsertAll(listOf(transaction(merchant = "Kopi Kenangan")))

        val stored = repository.all().single()
        assertNull(stored.categoryName)
        assertNull(stored.categorySource)
        assertNull(stored.editedAt)
    }

    @Test
    fun `mail nothing recognises leaves the ledger untouched`() = runBlocking {
        repository.upsertAll(listOf(transaction(merchant = "Kopi Kenangan")))

        // How a source disowns an email: it yields nothing, and nothing is
        // written. No error, no placeholder row, no review queue.
        repository.upsertAll(emptyList())

        assertEquals(1, repository.all().size)
    }

    @Test
    fun `filing a group writes every row under it and leaves others alone`() = runBlocking {
        // Forty identical receipts, plus one merchant that is not in the group.
        repository.upsertAll(
            List(40) { i -> transaction(index = i, merchant = "SHOPEE - 4471") } +
                listOf(transaction(index = 0, merchant = "Indomaret", emailMessageId = "other")),
        )

        val group = repository.fileableGroups().first { it.title == "SHOPEE - 4471" }
        assertEquals(40, group.rowCount)
        repository.setCategoryByUser(group.rows.map { it.id }, TransactionCategory.SHOPPING)

        val stored = repository.all()
        val filed = stored.filter { it.merchant == "SHOPEE - 4471" }
        assertEquals(40, filed.size)
        assertTrue(filed.all { it.category == TransactionCategory.SHOPPING })
        assertTrue(filed.all { it.categorySource == CategorySource.USER })
        assertTrue(filed.all { it.edited })
        val other = stored.single { it.merchant == "Indomaret" }
        assertNull(other.category)
        assertNull(other.editedAt)
    }

    @Test
    fun `a group already fully filed is not fileable`() = runBlocking {
        repository.upsertAll(listOf(transaction(index = 0, merchant = "Kopi Kenangan")))
        repository.setCategoryByUser(
            listOf(repository.all().single().id),
            TransactionCategory.FOOD,
        )

        assertEquals(0, repository.fileableGroups().size)
    }

    @Test
    fun `a group filed half by user and half by pass files the rest`() = runBlocking {
        repository.upsertAll(
            listOf(
                transaction(index = 0, merchant = "Kopi Kenangan", emailMessageId = "m0"),
                transaction(index = 0, merchant = "Kopi Kenangan", emailMessageId = "m1"),
                transaction(index = 0, merchant = "Kopi Kenangan", emailMessageId = "m2"),
            ),
        )
        val first = repository.all().sortedBy { it.id }.first()
        repository.setCategoryByUser(listOf(first.id), TransactionCategory.FOOD)

        val group = repository.fileableGroups().single()
        // The user's row is not re-filed; the two it missed are.
        assertEquals(2, group.rowCount)
        repository.setCategoryByUser(group.rows.map { it.id }, TransactionCategory.GROCERIES)

        val found = repository.all()
        assertEquals(1, found.count { it.category == TransactionCategory.FOOD })
        assertEquals(2, found.count { it.category == TransactionCategory.GROCERIES })
    }

    private fun transaction(
        index: Int = 0,
        merchant: String? = "Kopi Kenangan",
        description: String? = "Coffee",
        amount: Long = 25_000,
        emailMessageId: String = "message-$index",
    ) = Transaction(
        accountId = "account",
        sourceId = "test",
        emailMessageId = emailMessageId,
        index = index,
        threadId = null,
        reference = null,
        date = 1_700_000_000_000L,
        amount = amount,
        currency = "IDR",
        direction = TransactionDirection.OUTGOING,
        categoryName = null,
        categorySource = null,
        description = description,
        merchant = merchant,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        editedAt = null,
        deleted = false,
    )
}
