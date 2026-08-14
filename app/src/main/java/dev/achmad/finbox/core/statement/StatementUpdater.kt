package dev.achmad.finbox.core.statement

import dev.achmad.data.model.Email
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionType
import dev.achmad.data.repository.AccountExtensionRepository
import dev.achmad.data.repository.AccountRepository
import dev.achmad.data.repository.EmailRepository
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.core.extension.LoadedSource
import dev.achmad.finbox.core.gmail.GmailApi
import dev.achmad.finbox.core.gmail.combineSourceQueries
import dev.achmad.finbox.core.gmail.model.MessageRef
import dev.achmad.finbox.util.network.HttpException
import dev.achmad.finbox.extension.EmailMessage
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Keeps the statement — the ledger of transactions — up to date from Gmail.
 *
 * An update runs in two halves. Fetching stores what identifies each selected
 * email; parsing hands the ones no parser has claimed to the installed sources,
 * and the first that claims an email turns it into transactions. The two are
 * separate because parsers come and go: an email remembers which sources have
 * already seen it, so installing a parser re-reads the mail it hasn't tried
 * and nothing else.
 *
 * Bodies are never stored, and never all held at once: message ids are
 * collected first, then walked in batches that download, parse, write and drop
 * each batch before starting the next. A few downloads run at a time, since
 * Gmail is rate limited and a mailbox import is thousands of messages.
 *
 * The first update for an account walks the mailbox over the chosen window and
 * records where Gmail's history stood when it started. Every update after that
 * asks Gmail only what changed since then, so a refresh with nothing new costs
 * one `history.list` call.
 */
