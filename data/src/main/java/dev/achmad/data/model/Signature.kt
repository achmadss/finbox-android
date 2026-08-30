package dev.achmad.data.model

/**
 * What a transaction is filed under: the merchant as the receipt names it, and
 * the way the money moved.
 *
 * Description is deliberately not here. A receipt's note line ("order".)
 * routinely differs between rows of the same shop — same merchant, same money,
 * two descriptions — so including one would split what is one classification
 * question into two, and the filing screen would ask twice. Two rows with the
 * same merchant are the same place, whatever their notes said.
 *
 * Forty rows with identical input are one classification problem presented forty times. If
 * two transactions produce identical input, classifying them separately cannot
 * produce a better answer, only a more expensive and less consistent one — so
 * this, not the transaction, is the unit of classification and the key of the
 * cache.
 *
 * `amount` is left out for the same reason: raw amounts are near-unique per
 * row, so including one collapses every group back to a single member and loses
 * the entire point.
 */
data class Signature(
    val merchant: String?,
    val direction: TransactionDirection?,
) {
    /**
     * Whether there is anything here at all to file other than UNKNOWN.
     *
     * Deliberately arithmetic rather than judgement. Whether a name is
     * *informative* — whether it says what the money was for — is a question
     * about the world, and the app is the wrong place to answer it: every
     * answer it could hold would be a fact about some particular provider
     * hard-coded somewhere it does not belong.
     *
     * All this rules out is a signature with no merchant whatsoever, which
     * cannot be filed and stays UNKNOWN.
     */
    val isComplete: Boolean
        get() = merchant != null
}

/**
 * Trims, collapses runs of whitespace, and uppercases; blank becomes null.
 *
 * One function so that the cache key and whatever gets sent to a classifier
 * cannot drift apart. Two rows differing only in spacing are the same row as
 * far as classification goes.
 */
fun normalizeForSignature(value: String?): String? =
    value?.trim()?.replace(WHITESPACE, " ")?.uppercase()?.takeIf { it.isNotEmpty() }

private val WHITESPACE = Regex("\\s+")

/** This transaction's [Signature], normalized. */
fun Transaction.signature() = Signature(
    merchant = normalizeForSignature(merchant),
    direction = direction,
)
