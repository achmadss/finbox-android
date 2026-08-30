package dev.achmad.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.model.CategoryRule
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.model.TransactionDirection
import dev.achmad.data.model.signature
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A rule is the user's own filing, verbatim: this merchant is this category.
 * Exact match only — no substring, no position, no ordering. This is what
 * makes it inspectable and what makes re-filing a correction.
 */
class CategoryRuleRepositoryTest {

    private lateinit var repository: CategoryRuleRepository
    private lateinit var transactions: TransactionRepository

    @Before
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FinboxDatabase.Schema.create(driver)
        val db = FinboxDatabase(driver)
        repository = CategoryRuleRepository(db)
        transactions = TransactionRepository(db)
    }

    private fun rule(
        merchant: String = "SHOPEE - 4471",
        direction: TransactionDirection? = TransactionDirection.OUTGOING,
        category: TransactionCategory = TransactionCategory.SHOPPING,
    ) = CategoryRule(
        id = 0,
        merchant = merchant,
        direction = direction,
        category = category,
        createdAt = 1_700_000_000_000L,
    )

    @Test
    fun `a rule matches its exact merchant and direction`() = runBlocking {
        val rule = rule()
        assertTrue(rule.matches(row(merchant = "SHOPEE - 4471").signature()))
        // Normalized, so spacing and case are the same merchant.
        assertTrue(rule.matches(row(merchant = "  shopee - 4471 ").signature()))
    }

    @Test
    fun `a rule is not a substring match`() = runBlocking {
        val shopee = rule(merchant = "SHOPEE - 4471")

        assertFalse(shopee.matches(row(merchant = "SHOPEEPAY").signature()))
        assertFalse(shopee.matches(row(merchant = "SHOPEE ID").signature()))
        assertFalse(shopee.matches(row(merchant = "SHOPEE - 4471", direction = TransactionDirection.INCOMING).signature()))
    }

    @Test
    fun `declaring replaces, not stacks`() = runBlocking {
        repository.declare("WARKOP BOB", TransactionDirection.OUTGOING, TransactionCategory.FOOD)
        repository.declare("WARKOP BOB", TransactionDirection.OUTGOING, TransactionCategory.GROCERIES)

        val rules = repository.all()
        assertEquals(1, rules.size)
        assertEquals(TransactionCategory.GROCERIES, rules.single().category)
    }

    @Test
    fun `a blank merchant is refused`() = runBlocking {
        var refused = false
        try {
            repository.declare("  ", TransactionDirection.OUTGOING, TransactionCategory.FOOD)
        } catch (e: IllegalArgumentException) {
            refused = true
        }
        assertTrue(refused)
        assertEquals(0, repository.all().size)
    }

    @Test
    fun `applyRules files open rows of the declared merchant and nothing else`() = runBlocking {
        transactions.upsertAll(
            listOf(
                row(index = 0, merchant = "WARKOP BOB"),
                row(index = 1, merchant = "warkop bob", description = "dark roast"),
                row(index = 2, merchant = "WARKOP MAHARANI"),
                row(index = 3, merchant = "WARKOP BOB", direction = TransactionDirection.INCOMING),
            ),
        )
        repository.declare("WARKOP BOB", TransactionDirection.OUTGOING, TransactionCategory.FOOD)

        transactions.applyRules(repository.all())

        val stored = transactions.all()
        // Both spellings of the same warkop: exact merchant after normalization.
        assertEquals(2, stored.count {
            it.merchant.equals("WARKOP BOB", ignoreCase = true) &&
                it.category == TransactionCategory.FOOD
        })
        // Not the other warkop; not the incoming money; nothing else filed.
        assertNull(stored.first { it.merchant == "WARKOP MAHARANI" }.category)
        assertNull(stored.first { it.direction == TransactionDirection.INCOMING }.category)
    }

    @Test
    fun `a decision already on a row is not overruled`() = runBlocking {
        transactions.upsertAll(listOf(row(index = 0, merchant = "WARKOP BOB")))
        repository.declare("WARKOP BOB", TransactionDirection.OUTGOING, TransactionCategory.FOOD)

        // The user filed the row by hand to something else first; the rule
        // loses to that decision.
        transactions.setCategoryByUser(
            listOf(transactions.all().single().id),
            TransactionCategory.GROCERIES,
        )
        transactions.applyRules(repository.all())

        assertEquals(
            TransactionCategory.GROCERIES,
            transactions.all().single().category,
        )
    }

    @Test
    fun `replaceExisting writes the new category on matching rows including decided ones`() = runBlocking {
        transactions.upsertAll(
            listOf(
                row(index = 0, merchant = "TOKOPEDIA"),
                row(index = 1, merchant = "TOKOPEDIA"),
                row(index = 2, merchant = "SHOPEE ID"),
            ),
        )
        // The user had filed the first row by hand — replacement is what
        // "change TOKOPEDIA from Shopping to Groceries" explicitly asks for.
        transactions.setCategoryByUser(
            listOf(transactions.all().sortedBy { it.id }.first().id),
            TransactionCategory.SHOPPING,
        )
        repository.declare("TOKOPEDIA", TransactionDirection.OUTGOING, TransactionCategory.GROCERIES)
        repository.replaceExisting(repository.all().first { it.merchant == "TOKOPEDIA" })

        val stored = transactions.all()
        assertEquals(2, stored.count { it.merchant == "TOKOPEDIA" && it.category == TransactionCategory.GROCERIES })
        assertNull(stored.first { it.merchant == "SHOPEE ID" }.category)
    }

    @Test
    fun `countReplace counts exactly what replaceExisting would write`() = runBlocking {
        transactions.upsertAll(
            listOf(
                row(index = 0, merchant = "TOKOPEDIA"),
                row(index = 1, merchant = "tokopedia"),
                row(index = 2, merchant = "SHOPEE ID"),
                row(index = 3, merchant = "TOKOPEDIA", direction = TransactionDirection.INCOMING),
            ),
        )

        assertEquals(2, repository.countReplace("TOKOPEDIA", TransactionDirection.OUTGOING))
        assertEquals(3, repository.countReplace("TOKOPEDIA", null))
    }

    private fun row(
        index: Int = 0,
        merchant: String? = "SHOPEE - 4471",
        description: String? = null,
        direction: TransactionDirection? = TransactionDirection.OUTGOING,
    ) = dev.achmad.data.model.Transaction(
        accountId = "account",
        sourceId = "test",
        emailMessageId = "message-$index",
        index = index,
        threadId = null,
        reference = null,
        date = null,
        amount = null,
        currency = null,
        direction = direction,
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
