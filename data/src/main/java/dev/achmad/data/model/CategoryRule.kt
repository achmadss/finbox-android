package dev.achmad.data.model

/**
 * One declaration, as the user made it: this merchant (+ direction) is this
 * category, for every row that has or will have that merchant.
 *
 * The rule is created when the user files a group — the filing IS the
 * declaration, no second thought needed. Matching is exact against the
 * normalized merchant, not a substring: the user says "SHOPEE - 4471" and the
 * rule means that one place, not every name containing SHOPEE.
 */
data class CategoryRule(
    val id: Long,
    /** Normalized merchant name — exact match. */
    val merchant: String,
    /** The way the money moved for that merchant. */
    val direction: TransactionDirection?,
    val category: TransactionCategory,
    val createdAt: Long,
)
