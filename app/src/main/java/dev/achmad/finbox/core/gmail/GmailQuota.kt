package dev.achmad.finbox.core.gmail

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Paces Gmail calls against the per-user quota.
 *
 * Gmail charges quota units per call, not per byte: `messages.get` costs 20,
 * `messages.list` 5, `history.list` 2, `getProfile` 1, against 6,000 units per
 * user per minute. Fetching one message is therefore twenty times a page of
 * five hundred ids, which is why an import narrows what it lists rather than
 * trimming what it downloads.
 *
 * A token bucket per account refills at the allowance and callers wait their
 * turn, so a long import runs at the limit instead of being cut off at it.
 *
 * ponytail: units are counted, not read back from Gmail — the API doesn't
 * report remaining quota. A 429 still backs off on top of this.
 */
class GmailQuota(
    private val unitsPerMinute: Int = UNITS_PER_MINUTE,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it.milliseconds) },
) {

    private class Bucket(var tokens: Double, var updatedAt: Long) {
        val mutex = Mutex()
    }

    private val buckets = ConcurrentHashMap<String, Bucket>()

    /** Waits until [units] are available for [accountId], then spends them. */
    suspend fun spend(accountId: String, units: Int) {
        val bucket = buckets.getOrPut(accountId) { Bucket(unitsPerMinute.toDouble(), now()) }
        val waitMillis = bucket.mutex.withLock {
            val at = now()
            val perMilli = unitsPerMinute / 60_000.0
            bucket.tokens = minOf(
                unitsPerMinute.toDouble(),
                bucket.tokens + (at - bucket.updatedAt) * perMilli,
            )
            bucket.updatedAt = at
            bucket.tokens -= units
            if (bucket.tokens >= 0) 0L else (-bucket.tokens / perMilli).toLong()
        }
        if (waitMillis > 0) sleep(waitMillis)
    }

    companion object {
        /** Gmail's per-user, per-project allowance. */
        const val UNITS_PER_MINUTE = 6_000

        const val MESSAGES_GET = 20
        const val MESSAGES_LIST = 5
        const val HISTORY_LIST = 2
        const val GET_PROFILE = 1
    }
}
