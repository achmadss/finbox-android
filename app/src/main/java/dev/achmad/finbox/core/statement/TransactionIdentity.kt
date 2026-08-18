package dev.achmad.finbox.core.statement

/**
 * A stable identity that does not change when the parser version changes.
 *
 * Keyed on the Gmail message, not the thread: a thread can carry unrelated mail
 * and genuinely different transactions, so collapsing one loses money. Two
 * messages reporting the same transaction are caught later, by the provider
 * reference — see [dev.achmad.data.repository.TransactionRepository.upsertAll].
 */
internal fun transactionId(
    accountId: String,
    messageId: String,
    sourceId: Long,
    index: Int,
): String = "$accountId:message:$messageId:$sourceId:$index"

internal fun String?.normalizedThreadId(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }
