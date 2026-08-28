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
 * The text is copied, not referenced: re-parsing later changes what a row
 * says, and the record must show what the model saw at the time.
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
