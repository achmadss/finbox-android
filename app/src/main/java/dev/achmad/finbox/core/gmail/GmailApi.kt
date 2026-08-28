package dev.achmad.finbox.core.gmail

import android.util.Base64
import dev.achmad.finbox.extension.Email
import dev.achmad.finbox.core.gmail.model.HistoryResponse
import dev.achmad.finbox.core.gmail.model.ProfileResponse
import dev.achmad.finbox.core.gmail.model.MessageRef
import dev.achmad.finbox.core.gmail.model.MessageResponse
import dev.achmad.finbox.core.gmail.model.Payload

/**
 * The reads a transaction update makes against a mailbox.
 *
 * An interface so a debug build can run the import offline against a mock —
 * see `di/GmailModule.kt` for which implementation is bound.
 *
 * The window is passed as timestamps, not a Gmail query string, so a fake
 * need not speak Gmail's search language.
 */
interface GmailApi {

    /** The mailbox's current [ProfileResponse.historyId], used to seed the sync cursor. */
    suspend fun getProfile(accountId: String): ProfileResponse

    /**
     * Ids of the messages in [after]..[before], newest first, [narrow]ed by a
     * Gmail search term, capped at [maxMessages] so a too-wide query cannot
     * walk the whole mailbox.
     */
    suspend fun listMessages(
        accountId: String,
        after: Long? = null,
        before: Long? = null,
        narrow: String? = null,
        maxMessages: Int = MAX_MESSAGES,
    ): List<MessageRef>

    /**
     * One page of changes since [startHistoryId].
     *
     * Throws [dev.achmad.finbox.util.network.HttpException] with code 404 when
     * Gmail has expired that cursor — the caller has to fall back to a full
     * update.
     */
    suspend fun listHistory(
        accountId: String,
        startHistoryId: String,
        pageToken: String? = null,
    ): HistoryResponse

    /** The full message, normalized for extensions. */
    suspend fun getEmail(accountId: String, messageId: String): Email

    companion object {
        /** Safety net against walking an entire mailbox. */
        const val MAX_MESSAGES = 5_000

        fun toEmail(response: MessageResponse): Email {
            val headers = response.payload?.headers.orEmpty()
            fun header(name: String): String? = headers.firstOrNull { it.name.equals(name, true) }?.value

            return Email(
                messageId = header("Message-ID") ?: response.id,
                threadId = response.threadId,
                subject = header("Subject") ?: "",
                from = header("From") ?: "",
                date = response.internalDate?.toLongOrNull() ?: 0L,
                body = collectBody(response.payload),
            )
        }

        /** The body as the message carried it: html preferred, plain text the fallback. */
        private fun collectBody(payload: Payload?): String {
            if (payload == null) return ""
            val data = payload.body.data ?: ""
            if (data.isNotEmpty() && payload.mimeType in BODY_TYPES) return decodeBase64(data)
            var text = ""
            for (part in payload.parts) {
                val body = collectBody(part)
                if (body.isEmpty()) continue
                if (part.mimeType == "text/html") return body
                if (text.isEmpty()) text = body
            }
            return text
        }

        private val BODY_TYPES = setOf("text/html", "text/plain")

        private fun decodeBase64(data: String): String = try {
            Base64.decode(data, Base64.URL_SAFE).decodeToString()
        } catch (e: Exception) {
            ""
        }
    }
}