class StatementUpdater(
    /** The installed sources, read at update time so an install takes effect at once. */
    private val sources: () -> List<LoadedSource>,
    private val accountRepository: AccountRepository,
    private val accountExtensionRepository: AccountExtensionRepository,
    private val emailRepository: EmailRepository,
    private val transactionRepository: TransactionRepository,
    private val gmailApi: GmailApi,
) {

    /** One update at a time per account, so two refreshes can't race the cursor. */
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * @param parsed transactions read out of the mail this update covered
     * @param importing true while the initial import still has window left
     */
    data class Result(
        val accountId: String,
        val parsed: Int,
        val importing: Boolean,
    )

    /** What one completed fetch-and-parse batch added, for a notification or progress line. */
    data class Progress(
        val accountId: String,
        /** Transactions written by the completed batch. */
        val imported: Int,
    )

    /** Updates every enabled account, importing the whole mailbox. */
    suspend fun updateAll(onProgress: suspend (Progress) -> Unit = {}): kotlin.Result<Int> =
        runCatching {
            val accounts = accountRepository.accounts().first().filter { it.enabled }
            var parsed = 0
            for (account in accounts) {
                parsed += update(account, onProgress).getOrThrow().parsed
            }
            parsed
        }

    /** True while any account still has an initial import to finish. */
    suspend fun isImporting(): Boolean =
        accountRepository.all().any { it.enabled && it.lastHistoryId == null }

    /**
     * Brings one account's statement up to date.
     *
     * On failure the cursor is left where it was, so the next attempt retries
     * the same range rather than skipping it.
     */
    suspend fun update(
        account: EmailAccount,
        onProgress: suspend (Progress) -> Unit = {},
    ): kotlin.Result<Result> = lockFor(account.id).withLock {
        runCatching {
            // Re-read: a queued refresh must see what the one before it stored.
            val current = accountRepository.getById(account.id) ?: account
            val cursor = current.lastHistoryId
            val parsed = if (cursor == null) {
                fullFetch(current, onProgress)
            } else {
                try {
                    incrementalUpdate(current, cursor, onProgress)
                } catch (e: HttpException) {
                    // Gmail drops history after about a week; the cursor is gone,
                    // not the account. Rebuild instead of failing the refresh.
                    if (e.code == 404) {
                        fullFetch(
                            current.copy(importCursor = null, importedBackTo = null),
                            onProgress,
                        )
                    } else {
                        throw e
                    }
                }
            }
            Result(current.id, parsed, importing = accountRepository.getById(current.id)?.lastHistoryId == null)
        }
    }

    /** What a batch run wrote. */
    private class Counts(val parsed: Int)

    /**
     * The sources this account parses with, in the order it wants them tried.
     *
     * An account that has never been configured uses everything installed. Once
     * it has, a source turned off there is skipped — and skipped without being
     * marked tried, so turning it back on re-reads the mail it missed. A parser
     * installed since then has no assignment yet and goes last rather than
     * being ignored.
     */
    private suspend fun sourcesFor(account: EmailAccount): List<LoadedSource> {
        val installed = sources()
        val assignments = accountExtensionRepository.forAccount(account.id).first()
        if (assignments.isEmpty()) return installed

        val position = assignments.associate { it.sourceId to it.position }
        val disabled = assignments.filterNot { it.enabled }.mapTo(mutableSetOf()) { it.sourceId }
        return installed
            .filterNot { it.id in disabled }
            .sortedBy { position[it.id] ?: Int.MAX_VALUE }
    }

    /**
     * The Gmail search for this account: what its parsers ask for, plus any
     * narrowing set on the account itself.
     *
     * A source that names no sender wants everything, and then nothing can be
     * excluded — filtering the list would silently skip that parser's mail.
     */
    private suspend fun narrowFor(account: EmailAccount): String? {
        val fromSources = combineSourceQueries(sourcesFor(account).map { it.emailQuery.value })
            ?: return account.syncQuery
        return listOfNotNull(account.syncQuery?.takeIf { it.isNotBlank() }, fromSources)
            .joinToString(" ")
    }

    /**
     * Imports the window a date slice at a time, newest first, recording how far
     * back it got after each one.
     *
     * Slices rather than one list call because an import can outlive the run
     * doing it: whatever a stopped run finished stays finished, and the next one
     * picks up where it left off instead of re-walking the mailbox. Recent mail
     * lands first, so the ledger is useful before the import ends.
     *
     * The history cursor is captured before the first slice and only promoted
     * when the last one lands — until then an update is still an import.
     */
    private suspend fun fullFetch(
        account: EmailAccount,
        onProgress: suspend (Progress) -> Unit,
    ): Int {
        // Read the cursor first: anything arriving during the walk then shows up
        // in the next incremental update instead of falling through the gap.
        // Without it there is nothing to promote at the end, so the import would
        // run in full and then start over — fail instead and retry later.
        val importCursor = account.importCursor
            ?: gmailApi.getProfile(account.id).historyId.takeIf { it.isNotEmpty() }
            ?: error("Gmail reported no history id for ${account.email}")

        val now = System.currentTimeMillis()
        val narrow = narrowFor(account)
        val floor = GMAIL_EPOCH
        var upper = account.importedBackTo ?: now
        var slice = SLICE_MILLIS
        var parsed = 0

        while (upper > floor) {
            currentCoroutineContext().ensureActive()
            val lower = maxOf(floor, upper - slice)

            val refs = gmailApi.listMessages(
                account.id,
                after = lower,
                before = upper,
                narrow = narrow,
                maxMessages = SLICE_CAP,
            )
            // A slice at the cap may have been truncated, and Gmail returns the
            // newest first — so the loss would be the oldest mail in it. Halve
            // the slice and retry rather than skip what didn't fit.
            if (refs.size >= SLICE_CAP && slice > MIN_SLICE_MILLIS) {
                slice /= 2
                continue
            }

            parsed += ingest(
                account,
                refs,
                lower,
                upper,
                onBatchParsed = { imported -> onProgress(Progress(account.id, imported)) },
            ).parsed

            upper = lower
            accountRepository.updateImportProgress(account.id, importCursor, upper, now)
            slice = SLICE_MILLIS
        }

        // The whole window is in: from here on, updates are incremental.
        finish(account, importCursor)
        return parsed
    }

    /** Reads only what changed since [cursor], then advances it. */
    private suspend fun incrementalUpdate(
        account: EmailAccount,
        cursor: String,
        onProgress: suspend (Progress) -> Unit,
    ): Int {
        val changed = linkedMapOf<String, MessageRef>()
        var newestHistoryId: String? = null
        var pageToken: String? = null
        do {
            val page = gmailApi.listHistory(account.id, cursor, pageToken)
            for (record in page.history) {
                // A message can appear in several records — added, then labelled.
                // The set keeps it at one fetch.
                record.messagesAdded.forEach { changed.putIfAbsent(it.message.id, it.message) }
                record.messages.forEach { changed.putIfAbsent(it.id, it) }
            }
            newestHistoryId = page.historyId ?: newestHistoryId
            pageToken = page.nextPageToken
        } while (pageToken != null)

        // Deletions are deliberately not requested: removing the email is inbox
        // cleanup, it does not un-spend the money, so the transaction stays.

        // History records are chronological; reverse them so the same
        // newest-first representative rule applies to refreshes as imports.
        val parsed = ingest(
            account,
            narrow(account, changed.values.toList()).asReversed(),
            after = null,
            before = null,
            onBatchParsed = { imported -> onProgress(Progress(account.id, imported)) },
        ).parsed
        finish(account, newestHistoryId ?: cursor)
        return parsed
    }

    /**
     * Drops the ids the account's own search wouldn't have asked for.
     *
     * History reports everything that arrived, and finding out what a message is
     * costs a 20-unit fetch. One `messages.list` covers up to 500 ids for 5, so
     * as soon as it excludes a single message it has paid for itself.
     */
    private suspend fun narrow(account: EmailAccount, candidates: List<MessageRef>): List<MessageRef> {
        val syncQuery = narrowFor(account)?.takeIf { it.isNotBlank() } ?: return candidates
        if (candidates.isEmpty()) return candidates

        // Only as far back as the last update: this is a refresh, and listing
        // pages of old mail costs 5 units each.
        val since = account.lastSyncAt?.minus(DAY_MILLIS)
        val allowed = try {
            gmailApi.listMessages(
                account.id,
                after = since,
                narrow = syncQuery,
                maxMessages = NARROW_CAP,
            )
        } catch (e: Exception) {
            // Falling back to fetching them all is expensive, never wrong.
            return candidates
        }
        // A truncated list would exclude mail that does match, and this filter
        // decides what never gets read. Only trust it when it's complete.
        if (allowed.size >= NARROW_CAP) return candidates

        val ids = allowed.mapTo(HashSet()) { it.id }
        return candidates.filter { it.id in ids }
    }

    /**
     * Downloads, parses and writes one newest message per Gmail thread at a time.
     *
     * A message already stored and already seen by every installed source costs
     * nothing — it is skipped before it is downloaded. A new message in a thread
     * that already has a transaction is skipped even when its own message id is
     * new, so duplicate notifications do not pay for another body fetch.
     */
    private suspend fun ingest(
        account: EmailAccount,
        messageRefs: List<MessageRef>,
        after: Long?,
        before: Long?,
        onBatchParsed: suspend (Int) -> Unit = {},
    ): Counts {
        val sources = sourcesFor(account)
        val stored = emailRepository.forAccount(account.id).associateBy { it.messageId }

        val todo = messageRefs.filter { ref ->
            val email = stored[ref.id] ?: return@filter true
            !email.parsed && sources.any { it.id !in email.triedSourceIds }
        }
        val representatives = selectNewestPerThread(
            refs = todo,
            existingThreadIds = transactionRepository.threadIds(account.id),
        )
        if (representatives.isEmpty()) return Counts(0)

        var parsed = 0
        // Gmail is rate limited, so a batch downloads a few at a time.
        val gate = Semaphore(MAX_PARALLEL_FETCHES)
        for (batch in representatives.chunked(BATCH_SIZE)) {
            currentCoroutineContext().ensureActive()

            val downloaded = withContext(Dispatchers.IO) {
                coroutineScope {
                    batch.map { ref ->
                        async {
                            gate.withPermit {
                                runCatching { gmailApi.getEmail(account.id, ref.id) }
                                    .getOrNull()
                                    ?.let { ref to it }
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
            }

            // Gmail's after:/before: are whole days in local time, so the query
            // is deliberately wider than the window the user picked.
            val inWindow = downloaded.filter { (_, message) ->
                (after == null || message.date >= after) && (before == null || message.date <= before)
            }

            val results = withContext(Dispatchers.Default) {
                coroutineScope {
                    inWindow.map { (ref, message) ->
                        async {
                            val storedEmail = stored[ref.id]
                            val threadId = message.threadId.normalizedThreadId()
                                ?: ref.threadId.normalizedThreadId()
                                ?: storedEmail?.threadId.normalizedThreadId()
                            val email = storedEmail?.copy(threadId = threadId)
                                ?: Email(
                                    // Gmail's own id, not the RFC Message-ID header:
                                    // this is what history.list reports, so it is
                                    // what dedup has to key on.
                                    messageId = ref.id,
                                    threadId = threadId,
                                    accountId = account.id,
                                    from = message.from,
                                    subject = message.subject,
                                    date = message.date,
                                    triedSourceIds = emptyList(),
                                    parsedBySourceId = null,
                                    fetchedAt = System.currentTimeMillis(),
                                )
                            parse(email, message, sources)
                        }
                    }.awaitAll()
                }
            }

            val transactions = results.flatMap { it.transactions }
                .distinctBy { it.id }
            // Transactions first: an id derives from a provider reference, thread,
            // or email fallback, so a re-run overwrites rather than duplicates.
            transactionRepository.upsertAll(transactions)
            val emails = results.map { it.email }
            emailRepository.insertNew(emails.filter { it.messageId !in stored })
            emailRepository.updateAll(emails.filter { it.messageId in stored })
            val imported = transactions.size
            parsed += imported
            onBatchParsed(imported)
        }
        return Counts(parsed)
    }

    /**
     * Re-reads the mail the installed sources haven't all tried — what a newly
     * installed parser needs. Bodies are downloaded again, in batches.
     *
     * This is the expensive path: bodies aren't stored, so every untried email
     * is a 20-unit fetch, and nothing narrows it — a new parser could want mail
     * from any sender. It is paced by the quota bucket rather than capped.
     *
     * @return how many transactions were written.
     */
    suspend fun parseUnparsed(onProgress: suspend (Progress) -> Unit = {}): Int {
        if (sources().isEmpty()) return 0

        var parsed = 0
        for ((accountId, emails) in emailRepository.unparsed().groupBy { it.accountId }) {
            val account = accountRepository.getById(accountId) ?: continue
            val sources = sourcesFor(account)
            val existingThreads = transactionRepository.threadIds(accountId)
            val pending = emails.filter { email ->
                val threadId = email.threadId.normalizedThreadId()
                (threadId == null || threadId !in existingThreads) &&
                    sources.any { it.id !in email.triedSourceIds }
            }
            if (pending.isEmpty()) continue
            parsed += lockFor(accountId).withLock {
                ingest(
                    account,
                    pending.sortedByDescending { it.date }
                        .map { MessageRef(it.messageId, it.threadId) },
                    after = null,
                    before = null,
                    onBatchParsed = { imported -> onProgress(Progress(account.id, imported)) },
                ).parsed
            }
        }
        return parsed
    }

    private class Parsed(val email: Email, val transactions: List<Transaction>)

    /** Runs [message] past the sources that haven't tried it, first claim wins. */
    private suspend fun parse(
        email: Email,
        message: EmailMessage,
        sources: List<LoadedSource>,
    ): Parsed {
        val untried = sources.filter { it.id !in email.triedSourceIds }
        val tried = email.triedSourceIds + untried.map { it.id }
        val now = System.currentTimeMillis()

        for (source in untried) {
            val claims = runCatching { source.isEmailForProvider(message) }.getOrDefault(false)
            if (!claims) continue
            val parsed = runCatching { source.parseEmail(message) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: continue

            val transactions = parsed.mapIndexed { index, transaction ->
                Transaction(
                    id = transactionId(
                        accountId = email.accountId,
                        provider = source.provider,
                        reference = transaction.reference,
                        threadId = email.threadId,
                        messageId = email.messageId,
                        sourceId = source.id,
                        index = index,
                    ),
                    accountId = email.accountId,
                    sourceId = source.id,
                    emailMessageId = email.messageId,
                    threadId = email.threadId,
                    reference = transaction.reference,
                    date = transaction.date,
                    amount = transaction.amount,
                    currency = transaction.currency,
                    type = transaction.type?.let {
                        runCatching { TransactionType.valueOf(it.name) }.getOrNull()
                    },
                    category = null,
                    description = transaction.description,
                    merchant = transaction.merchant,
                    createdAt = now,
                    updatedAt = now,
                    deleted = false,
                )
            }
            return Parsed(
                email.copy(triedSourceIds = tried, parsedBySourceId = source.id),
                transactions,
            )
        }
        // Nothing claimed it: remember who looked, so only a new or updated
        // parser reads this email again.
        return Parsed(email.copy(triedSourceIds = tried), emptyList())
    }

    /** Advances the cursor. Only reached once the work above succeeded. */
    private suspend fun finish(account: EmailAccount, historyId: String?) {
        accountRepository.updateHistoryId(
            id = account.id,
            historyId = historyId ?: account.lastHistoryId,
            at = System.currentTimeMillis(),
        )
    }

    private fun lockFor(accountId: String): Mutex = locks.getOrPut(accountId) { Mutex() }

    private companion object {
        /** Concurrent Gmail downloads, mirroring what a library update dares. */
        const val MAX_PARALLEL_FETCHES = 5

        /** Messages downloaded, parsed and written before the next batch starts. */
        const val BATCH_SIZE = 25

        const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** How much of the window one list call covers, halved when it overflows. */
        const val SLICE_MILLIS = 30L * DAY_MILLIS

        /** Below this a slice is left as-is rather than split further. */
        const val MIN_SLICE_MILLIS = DAY_MILLIS

        /** Ids one slice may return; more than this and the slice is halved. */
        const val SLICE_CAP = 500

        /**
         * Where an import stops walking back: no mail predates Gmail.
         *
         * ponytail: a mailbox younger than that still pays one empty list call
         * per 30-day slice down to here. Stop after a run of empty slices if an
         * import ever feels slow to start.
         */
        const val GMAIL_EPOCH = 1_072_915_200_000L // 2004-01-01

        /** Ids the refresh filter will hold; past this it stops being trusted. */
        const val NARROW_CAP = 2_000

    }
}
