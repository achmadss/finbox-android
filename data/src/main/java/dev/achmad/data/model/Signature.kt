package dev.achmad.data.model

/**
 * The part of a transaction a classifier actually sees.
 *
 * Forty rows with identical input are one classification problem presented forty times. If
 * two transactions produce identical input, classifying them separately cannot
 * produce a better answer, only a more expensive and less consistent one — so
 * this, not the transaction, is the unit of classification and the key of the
 * cache.
 *
 * **The key has to be exactly the input.** Anything sent to a classifier that
 * is not in here means the cache keyed on less than the answer was conditioned
 * on, and the cache is then wrong rather than merely cold.
 *
 * `amount` is left out for the same reason: raw amounts are near-unique per
 * row, so including one collapses every group back to a single member and loses
 * the entire point. If amount ever turns out to matter, bucket it coarsely and
 * put the bucket in here — never the value.
 */
data class Signature(
    val merchant: String?,
    val description: String?,
    val direction: TransactionDirection?,
) {
    /**
     * Whether there is anything here at all to send.
     *
     * Deliberately arithmetic rather than judgement. Whether a name is
     * *informative* — whether it says what the money was for — is a question
     * about the world, and the app is the wrong place to answer it: every
     * answer it could hold would be a fact about some particular provider
     * hard-coded somewhere it does not belong. That reading is left to the
     * classifier, which may report back that the text does not say.
     *
     * All this rules out is a signature with no text whatsoever, which there is
     * no point spending a call on.
     */
    val isComplete: Boolean
        get() = merchant != null || description != null
}

/**
 * Trims, collapses runs of whitespace, and uppercases; blank becomes null.
 *
 * One function so that the cache key, the SQL and whatever gets sent to a
 * classifier cannot drift apart. Two rows differing only in spacing are the
 * same row as far as classification goes.
 */
fun normalizeForSignature(value: String?): String? =
    value?.trim()?.replace(WHITESPACE, " ")?.uppercase()?.takeIf { it.isNotEmpty() }

private val WHITESPACE = Regex("\\s+")

/** This transaction's [Signature], normalized. */
fun Transaction.signature() = Signature(
    merchant = normalizeForSignature(merchant),
    description = normalizeForSignature(description),
    direction = direction,
)
