package dev.achmad.data.model

/**
 * What the money was *for* — the app's own vocabulary, never a parser's.
 *
 * Stored as the name in `transactions.category` and validated on the way back
 * out, so changing this list can only ever cost a reclassification, never
 * corrupt a row into a category that no longer exists.
 */
enum class TransactionCategory {
    INCOME,
    FOOD,
    GROCERIES,
    SHOPPING,
    TRANSPORTATION,
    BILLS,
    HOUSING,
    ENTERTAINMENT,
    HEALTH,
    EDUCATION,
    TRAVEL,
    PERSONAL_CARE,
    FINANCIAL,
    TRANSFER,
    FEES,

    /** Looked at, and genuinely miscellaneous. */
    OTHER,

    /**
     * The receipt does not say what the money was for.
     *
     * Two things arrive here and they mean the same to everything downstream.
     * Code assigns it when a signature carried no text at all. The classifier
     * may also answer it, for a signature that carried text which does not name
     * a purpose — a top up to a wallet, a card rail, a transfer to an account
     * number. Only the classifier can make that call: it is a fact about the
     * world, and an app that knew it would be an app with a bank's name written
     * inside it.
     *
     * Deliberately not null and deliberately not [OTHER]. Null means not
     * processed, so the pass retries it forever. [OTHER] means looked at and
     * genuinely miscellaneous, so folding these in inflates a real category and
     * every insight built on it repeats the lie.
     */
    UNKNOWN,
    ;

    companion object {
        /** The stored string, or null if it names nothing this build knows. */
        fun fromStringOrNull(value: String?): TransactionCategory? =
            value?.let { name -> entries.firstOrNull { it.name == name } }
    }
}

/**
 * Who decided a category.
 *
 * Null — no source at all — is the third state and means nobody decided: not
 * classified yet, or folded to [TransactionCategory.UNKNOWN] by code. There is
 * no `DEFAULT`; a null says it better.
 */
enum class CategorySource {
    USER,
    AI,
    ;

    companion object {
        fun fromStringOrNull(value: String?): CategorySource? =
            value?.let { name -> entries.firstOrNull { it.name == name } }
    }
}
