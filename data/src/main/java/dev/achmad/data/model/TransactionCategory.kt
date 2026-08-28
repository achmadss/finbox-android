package dev.achmad.data.model

/** What the money was for — the app's own vocabulary, never an extension's. */
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
     * Code assigns it when a signature carries no text; the classifier may
     * answer it when the text names no purpose. Deliberately not null and not
     * [OTHER]: null means not processed, so the pass retries it constantly.
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
 * Null means nobody decided — not classified yet, or assigned UNKNOWN by code.
 * There is no DEFAULT; null says it better.
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
