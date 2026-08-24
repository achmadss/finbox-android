package dev.achmad.data.model

/** Which rows a classify pass was pointed at. */
enum class ClassificationScope {
    /** Everything nothing has decided yet. The only one that runs unprompted. */
    UNCATEGORIZED,
    SELECTION,
    ALL,
    ;

    companion object {
        fun fromStringOrNull(value: String?): ClassificationScope? =
            value?.let { name -> entries.firstOrNull { it.name == name } }
    }
}

enum class ClassificationStatus {
    RUNNING,
    DONE,
    FAILED,

    /** Stopped by the user, or by the process dying under it. */
    CANCELLED,
    ;

    companion object {
        fun fromStringOrNull(value: String?): ClassificationStatus? =
            value?.let { name -> entries.firstOrNull { it.name == name } }
    }
}

/**
 * One classify pass, as it happened.
 *
 * Kept because the alternative is asking someone to trust a background job that
 * spends their money and silently rewrites their ledger. Counts are in
 * signatures where the classifier works in signatures and in transactions where
 * the user does, because conflating the two is what makes "17 requests
 * categorized 387 transactions" sound impossible.
 */
data class ClassificationRun(
    val id: Long,
    val startedAt: Long,
    val finishedAt: Long?,
    val scope: ClassificationScope,
    val replaceManual: Boolean,
    val providerName: String?,
    val model: String?,
    val signaturesTotal: Int,
    val signaturesSent: Int,
    val signaturesCached: Int,
    val categorized: Int,
    /** Answered, but the receipt did not say what for. Not a failure. */
    val unknown: Int,
    val requests: Int,
    /** Null where the endpoint reports no usage, which many do not. */
    val promptTokens: Long?,
    val completionTokens: Long?,
    val status: ClassificationStatus,
    val error: String?,
) {
    val totalTokens: Long?
        get() = if (promptTokens == null && completionTokens == null) {
            null
        } else {
            (promptTokens ?: 0) + (completionTokens ?: 0)
        }

    val duration: Long? get() = finishedAt?.let { it - startedAt }

    /** Rows the pass wrote something to, however it answered. */
    val touched: Int get() = categorized + unknown
}
