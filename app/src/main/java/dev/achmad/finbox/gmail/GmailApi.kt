package dev.achmad.finbox.gmail

import android.util.Base64
import dev.achmad.finbox.core.FinboxConfig
import dev.achmad.finbox.core.network.HttpException
import dev.achmad.finbox.core.network.get
import dev.achmad.finbox.core.network.parseAs
import dev.achmad.finbox.extension.EmailMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup

@Serializable
data class MessageListResponse(
    @SerialName("messages") val messages: List<MessageRef> = emptyList(),
    @SerialName("nextPageToken") val nextPageToken: String? = null,
)

@Serializable
data class MessageRef(
    @SerialName("id") val id: String,
    @SerialName("threadId") val threadId: String? = null,
)

@Serializable
data class MessageResponse(
    @SerialName("id") val id: String = "",
    @SerialName("threadId") val threadId: String = "",
    @SerialName("payload") val payload: Payload? = null,
    @SerialName("internalDate") val internalDate: String? = null,
)

@Serializable
data class Payload(
    @SerialName("headers") val headers: List<Header> = emptyList(),
    @SerialName("mimeType") val mimeType: String? = null,
    @SerialName("body") val body: Body = Body(),
    @SerialName("parts") val parts: List<Payload> = emptyList(),
)

@Serializable
data class Header(
    @SerialName("name") val name: String = "",
    @SerialName("value") val value: String = "",
)

@Serializable
data class Body(
    @SerialName("size") val size: Int = 0,
    @SerialName("data") val data: String? = null,
)

/** Thin Gmail REST client on top of OkHttp. */
class GmailApi(
    private val client: OkHttpClient,
    private val tokens: GmailTokenManager,
) {

    suspend fun listMessages(
        accountId: String,
        query: String = "category:finance",
        maxResults: Int = 100,
    ): List<MessageRef> {
        val token = tokens.getAccessToken(accountId) ?: return emptyList()
        val url = "${FinboxConfig.GMAIL_API_BASE}/messages" +
            "?q=${
                withContext(Dispatchers.IO) {
                    URLEncoder.encode(query, "UTF-8")
                }
            }&maxResults=$maxResults"
        return getWithRetry<MessageListResponse>(accountId, url, token).messages
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
