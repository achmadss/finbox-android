package dev.achmad.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.achmad.data.db.Classification_result
import dev.achmad.data.db.Classification_run
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.model.ClassificationOrigin
import dev.achmad.data.model.ClassificationResult
import dev.achmad.data.model.ClassificationRun
import dev.achmad.data.model.ClassificationScope
import dev.achmad.data.model.ClassificationStatus
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.model.TransactionDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The history of classify passes: what ran, what it cost, what it decided. */
class ClassificationRunRepository(
    private val db: FinboxDatabase,
) {

    fun runs(limit: Long = 50): Flow<List<ClassificationRun>> =
        db.classificationRunQueries.SELECTRuns(limit)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    suspend fun getById(id: Long): ClassificationRun? = withContext(Dispatchers.IO) {
        db.classificationRunQueries.SELECTRunById(id).executeAsOneOrNull()?.toModel()
    }

    /** Every decision one run made, as it was made. */
    fun results(runId: Long): Flow<List<ClassificationResult>> =
        db.classificationResultQueries.SELECTResults(runId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    /**
     * Records what a batch decided.
     *
     * One database transaction for the whole batch: a pass writes a few hundred
     * of these and a round trip each would show up next to the request itself.
     */
    suspend fun recordResults(results: List<ClassificationResult>) = withContext(Dispatchers.IO) {
        db.transaction {
            results.forEach {
                db.classificationResultQueries.INSERTResult(
                    run_id = it.runId,
                    transaction_id = it.transactionId,
                    merchant = it.merchant,
                    description = it.description,
                    method = it.method,
                    direction = it.direction?.name,
                    amount = it.amount,
                    date = it.date,
                    category = it.categoryName,
                    origin = it.origin.name,
                )
            }
        }
    }

    suspend fun start(
        scope: ClassificationScope,
        replaceManual: Boolean,
        providerName: String?,
        model: String?,
        signaturesTotal: Int,
    ): Long = withContext(Dispatchers.IO) {
        db.transactionWithResult {
            db.classificationRunQueries.INSERTRun(
                started_at = System.currentTimeMillis(),
                scope = scope.name,
                replace_manual = if (replaceManual) 1L else 0L,
                provider_name = providerName,
                model = model,
                signatures_total = signaturesTotal.toLong(),
            )
            db.classificationRunQueries.lastRunId().executeAsOne()
        }
    }

    /**
     * Adds a finished batch to the running totals.
     *
     * Written per batch rather than once at the end, so a run that is killed
     * still says what it managed. Everything here is a delta.
     */
    suspend fun addProgress(
        id: Long,
        signaturesSent: Int = 0,
        signaturesCached: Int = 0,
        categorized: Int = 0,
        unknown: Int = 0,
        requests: Int = 0,
        promptTokens: Long = 0,
        completionTokens: Long = 0,
    ) = withContext(Dispatchers.IO) {
        db.classificationRunQueries.UPDATERunProgress(
            signatures_sent = signaturesSent.toLong(),
            signatures_cached = signaturesCached.toLong(),
            categorized = categorized.toLong(),
            unknown = unknown.toLong(),
            requests = requests.toLong(),
            prompt_tokens = promptTokens,
            completion_tokens = completionTokens,
            id = id,
        )
        Unit
    }

    suspend fun finish(
        id: Long,
        status: ClassificationStatus,
        error: String? = null,
    ) = withContext(Dispatchers.IO) {
        db.classificationRunQueries.UPDATERunFinished(
            finished_at = System.currentTimeMillis(),
            status = status.name,
            error = error,
            id = id,
        )
        Unit
    }

    /**
     * Closes off runs the process died under.
     *
     * Nothing is going to finish them, and a history that shows a run still
     * going three days later is worse than one that admits it was killed.
     */
    suspend fun cancelStale() = withContext(Dispatchers.IO) {
        db.classificationRunQueries.UPDATEStaleRuns(System.currentTimeMillis())
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.transaction {
            db.classificationResultQueries.DELETEAllResults()
            db.classificationRunQueries.DELETEAllRuns()
        }
    }

    private fun Classification_result.toModel() = ClassificationResult(
        runId = run_id,
        transactionId = transaction_id,
        merchant = merchant,
        description = description,
        method = method,
        direction = direction?.let { name -> TransactionDirection.entries.firstOrNull { it.name == name } },
        amount = amount,
        date = date,
        category = TransactionCategory.fromStringOrNull(category),
        categoryName = category,
        origin = ClassificationOrigin.fromStringOrNull(origin) ?: ClassificationOrigin.ASKED,
    )

    private fun Classification_run.toModel() = ClassificationRun(
        id = id,
        startedAt = started_at,
        finishedAt = finished_at,
        scope = ClassificationScope.fromStringOrNull(scope) ?: ClassificationScope.UNCATEGORIZED,
        replaceManual = replace_manual != 0L,
        providerName = provider_name,
        model = model,
        signaturesTotal = signatures_total.toInt(),
        signaturesSent = signatures_sent.toInt(),
        signaturesCached = signatures_cached.toInt(),
        categorized = categorized.toInt(),
        unknown = unknown.toInt(),
        requests = requests.toInt(),
        promptTokens = prompt_tokens,
        completionTokens = completion_tokens,
        status = ClassificationStatus.fromStringOrNull(status) ?: ClassificationStatus.CANCELLED,
        error = error,
    )
}
