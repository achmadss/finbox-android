package dev.achmad.data.model

/**
 * One filing question and every transaction that asks it, in file order.
 *
 * The grouping itself lives in [dev.achmad.data.repository.TransactionRepository.fileableGroups]
 * — a signature here always comes with its rows, and rows come from a
 * repository query, so the pure function was removed rather than kept as a
 * duplicate of the query.
 */
data class SignatureGroup(
    val signature: Signature,
    /**
     * The open rows of this group — written but not yet categorized. Never the
     * rows somebody already answered: filing must not touch a decision.
     */
    val rows: List<Transaction>,
) {
    val rowCount: Int get() = rows.size

    val title: String get() = signature.merchant ?: ""
}
