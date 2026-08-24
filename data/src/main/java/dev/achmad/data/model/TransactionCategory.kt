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
     * Nothing to classify with — the signature carried no merchant and no
     * description.
     *
     * Assigned by code, never chosen by a model, and deliberately not the same
     * as a null category. Null means not processed yet, so the pass retries it;
     * this means the pass looked and the data was not there. Folding the two
     * together would either retry forever or hide the gap, and folding this into
     * [OTHER] would inflate a real category with rows that are not
     * miscellaneous, which every insight built on it would then repeat.
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
