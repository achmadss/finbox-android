package dev.achmad.finbox.core.statement

import dev.achmad.finbox.core.gmail.model.MessageRef

/** Picks the first, newest Gmail message for each thread. */
internal fun selectNewestPerThread(
    refs: List<MessageRef>,
    existingThreadIds: Set<String>,
): List<MessageRef> {
    val seenThreads = existingThreadIds.toMutableSet()
    val seenMessages = HashSet<String>()
    return refs.filter { ref ->
        if (!seenMessages.add(ref.id)) return@filter false
        val threadId = ref.threadId.normalizedThreadId()
        threadId == null || seenThreads.add(threadId)
    }
}

/** A stable identity that does not change when the parser version changes. */
internal fun transactionId(
    accountId: String,
    provider: String,
    reference: String?,
    threadId: String?,
    messageId: String,
    sourceId: Long,
    index: Int,
): String {
    val normalizedReference = reference?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedThread = threadId.normalizedThreadId()
    return when {
        normalizedReference != null ->
            "$accountId:reference:${provider.trim().lowercase()}:$normalizedReference"
        normalizedThread != null ->
            "$accountId:thread:$normalizedThread:$index"
        else ->
            "$accountId:message:$messageId:$sourceId:$index"
    }
}

internal fun String?.normalizedThreadId(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }
