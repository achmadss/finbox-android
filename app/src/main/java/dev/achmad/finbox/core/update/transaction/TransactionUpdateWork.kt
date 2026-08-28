package dev.achmad.finbox.core.update.transaction

/** The names and keys the transaction update work is addressed by. */
object TransactionUpdateWork {
    const val WORK_NAME = "finbox_transaction_update"

    /** Shared by manual and parse-only requests so they cannot overlap. */
    const val MANUAL_WORK_NAME = "${WORK_NAME}_manual"

    const val REPARSE_WORK_NAME = "finbox_transaction_reparse"

    const val PROGRESS_IMPORTED = "progress_imported"

    /** Skip the Gmail sync and only re-read stored mail. */
    const val PARSE_ONLY = "parse_only"

    /** Parser ids whose already-claimed mail is to be read again. */
    const val REPARSE_PARSERS = "reparse_parsers"

    /** On a request that only re-reads stored mail — what a full update may supersede. */
    const val PARSE_ONLY_TAG = "parse_only_request"
}
