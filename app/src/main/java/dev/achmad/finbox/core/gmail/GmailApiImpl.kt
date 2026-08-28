package dev.achmad.finbox.core.gmail

import dev.achmad.finbox.core.FinboxConfig
import dev.achmad.finbox.util.network.HttpException
import dev.achmad.finbox.util.network.get
import dev.achmad.finbox.util.network.json
import dev.achmad.finbox.util.network.parseAs
import dev.achmad.finbox.extension.Email
import dev.achmad.finbox.core.gmail.model.HistoryResponse
import dev.achmad.finbox.core.gmail.model.MessageListResponse
import dev.achmad.finbox.core.gmail.model.ProfileResponse
import dev.achmad.finbox.core.gmail.model.MessageRef
import dev.achmad.finbox.core.gmail.model.MessageResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.time.Duration.Companion.milliseconds

/** Thin Gmail REST client on top of OkHttp. */
class GmailApiImpl(
    private val client: OkHttpClient,
    private val tokens: GmailTokenManager,
    private val quota: GmailQuota = GmailQuota(),
) : GmailApi {

    override suspend fun listMessages(
        accountId: String,
        after: Long?,
        before: Long?,
        narrow: String?,
        maxMessages: Int,
    ): List<MessageRef> {
        val query = buildWindowQuery(after = after, before = before, narrow = narrow)
        val refs = mutableListOf<MessageRef>()
        var pageToken: String? = null
        do {
            val page = listPage(accountId, query, pageToken = pageToken)
            refs += page.messages
            pageToken = page.nextPageToken
        } while (pageToken != null && refs.size < maxMessages)
        return if (refs.size > maxMessages) refs.take(maxMessages) else refs
    }

    private suspend fun listPage(
        accountId: String,
        query: String,
        maxResults: Int = PAGE_SIZE,
        pageToken: String? = null,
    ): MessageListResponse {
        // Fail, not return empty: an import that "finishes" empty would promote its cursor.
        val token = tokens.getAccessToken(accountId) ?: error("No token")
        val encoded = withContext(Dispatchers.IO) { URLEncoder.encode(query, "UTF-8") }
        val url = "${FinboxConfig.GMAIL_API_BASE}/messages?q=$encoded&maxResults=$maxResults" +
            "&fields=messages/id,messages/threadId,nextPageToken" +
            pageToken.orEmpty().let { if (it.isEmpty()) "" else "&pageToken=$it" }
        return getWithRetry(accountId, url, token, GmailQuota.MESSAGES_LIST)
    }

    /** The whole message: quota is charged per call, so headers first would cost twice. */
    private suspend fun getMessage(accountId: String, messageId: String): MessageResponse {
        val token = tokens.getAccessToken(accountId) ?: error("No token")
        val url = "${FinboxConfig.GMAIL_API_BASE}/messages/$messageId?format=full" +
            "&fields=id,threadId,internalDate,payload"
        return getWithRetry(accountId, url, token, GmailQuota.MESSAGES_GET)
    }

    override suspend fun getEmail(accountId: String, messageId: String): Email =
        GmailApi.toEmail(getMessage(accountId, messageId))

    override suspend fun getProfile(accountId: String): ProfileResponse {
        val token = tokens.getAccessToken(accountId) ?: error("No token")
        return getWithRetry(
            accountId,
            "${FinboxConfig.GMAIL_API_BASE}/profile",
            token,
            GmailQuota.GET_PROFILE,
        )
    }

    override suspend fun listHistory(
        accountId: String,
        startHistoryId: String,
        pageToken: String?,
    ): HistoryResponse {
        val token = tokens.getAccessToken(accountId) ?: error("No token")
        // Only messageAdded: label moves cannot produce transactions, and every
        // id here costs a 20-unit fetch.
        val url = "${FinboxConfig.GMAIL_API_BASE}/history" +
            "?startHistoryId=$startHistoryId&maxResults=$PAGE_SIZE" +
            "&historyTypes=messageAdded" +
            "&fields=history/messages/id,history/messages/threadId," +
            "history/messagesAdded/message/id,history/messagesAdded/message/threadId," +
            "nextPageToken,historyId" +
            pageToken.orEmpty().let { if (it.isEmpty()) "" else "&pageToken=$it" }
        return getWithRetry(accountId, url, token, GmailQuota.HISTORY_LIST)
    }

    /** GET paced against the quota, with a 401 token refresh and a retry backoff. */
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
                // Gmail answers 204 when the fields mask leaves nothing to send — an
                // empty result, not an error.
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

        private const val MAX_RETRIES = 4

        /** Doubles per attempt: 1s, 2s, 4s, 8s. */
        private const val RETRY_BASE_MILLIS = 1_000L
    }
}
