package dev.achmad.data.repository

import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.model.CategoryRule
import dev.achmad.data.model.CategorySource
import dev.achmad.data.model.Signature
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.model.TransactionDirection
import dev.achmad.data.model.normalizeForSignature
import dev.achmad.data.model.signature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The declarations the user has made by filing groups: merchant -> category.
 *
 * Nothing is auto-declared. A rule exists because the user filed a group and
 * the filing carried it; that is the only source, and it is what makes the
 * rule inspectable — it says the merchant exactly as the receipt said it.
 */
class CategoryRuleRepository(
    private val db: FinboxDatabase,
) {

    suspend fun all(): List<CategoryRule> = withContext(Dispatchers.IO) {
        db.categoryRuleQueries.SELECTAllRules().executeAsList().map { it.toModel() }
    }

    /**
     * The declaration that governs a row, or null.
     *
     * Exact merchant and, when the rule names one, exact direction. The first
     * matching rule is the answer: there is no ordering — a merchant is one
     * merchant, and re-declaring it replaces the old rule.
     */
    suspend fun forRow(row: Transaction): CategoryRule? = withContext(Dispatchers.IO) {
        all().firstOrNull { it.matches(row.signature()) }
    }

    /**
     * Declares (replaces) the rule for [merchant] + [direction].
     *
     * Filing the same merchant again under a different category is a
     * correction, and a correction replaces, never stacks: one merchant has
     * one answer.
     */
    suspend fun declare(
        merchant: String,
        direction: TransactionDirection?,
        category: TransactionCategory,
    ) = withContext(Dispatchers.IO) {
        val normalized = normalizeForSignature(merchant)
            ?: throw IllegalArgumentException("A rule with no merchant is not a rule")
        db.categoryRuleQueries.DELETERuleByMerchant(normalized, direction?.name)
        db.categoryRuleQueries.INSERTRule(
            merchant = normalized,
            direction = direction?.name,
            category = category.name,
            created_at = System.currentTimeMillis(),
        )
        Unit
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.categoryRuleQueries.DELETERule(id)
        Unit
    }

    /**
     * How many rows a replacement of [merchant]+[direction] would write.
     *
     * What the confirmation dialog names, so applying to existing is never a
     * blind "yes".
     */
    suspend fun countReplace(merchant: String, direction: TransactionDirection?): Int =
        withContext(Dispatchers.IO) {
            db.categoryRuleQueries.COUNTRuleReplace(merchant, direction?.name)
                .executeAsOne()
                .toInt()
        }

    /**
     * Writes the rule's category onto every matching row.
     *
     * This is the "also apply to existing" half of an edit. It replaces what
     * was there — the user just said it, that is the point — so unlike
     * [TransactionRepository.applyRules] it does not leave already-decided
     * rows alone.
     */
    suspend fun replaceExisting(rule: CategoryRule) = withContext(Dispatchers.IO) {
        db.categoryRuleQueries.UPDATERuleReplace(
            category = rule.category.name,
            now = System.currentTimeMillis(),
            merchant = rule.merchant,
            direction = rule.direction?.name,
        )
        Unit
    }

    private fun dev.achmad.data.db.Category_rule.toModel() = CategoryRule(
        id = id,
        merchant = merchant,
        direction = direction?.let { runCatching { TransactionDirection.valueOf(it) }.getOrNull() },
        category = TransactionCategory.fromStringOrNull(category)
            ?: error("Row $id carries unknown category '$category'"),
        createdAt = created_at,
    )

    private fun dev.achmad.data.db.Transactions.toTransaction() = Transaction(
        accountId = account_id,
        sourceId = source_id,
        emailMessageId = email_message_id,
        index = dev.achmad.data.model.transactionIndexOf(id),
        threadId = thread_id,
        reference = reference,
        date = date,
        amount = amount,
        currency = currency,
        direction = direction?.let { runCatching { TransactionDirection.valueOf(it) }.getOrNull() },
        categoryName = category,
        categorySource = CategorySource.fromStringOrNull(category_source),
        description = description,
        merchant = merchant,
        createdAt = created_at,
        updatedAt = updated_at,
        editedAt = edited_at,
        deleted = deleted != 0L,
    )
}

/**
 * Exact-match: the rule's merchant is this signature's merchant, and if the
 * rule swallowed a direction, the row's matches.
 *
 * Deliberately no substring. A rule means the place the user looked at, and
 * the normalized merchant is already what both sides compare.
 */
fun CategoryRule.matches(signature: Signature): Boolean =
    merchant == signature.merchant && (direction == null || direction == signature.direction)
