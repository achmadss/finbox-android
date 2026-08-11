package dev.achmad.finbox.core.gmail

import android.util.Base64
import dev.achmad.finbox.core.FinboxConfig
import dev.achmad.finbox.core.network.HttpException
import dev.achmad.finbox.core.network.get
import dev.achmad.finbox.core.network.parseAs
import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.core.gmail.model.MessageListResponse
import dev.achmad.finbox.core.gmail.model.MessageRef
import dev.achmad.finbox.core.gmail.model.MessageResponse
import dev.achmad.finbox.core.gmail.model.Payload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup

/** Thin Gmail REST client on top of OkHttp. */
class GmailApi(
    private val client: OkHttpClient,
    private val tokens: GmailTokenManager,
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
        val token = tokens.getAccessToken(accountId) ?: return MessageListResponse()
        val encoded = withContext(Dispatchers.IO) { URLEncoder.encode(query, "UTF-8") }
        val url = "${FinboxConfig.GMAIL_API_BASE}/messages?q=$encoded&maxResults=$maxResults" +
            pageToken.orEmpty().let { if (it.isEmpty()) "" else "&pageToken=$it" }
        return getWithRetry(accountId, url, token)
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

    suspend fun getMessage(accountId: String, messageId: String): MessageResponse {
        val token = tokens.getAccessToken(accountId) ?: error("No token")
        val url = "${FinboxConfig.GMAIL_API_BASE}/messages/$messageId?format=full"
        return getWithRetry(accountId, url, token)
    }

    /** GET with a single 401-triggered token refresh retry. */
    private suspend inline fun <reified T> getWithRetry(
        accountId: String,
        url: String,
        token: String,
    ): T {
        var response = get(url, token)
        if (response.code == 401) {
            response.close()
            val fresh = tokens.refreshAccessToken(accountId) ?: throw HttpException(401)
            response = get(url, fresh)
        }
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
        return response.parseAs()
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

        /** Safety net for a source whose query is too broad. */
        private const val MAX_MESSAGES = 5_000

        /** Builds a normalized [EmailMessage] for extensions. */
        fun toEmailMessage(response: MessageResponse): EmailMessage {
            val headers = response.payload?.headers.orEmpty()
            fun header(name: String): String? = headers.firstOrNull { it.name.equals(name, true) }?.value

            val (text, html) = extractBodies(response.payload)
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

        private fun extractBodies(payload: Payload?): Pair<String, String> {
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
                val (t, h) = extractBodies(part)
                if (t.isNotEmpty() && text.isEmpty()) text = t
                if (h.isNotEmpty() && html.isEmpty()) html = h
            }
            if (text.isEmpty() && html.isNotEmpty()) {
                text = Jsoup.parse(html).text()
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
