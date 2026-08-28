package dev.achmad.finbox.core.categorization

import android.util.Log
import dev.achmad.data.model.CategorySource
import dev.achmad.data.model.ClassificationOrigin
import dev.achmad.data.model.ClassificationResult
import dev.achmad.data.model.ClassificationScope
import dev.achmad.data.model.ClassificationStatus
import dev.achmad.data.model.Signature
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.model.signature
import dev.achmad.data.repository.ClassificationRunRepository
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.core.llm.LlmProviderStore
import dev.achmad.finbox.core.llm.TransactionClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The classify pass: pick rows, reduce them to signatures, answer as few of
 * those as possible, write the answers back.
 *
 * Never part of an import: a classifier failure must never fail one.
 */
class TransactionCategorizer(
    private val transactions: TransactionRepository,
    private val runs: ClassificationRunRepository,
    private val classifier: TransactionClassifier,
    private val providers: LlmProviderStore,
) {

    /**
     * What a run would cost, for a confirmation that names real numbers.
     *
     * Worth showing because the expected number — one request per transaction —
     * is wrong by an order of magnitude in the cheap direction.
     */
    suspend fun estimate(
        scope: ClassificationScope,
        ids: Set<String> = emptySet(),
        replaceManual: Boolean = false,
    ): Estimate {
        val candidates = candidates(scope, ids, replaceManual)
        val groups = candidates.groupBy { it.signature() }
        val cached = if (replaceManual) emptyMap() else transactions.categoryCache()
        val toAsk = groups.keys.filter { it.isComplete && it !in cached }
        return Estimate(
            transactions = candidates.size,
            signatures = groups.size,
            fromCache = groups.entries.count { !replaceManual && it.key in cached },
            requests = (toAsk.size + TransactionClassifier.BATCH_SIZE - 1) /
                TransactionClassifier.BATCH_SIZE,
        )
    }

    /**
     * Runs a pass, reporting progress in signature groups.
     *
     * Cancellable at every batch boundary; whatever finished before a
     * cancellation stays written.
     */
    suspend fun classify(
        scope: ClassificationScope,
        ids: Set<String> = emptySet(),
        replaceManual: Boolean = false,
        onProgress: suspend (Progress) -> Unit = {},
    ): Long {
        val provider = providers.active()
        val candidates = candidates(scope, ids, replaceManual)
        val groups = candidates.groupBy { it.signature() }

        val runId = runs.start(
            scope = scope,
            replaceManual = replaceManual,
            providerName = provider?.name,
            model = provider?.model,
            signaturesTotal = groups.size,
        )

        try {
            // Nothing to classify with at all: an answer, and a free one.
            val (sendable, empty) = groups.entries.partition { it.key.isComplete }
            var done = 0
            for (entry in empty) {
                write(runId, entry.value, TransactionCategory.UNKNOWN, null, ClassificationOrigin.NO_INPUT)
                runs.addProgress(runId, unknown = entry.value.size)
                done++
            }

            // The user's own answers, and the model's from previous runs, applied
            // for free. Skipped entirely when replacing manual work: the cache
            // prefers USER rows, so consulting it here would feed the user's own
            // categories straight back and the run would do nothing.
            val cache = if (replaceManual) emptyMap() else transactions.categoryCache()
            val remaining = mutableListOf<Map.Entry<Signature, List<Transaction>>>()
            for (entry in sendable) {
                val hit = cache[entry.key]
                if (hit == null) {
                    remaining += entry
                } else {
                    write(runId, entry.value, hit, CategorySource.USER, ClassificationOrigin.CACHED)
                    runs.addProgress(runId, signaturesCached = 1, categorized = entry.value.size)
                    done++
                    onProgress(Progress(done, groups.size))
                }
            }

            if (remaining.isNotEmpty() && !classifier.isConfigured()) {
                // Everything the cache could do is done; the rest needs a model
                // nobody has set up. Not a failure — this is the normal state
                // for someone who never turned the AI on.
                runs.finish(runId, ClassificationStatus.DONE)
                return runId
            }

            // Biggest groups first. It costs nothing — the same signatures are
            // sent either way — and it decides how much is done when a run stops
            // early, which is the only time it matters and exactly when it does.
            remaining.sortByDescending { it.value.size }

            for (batch in remaining.chunked(TransactionClassifier.BATCH_SIZE)) {
                currentCoroutineContext().ensureActive()
                val answers = classifier.classify(batch.map { it.key })
                    .getOrElse { error ->
                        // Rate limited, offline, endpoint down. Stop and keep
                        // what landed; the rest are still null and the next pass
                        // picks them up.
                        // The endpoint's own words, not "something went wrong":
                        // this is the only place a user can see why a run they
                        // paid for stopped.
                        Log.w(TAG, "Batch failed, stopping the pass", error)
                        runs.finish(
                            runId,
                            ClassificationStatus.FAILED,
                            error.message?.take(300) ?: error::class.simpleName,
                        )
                        return runId
                    }

                var categorized = 0
                var unknown = 0
                for (entry in batch) {
                    val category = answers.categories[entry.key] ?: continue
                    val source = if (category == TransactionCategory.UNKNOWN) null else CategorySource.AI
                    write(runId, entry.value, category, source, ClassificationOrigin.ASKED)
                    if (category == TransactionCategory.UNKNOWN) {
                        unknown += entry.value.size
                    } else {
                        categorized += entry.value.size
                    }
                }
                // Written as it lands rather than accumulated: a killed app
                // keeps everything it finished.
                runs.addProgress(
                    id = runId,
                    signaturesSent = batch.size,
                    categorized = categorized,
                    unknown = unknown,
                    requests = answers.requests,
                    promptTokens = answers.promptTokens,
                    completionTokens = answers.completionTokens,
                )
                done += batch.size
                onProgress(Progress(done, groups.size))
            }
            runs.finish(runId, ClassificationStatus.DONE)
        } catch (cancellation: CancellationException) {
            runs.finish(runId, ClassificationStatus.CANCELLED)
            throw cancellation
        } catch (error: Throwable) {
            Log.e(TAG, "Classify pass failed", error)
            runs.finish(runId, ClassificationStatus.FAILED, error.message)
        }
        return runId
    }

    /**
     * Files the rows, and keeps a copy of the decision.
     *
     * The record is written from the transaction as it stood when it was
     * classified rather than read back later, so a re-parse afterwards cannot
     * quietly change what the model is on the record as having seen.
     */
    private suspend fun write(
        runId: Long,
        rows: List<Transaction>,
        category: TransactionCategory,
        source: CategorySource?,
        origin: ClassificationOrigin,
    ) {
        rows.forEach { transactions.setCategory(it.id, category, source) }
        runs.recordResults(
            rows.map { row ->
                ClassificationResult(
                    runId = runId,
                    transactionId = row.id,
                    merchant = row.merchant,
                    description = row.description,
                    method = row.method,
                    direction = row.direction,
                    amount = row.amount,
                    date = row.date,
                    category = category,
                    categoryName = category.name,
                    origin = origin,
                )
            },
        )
    }

    /**
     * The rows a run is pointed at.
     *
     * [replaceManual] filters within a scope rather than widening it: off, rows
     * the user filed themselves are left alone whatever the scope says.
     */
    private suspend fun candidates(
        scope: ClassificationScope,
        ids: Set<String>,
        replaceManual: Boolean,
    ): List<Transaction> {
        val all = transactions.all().filterNot { it.deleted }
        val scoped = when (scope) {
            ClassificationScope.UNCATEGORIZED -> all.filter { it.categoryName == null }
            ClassificationScope.SELECTION -> all.filter { it.id in ids }
            ClassificationScope.ALL -> all
        }
        return if (replaceManual) {
            scoped
        } else {
            scoped.filterNot { it.categorySource == CategorySource.USER }
        }
    }

    /** Counted in signature groups: one group can be a hundred rows. */
    data class Progress(val done: Int, val total: Int)

    data class Estimate(
        val transactions: Int,
        val signatures: Int,
        val fromCache: Int,
        val requests: Int,
    )

    private companion object {
        const val TAG = "Categorizer"
    }
}
