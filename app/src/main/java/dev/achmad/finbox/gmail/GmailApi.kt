package dev.achmad.finbox.gmail

import dev.achmad.finbox.config.FinboxConfig
import dev.achmad.finbox.extension.EmailMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.util.Base64
import java.io.IOException
import java.net.URLEncoder
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

@Serializable
data class MessageListResponse(val messages: List<MessageRef> = emptyList(), val nextPageToken: String? = null)

@Serializable
data class MessageRef(val id: String, val threadId: String? = null)

@Serializable
data class MessageResponse(
    val id: String = "",
    val threadId: String = "",
    val payload: Payload? = null,
    val internalDate: String? = null,
)

@Serializable
data class Payload(
    val headers: List<Header> = emptyList(),
    val mimeType: String? = null,
    val body: Body = Body(),
    val parts: List<Payload> = emptyList(),
)

@Serializable
data class Header(val name: String = "", val value: String = "")

@Serializable
data class Body(val size: Int = 0, val data: String? = null)

/** Thin Gmail REST client on top of OkHttp. */
class GmailApi(
    private val client: OkHttpClient,
    private val tokens: GmailTokenManager,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listMessages(
        accountId: String,
        query: String = "category:finance",
        maxResults: Int = 100,
    ): List<MessageRef> {
        val token = tokens.getAccessToken(accountId) ?: return emptyList()
        val url = "${FinboxConfig.GMAIL_API_BASE}/messages?q=${URLEncoder.encode(query, "UTF-8")}&maxResults=$maxResults"
        val response = getWithRetry(accountId, url, token)
        return json.decodeFromString<MessageListResponse>(response).messages
    }

    suspend fun getMessage(accountId: String, messageId: String): MessageResponse {
        val token = tokens.getAccessToken(accountId) ?: error("No token")
        val url = "${FinboxConfig.GMAIL_API_BASE}/messages/$messageId?format=full"
        val response = getWithRetry(accountId, url, token)
        return json.decodeFromString<MessageResponse>(response)
    }

    /** GET with a single 401-triggered token refresh retry. */
    private suspend fun getWithRetry(accountId: String, url: String, token: String): String {
        val first = get(url, token)
        if (first.startsWith("{\"error\"")) {
            tokens.refreshAccessToken(accountId)?.let { fresh ->
                return get(url, fresh)
            }
        }
        return first
    }

    private suspend fun get(url: String, token: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 401) {
                throw IOException("Gmail API ${response.code}")
            }
            response.body?.string().orEmpty()
        }
    }

    companion object {
        /** Builds a normalized [EmailMessage] for parser extensions. */
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
