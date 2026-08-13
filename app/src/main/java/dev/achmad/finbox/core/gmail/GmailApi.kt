package dev.achmad.finbox.core.gmail

import android.util.Base64
import dev.achmad.finbox.core.FinboxConfig
import dev.achmad.finbox.util.network.HttpException
import dev.achmad.finbox.util.network.get
import dev.achmad.finbox.util.network.json
import dev.achmad.finbox.util.network.parseAs
import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.core.gmail.model.HistoryResponse
import dev.achmad.finbox.core.gmail.model.MessageListResponse
import dev.achmad.finbox.core.gmail.model.ProfileResponse
import dev.achmad.finbox.core.gmail.model.MessageRef
import dev.achmad.finbox.core.gmail.model.MessageResponse
import dev.achmad.finbox.core.gmail.model.Payload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.time.Duration.Companion.milliseconds

/** Thin Gmail REST client on top of OkHttp. */
class GmailApi(
    private val client: OkHttpClient,
    private val tokens: GmailTokenManager,
    private val quota: GmailQuota = GmailQuota(),
) {

    /**
     * One page of message ids for [query]. Pass [MessageListResponse.nextPageToken]
     * back as [pageToken] for the next one, or use [listAllMessages].
     */
    suspend fun listMessages(
        accountId: String,
        query: String,
        maxResults: Int = PAGE_SIZE,
        pageToken: String? = null,
    ): MessageListResponse {
        // Not an empty mailbox: an unusable account has to fail the update, or an
        // import would "finish" empty and promote its cursor.
        val token = tokens.getAccessToken(accountId) ?: error("No token")
        val encoded = withContext(Dispatchers.IO) { URLEncoder.encode(query, "UTF-8") }
        val url = "${FinboxConfig.GMAIL_API_BASE}/messages?q=$encoded&maxResults=$maxResults" +
            "&fields=messages/id,nextPageToken" +
            pageToken.orEmpty().let { if (it.isEmpty()) "" else "&pageToken=$it" }
        return getWithRetry(accountId, url, token, GmailQuota.MESSAGES_LIST)
    }

    /**
     * Every message matching [query], paged. An import can legitimately span
     * thousands of emails, so this is capped at [maxMessages] rather than left
     * unbounded — a source with a too-wide query would otherwise walk the
     * entire mailbox.
     */
    suspend fun listAllMessages(
        accountId: String,
        query: String,
        maxMessages: Int = MAX_MESSAGES,
    ): List<MessageRef> {
        val refs = mutableListOf<MessageRef>()
        var pageToken: String? = null
        do {
            val page = listMessages(accountId, query, pageToken = pageToken)
            refs += page.messages
            pageToken = page.nextPageToken
        } while (pageToken != null && refs.size < maxMessages)
        return if (refs.size > maxMessages) refs.take(maxMessages) else refs
    }

    /**
     * The whole message. Quota is charged per call, not per byte, so there is
     * nothing to save by asking for headers first and the body later — that
     * would cost twice.
     */
    suspend fun getMessage(accountId: String, messageId: String): MessageResponse {
        val token = tokens.getAccessToken(accountId) ?: error("No token")
        val url = "${FinboxConfig.GMAIL_API_BASE}/messages/$messageId?format=full" +
            "&fields=id,internalDate,payload"
        return getWithRetry(accountId, url, token, GmailQuota.MESSAGES_GET)
    }

    /** The full message, normalized for extensions. */
    suspend fun getEmail(accountId: String, messageId: String): EmailMessage =
        toEmailMessage(getMessage(accountId, messageId))

    /** The mailbox's current [ProfileResponse.historyId], used to seed the sync cursor. */
    suspend fun getProfile(accountId: String): ProfileResponse {
        val token = tokens.getAccessToken(accountId) ?: error("No token")
        return getWithRetry(
            accountId,
            "${FinboxConfig.GMAIL_API_BASE}/profile",
            token,
            GmailQuota.GET_PROFILE,
        )
    }

    /**
     * One page of changes since [startHistoryId].
     *
     * Throws [HttpException] with code 404 when Gmail has expired that cursor —
     * the caller has to fall back to a full update.
     */
    suspend fun listHistory(
        accountId: String,
        startHistoryId: String,
        pageToken: String? = null,
    ): HistoryResponse {
        val token = tokens.getAccessToken(accountId) ?: error("No token")
        // Only messageAdded: a label moving on a message the ledger already has
        // can't produce a transaction, and every id here costs a 20-unit fetch.
        val url = "${FinboxConfig.GMAIL_API_BASE}/history" +
            "?startHistoryId=$startHistoryId&maxResults=$PAGE_SIZE" +
            "&historyTypes=messageAdded" +
            "&fields=history/messagesAdded/message/id,nextPageToken,historyId" +
            pageToken.orEmpty().let { if (it.isEmpty()) "" else "&pageToken=$it" }
        return getWithRetry(accountId, url, token, GmailQuota.HISTORY_LIST)
    }

    /**
     * GET, paced against the quota, with a 401-triggered token refresh and a
     * backoff for the rate-limit and server errors Gmail asks callers to retry.
     */
    private suspend inline fun <reified T> getWithRetry(
        accountId: String,
        url: String,
        token: String,
        units: Int,
    ): T {
        var attempt = 0
        while (true) {
            quota.spend(accountId, units)
            var response = get(url, token)
            if (response.code == 401) {
                response.close()
                val fresh = tokens.refreshAccessToken(accountId) ?: throw HttpException(401)
                quota.spend(accountId, units)
                response = get(url, fresh)
            }
            if (response.isSuccessful) {
                // Gmail answers 204 with no body when the fields mask leaves nothing to send
                // back — a window with no matching mail. That is an empty result, not an error.
                if (response.code == 204) {
                    response.close()
                    return json.decodeFromString("{}")
                }
                return response.parseAs()
            }

            val code = response.code
            response.close()
            // 403 is also how Gmail reports userRateLimitExceeded.
            val retryable = code == 429 || code == 403 || code >= 500
            if (!retryable || attempt >= MAX_RETRIES) throw HttpException(code)
            delay((RETRY_BASE_MILLIS shl attempt).milliseconds)
            attempt++
        }
    }

    // No cache control: these responses are account-specific and must not be cached.
    private suspend fun get(url: String, token: String): Response = client.get(
        url = url,
        headers = Headers.headersOf("Authorization", "Bearer $token"),
        cacheControl = null,
        ensureSuccess = false,
    )

    companion object {
        /** Gmail's own page maximum for messages.list. */
        private const val PAGE_SIZE = 500

        /** Safety net against walking an entire mailbox. */
        private const val MAX_MESSAGES = 5_000

        private const val MAX_RETRIES = 4

        /** Doubles per attempt: 1s, 2s, 4s, 8s. */
        private const val RETRY_BASE_MILLIS = 1_000L

        /** Builds a normalized [EmailMessage] for extensions. */
        fun toEmailMessage(response: MessageResponse): EmailMessage {
            val headers = response.payload?.headers.orEmpty()
            fun header(name: String): String? = headers.firstOrNull { it.name.equals(name, true) }?.value

            val (text, html) = collectBodies(response.payload)
            return EmailMessage(
                id = response.id.hashCode().toLong(),
                messageId = header("Message-ID") ?: response.id,
                threadId = header("Thread-ID") ?: response.threadId,
                subject = header("Subject") ?: "",
                from = header("From") ?: "",
                to = header("To") ?: "",
                date = response.internalDate?.toLongOrNull() ?: 0L,
                bodyText = text,
                bodyHtml = html,
            )
        }

        /**
         * The text and html parts as the message carried them.
         *
         * A bank receipt is often html and nothing else, and it is left that
         * way: turning markup into the lines a parser reads is a parser's
         * decision, and it belongs to whoever knows the bank. Extensions do it
         * with the receipt library in finbox-extension.
         */
        private fun collectBodies(payload: Payload?): Pair<String, String> {
            if (payload == null) return "" to ""
            val data = payload.body.data ?: ""
            if (payload.mimeType == "text/plain" && data.isNotEmpty()) {
                return decodeBase64(data) to ""
            }
            if (payload.mimeType == "text/html" && data.isNotEmpty()) {
                return "" to decodeBase64(data)
            }
            var text = ""
            var html = ""
            for (part in payload.parts) {
                val (t, h) = collectBodies(part)
                if (t.isNotEmpty() && text.isEmpty()) text = t
                if (h.isNotEmpty() && html.isEmpty()) html = h
            }
            return text to html
        }

        private fun decodeBase64(data: String): String = try {
            Base64.decode(data, Base64.URL_SAFE).decodeToString()
        } catch (e: Exception) {
            ""
        }
    }
}
