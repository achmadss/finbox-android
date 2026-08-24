package dev.achmad.data.model

/** How a run arrived at one row's category. */
enum class ClassificationOrigin {
    /** Sent to the model in a batch. This is the one that cost something. */
    ASKED,

    /** Answered already, by the user or by an earlier run, and reused for free. */
    CACHED,

    /** Never sent: the receipt carried no merchant, note or method to send. */
    NO_INPUT,
    ;

    companion object {
        fun fromStringOrNull(value: String?): ClassificationOrigin? =
            value?.let { name -> entries.firstOrNull { it.name == name } }
    }
}

/**
 * One decision a run made, kept as it was made.
 *
 * The text is copied, not referenced. Re-parsing a mailbox afterwards changes
 * what a transaction says, and a record of a decision that silently updates to
 * the new evidence cannot answer the question it exists for: what did the model
 * see when it said that.
 */
data class ClassificationResult(
    val runId: Long,
    val transactionId: String,
    val merchant: String?,
    val description: String?,
    val method: String?,
    val direction: TransactionDirection?,
    val amount: Long?,
    val date: Long?,
    val category: TransactionCategory?,
    /** The name as stored, so a category this build dropped still shows. */
    val categoryName: String,
    val origin: ClassificationOrigin,
)
