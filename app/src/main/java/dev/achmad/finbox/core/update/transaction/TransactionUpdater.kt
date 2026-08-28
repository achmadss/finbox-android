package dev.achmad.finbox.core.update.transaction

import dev.achmad.data.model.StoredEmail
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionDirection
import dev.achmad.data.model.normalizedThreadId
import dev.achmad.data.repository.AccountExtensionRepository
import dev.achmad.data.repository.AccountRepository
import dev.achmad.data.repository.EmailRepository
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.extension.Extension
import dev.achmad.finbox.core.gmail.GmailApi
import dev.achmad.finbox.core.gmail.combineExtensionQueries
import dev.achmad.finbox.core.gmail.model.MessageRef
import dev.achmad.finbox.util.network.HttpException
import dev.achmad.finbox.extension.core.source.email.model.Email
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
 * Keeps the ledger of transactions up to date from Gmail.
 */
class TransactionUpdater(
    /** The enabled extensions, read at update time so a switch takes effect at once. */
    private val extensions: () -> List<Extension>,
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
     * Brings one account's transactions up to date.
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
     * The extensions this account parses with, in the order it wants them tried.
     *
     * An account that has never been configured uses everything installed. Once
     * it has, an extension turned off there is skipped — and skipped without being
     * marked tried, so turning it back on re-reads the mail it missed. An extension
     * installed since then has no assignment yet and goes last rather than
     * being ignored.
     */
    private suspend fun extensionsFor(account: EmailAccount): List<Extension> {
        val installed = extensions()
        val assignments = accountExtensionRepository.forAccount(account.id).first()
        if (assignments.isEmpty()) return installed

        val position = assignments.associate { it.extensionId to it.position }
        val disabled = assignments.filterNot { it.enabled }.mapTo(mutableSetOf()) { it.extensionId }
        return installed
            .filterNot { it.id in disabled }
            .sortedBy { position[it.id] ?: Int.MAX_VALUE }
    }

    /**
     * The Gmail search for this account: what its extensions ask for, plus any
     * narrowing set on the account itself.
     *
     * An extension that names no sender wants everything, and then nothing can be
     * excluded — filtering the list would silently skip that extension's mail.
     */
    private suspend fun narrowFor(account: EmailAccount): String? {
        val fromSources = combineExtensionQueries(
            extensionsFor(account).mapNotNull { it.email?.query?.value },
        ) ?: return account.syncQuery
        return listOfNotNull(account.syncQuery?.takeIf { it.isNotBlank() }, fromSources)
            .joinToString(" ")
    }

    /**
     * Imports the window a date slice at a time, newest first, recording how far
     * back it got after each one, so a stopped run resumes where it left off.
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
            // A slice at the cap may have been truncated; Gmail returns the
            // newest first, so halve it and retry rather than skip what did
            // not fit.
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
                // A message can appear in several records; the map keeps it at
                // one fetch.
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
     * History reports everything that arrived, and one `messages.list` is far
     * cheaper than fetching a message to find out what it is.
     */
    private suspend fun narrow(account: EmailAccount, candidates: List<MessageRef>): List<MessageRef> {
        val syncQuery = narrowFor(account)?.takeIf { it.isNotBlank() } ?: return candidates
        if (candidates.isEmpty()) return candidates

        // Only as far back as the last update: listing pages of old mail costs quota.
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
        // A truncated list would exclude mail that does match, so only trust it
        // when it is complete.
        if (allowed.size >= NARROW_CAP) return candidates

        val ids = allowed.mapTo(HashSet()) { it.id }
        return candidates.filter { it.id in ids }
    }

    /**
     * Downloads, parses and writes a batch of messages at a time.
     *
     * A thread can hold unrelated mail, so a second message in a known thread
     * may well be a second transaction. Duplicates are settled after parsing,
     * on the provider reference.
     */
    private suspend fun ingest(
        account: EmailAccount,
        messageRefs: List<MessageRef>,
        after: Long?,
        before: Long?,
        onBatchParsed: suspend (Int) -> Unit = {},
    ): Counts {
        val extensions = extensionsFor(account)
        val stored = emailRepository.forAccount(account.id).associateBy { it.messageId }

        val todo = messageRefs.distinctBy { it.id }.filter { ref ->
            val email = stored[ref.id] ?: return@filter true
            !email.parsed && extensions.any { it.id !in email.triedExtensionIds }
        }
        if (todo.isEmpty()) return Counts(0)

        var parsed = 0
        // Gmail is rate limited, so a batch downloads a few at a time.
        val gate = Semaphore(MAX_PARALLEL_FETCHES)
        for (batch in todo.chunked(BATCH_SIZE)) {
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
                            // Downloaded anyway, so keep the body: later re-reads
                            // do it for free.
                            val body = message.body.ifBlank { null }
                            val email = storedEmail?.copy(threadId = threadId, body = body)
                                ?: StoredEmail(
                                    // Gmail's own id, not the RFC Message-ID header:
                                    // this is what history.list reports, so it is
                                    // what dedup has to key on.
                                    messageId = ref.id,
                                    threadId = threadId,
                                    accountId = account.id,
                                    from = message.from,
                                    subject = message.subject,
                                    date = message.date,
                                    body = body,
                                    triedExtensionIds = emptyList(),
                                    parsedByExtensionId = null,
                                    fetchedAt = System.currentTimeMillis(),
                                )
                            parse(email, message, extensions)
                        }
                    }.awaitAll()
                }
            }

            val transactions = results.flatMap { it.transactions }
                .distinctBy { it.id }
            // Transactions first: an id derives from the email, so a re-run
            // overwrites the same reference rather than duplicating it.
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
     * Re-reads the mail the installed extensions haven't all tried — what a newly
     * installed or updated extension needs. Nearly free: bodies are parsed in
     * place; only mail fetched before bodies were kept costs a 20-unit
     * download, which backfills the body once per email ever.
     *
     * @return how many transactions were written.
     */
    suspend fun parseUnparsed(onProgress: suspend (Progress) -> Unit = {}): Int {
        if (extensions().isEmpty()) return 0

        var parsed = 0
        for ((accountId, emails) in emailRepository.unparsed().groupBy { it.accountId }) {
            val account = accountRepository.getById(accountId) ?: continue
            val extensions = extensionsFor(account)
            val pending = emails.filter { email ->
                extensions.any { it.id !in email.triedExtensionIds }
            }
            if (pending.isEmpty()) continue
            parsed += lockFor(accountId).withLock {
                reread(account, pending, extensions, force = false, onProgress)
            }
        }
        return parsed
    }

    /**
     * Re-reads mail one of [extensionIds] already claimed.
     *
     * Switching a transaction method back on needs this: those emails are parsed,
     * so nothing above would look at them again.
     *
     * @return how many transactions were written.
     */
    suspend fun reparseExtensions(
        extensionIds: Set<String>,
        onProgress: suspend (Progress) -> Unit = {},
    ): Int {
        if (extensionIds.isEmpty()) return 0

        var parsed = 0
        for ((accountId, emails) in emailRepository.parsedBy(extensionIds).groupBy { it.accountId }) {
            val account = accountRepository.getById(accountId) ?: continue
            val extensions = extensionsFor(account).filter { it.id in extensionIds }
            if (extensions.isEmpty()) continue

            val (stored, bodiless) = emails.partition { !it.body.isNullOrBlank() }
            parsed += lockFor(accountId).withLock {
                // No thread filter and forced past triedExtensionIds: the extension that
                // claimed these emails is the one meant to read them again.
                parseStored(account, stored, extensions, force = true, onProgress) +
                    releaseBodiless(bodiless, extensionIds)
            }
        }
        return parsed
    }

    /**
     * Hands mail with no stored body back to [parseUnparsed] — pre-body-storage
     * emails a re-parse can't read from here. Clearing the claims puts them in
     * front of the fetching path again; the download stores the body, so each
     * costs one fetch, once.
     *
     * @return 0 — nothing is parsed here, the next refresh does it.
     */
    private suspend fun releaseBodiless(emails: List<StoredEmail>, extensionIds: Set<String>): Int {
        if (emails.isEmpty()) return 0
        emailRepository.updateAll(
            emails.map {
                it.copy(
                    triedExtensionIds = it.triedExtensionIds - extensionIds,
                    parsedByExtensionId = null,
                )
            },
        )
        return 0
    }

    /** Parses what has a stored body and downloads the rest. */
    private suspend fun reread(
        account: EmailAccount,
        emails: List<StoredEmail>,
        extensions: List<Extension>,
        force: Boolean,
        onProgress: suspend (Progress) -> Unit,
    ): Int {
        val (stored, missing) = emails.partition { !it.body.isNullOrBlank() }
        return parseStored(account, stored, extensions, force, onProgress) +
            ingest(
                account,
                missing.sortedByDescending { it.date }
                    .map { MessageRef(it.messageId, it.threadId) },
                after = null,
                before = null,
                onBatchParsed = { imported -> onProgress(Progress(account.id, imported)) },
            ).parsed
    }

    /**
     * Parses stored mail from the body stored with it — no Gmail, no quota.
     *
     * Batched like a download is: parsing a mailbox of html is still work.
     */
    private suspend fun parseStored(
        account: EmailAccount,
        emails: List<StoredEmail>,
        extensions: List<Extension>,
        force: Boolean,
        onProgress: suspend (Progress) -> Unit,
    ): Int {
        if (emails.isEmpty()) return 0

        var parsed = 0
        for (batch in emails.chunked(BATCH_SIZE)) {
            currentCoroutineContext().ensureActive()
            val results = withContext(Dispatchers.Default) {
                coroutineScope {
                    batch.map { email ->
                        async { parse(email, email.asExtensionEmail(), extensions, force) }
                    }.awaitAll()
                }
            }
            val transactions = results.flatMap { it.transactions }.distinctBy { it.id }
            transactionRepository.upsertAll(transactions)
            emailRepository.updateAll(results.map { it.email })
            parsed += transactions.size
            onProgress(Progress(account.id, transactions.size))
        }
        return parsed
    }

    /** A stored email as an extension sees one. */
    private fun StoredEmail.asExtensionEmail() = Email(
        messageId = messageId,
        threadId = threadId.orEmpty(),
        subject = subject,
        from = from,
        date = date,
        body = body.orEmpty(),
    )

    private class Parsed(val email: StoredEmail, val transactions: List<Transaction>)

    /**
     * Runs [message] past the extensions that haven't tried it, first claim wins.
     *
     * [force] runs it past all of them regardless: a method switched back on has
     * to reach the extension that already claimed this email.
     */
    private suspend fun parse(
        email: StoredEmail,
        message: Email,
        extensions: List<Extension>,
        force: Boolean = false,
    ): Parsed {
        val candidates = if (force) extensions else extensions.filter { it.id !in email.triedExtensionIds }
        val tried = (email.triedExtensionIds + candidates.map { it.id }).distinct()
        val now = System.currentTimeMillis()

        for (extension in candidates) {
            // An extension that reads something other than mail has nothing to
            // say about this, and is not a failure.
            val source = extension.email ?: continue
            // Empty is how an extension disowns an email.
            val parsed = runCatching { source.parse(message) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: continue

            val transactions = parsed.mapIndexed { index, transaction ->
                Transaction(
                    accountId = email.accountId,
                    extensionId = extension.id,
                    emailMessageId = email.messageId,
                    index = index,
                    threadId = email.threadId,
                    reference = transaction.reference?.trim()?.takeIf { it.isNotEmpty() },
                    date = transaction.date,
                    amount = transaction.amount,
                    currency = transaction.currency,
                    // By name, not mapped: an extension built against a later API
                    // could name a direction this build has never heard of, and
                    // an unsigned row beats a crash.
                    direction = runCatching {
                        TransactionDirection.valueOf(transaction.direction.name)
                    }.getOrNull(),
                    // Import never classifies: a row lands uncategorized and the
                    // classify pass picks it up later, so a classifier that is
                    // unavailable, slow or wrong can never fail an import.
                    categoryName = null,
                    categorySource = null,
                    description = transaction.description,
                    merchant = transaction.merchant,
                    createdAt = now,
                    updatedAt = now,
                    editedAt = null,
                    deleted = false,
                )
            }
            return Parsed(
                email.copy(triedExtensionIds = tried, parsedByExtensionId = extension.id),
                transactions,
            )
        }
        // Remember who looked, so only a new or updated extension tries again.
        return Parsed(email.copy(triedExtensionIds = tried), emptyList())
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
         * A mailbox younger than that still pays one empty list call per
         * 30-day slice down to here.
         */
        const val GMAIL_EPOCH = 1_072_915_200_000L // 2004-01-01

        /** Ids the refresh filter will hold; past this it stops being trusted. */
        const val NARROW_CAP = 2_000

    }
}
