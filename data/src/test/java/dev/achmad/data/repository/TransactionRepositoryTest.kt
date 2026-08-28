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

        // The same parser reading the same message again, and disagreeing.
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
    fun `a classify pass writing a category does not mark the row as edited`() = runBlocking {
        repository.upsertAll(listOf(transaction(merchant = "GRABFOOD")))
        val id = repository.all().single().id

        repository.setCategory(id, TransactionCategory.FOOD, CategorySource.AI)

        val stored = repository.all().single()
        assertEquals(CategorySource.AI, stored.categorySource)
        assertNull(stored.editedAt)
    }

    @Test
    fun `the cache answers a signature another row already carries`() = runBlocking {
        repository.upsertAll(
            listOf(
                transaction(index = 0, merchant = "GrabFood", description = "Order"),
                // The same signature once normalized: spacing and case do not
                // make it a different classification problem.
                transaction(index = 1, merchant = "  GRABFOOD ", description = "order"),
            ),
        )
        val (first, second) = repository.all().sortedBy { it.id }
        repository.setCategory(first.id, TransactionCategory.FOOD, CategorySource.AI)

        val cache = repository.categoryCache()

        assertEquals(TransactionCategory.FOOD, cache[second.signature()])
    }

    @Test
    fun `the cache prefers the user's answer over a model's`() = runBlocking {
        repository.upsertAll(
            listOf(
                transaction(index = 0, merchant = "Indomaret"),
                transaction(index = 1, merchant = "Indomaret"),
            ),
        )
        val (byModel, byUser) = repository.all().sortedBy { it.id }
        repository.setCategory(byModel.id, TransactionCategory.SHOPPING, CategorySource.AI)
        repository.setCategoryByUser(listOf(byUser.id), TransactionCategory.GROCERIES)

        val cache = repository.categoryCache()

        assertEquals(TransactionCategory.GROCERIES, cache[byUser.signature()])
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
                // Same merchant, different description: not the same problem to a classifier.
                transaction(index = 2, merchant = "Indomaret", description = "Top up"),
                transaction(index = 3, merchant = "Alfamart", description = "Purchase"),
            ),
        )
        val rows = repository.all().sortedBy { it.id }

        val matching = repository.withSignature(rows[0].signature(), excludingId = rows[0].id)

        assertEquals(listOf(rows[1].id), matching.map { it.id })
    }

    @Test
    fun `an UNKNOWN row never becomes a cached answer`() = runBlocking {
        repository.upsertAll(listOf(transaction(merchant = null, description = null)))
        val id = repository.all().single().id
        repository.setCategory(id, TransactionCategory.UNKNOWN, source = null)

        assertTrue(repository.categoryCache().isEmpty())
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
        repository.setCategory(miscellaneous.id, TransactionCategory.OTHER, CategorySource.AI)

        val stored = repository.all().sortedBy { it.id }
        assertNull(stored[0].category)
        assertEquals(TransactionCategory.UNKNOWN, stored[1].category)
        assertEquals(TransactionCategory.OTHER, stored[2].category)

        // Only OTHER is an answer worth reusing. UNKNOWN and null both mean the
        // question is still open, for different reasons, and collapsing either
        // into the cache would stop the pass ever asking again.
        val cache = repository.categoryCache()
        assertEquals(listOf(TransactionCategory.OTHER), cache.values.toList())
    }

    @Test
    fun `an imported row lands uncategorized and stays available to the classify pass`() = runBlocking {
        // What the import path writes: no category, no source. A classifier that
        // is unavailable, slow or wrong cannot fail this, because it is not run.
        repository.upsertAll(listOf(transaction(merchant = "Kopi Kenangan")))

        val stored = repository.all().single()
        assertNull(stored.categoryName)
        assertNull(stored.categorySource)
        assertNull(stored.editedAt)
    }

    @Test
    fun `mail nothing recognises leaves the ledger untouched`() = runBlocking {
        repository.upsertAll(listOf(transaction(merchant = "Kopi Kenangan")))

        // How a parser disowns an email: it yields nothing, and nothing is
        // written. No error, no placeholder row, no review queue.
        repository.upsertAll(emptyList())

        assertEquals(1, repository.all().size)
    }

    private fun transaction(
        index: Int = 0,
        merchant: String? = "Kopi Kenangan",
        description: String? = "Coffee",
        amount: Long = 25_000,
    ) = Transaction(
        accountId = "account",
        parserId = 1L,
        emailMessageId = "message-$index",
        index = index,
        threadId = null,
        reference = null,
        date = 1_700_000_000_000L,
        amount = amount,
        currency = "IDR",
        direction = TransactionDirection.OUTGOING,
        method = "QRIS",
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
