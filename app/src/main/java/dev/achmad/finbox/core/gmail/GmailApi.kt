package dev.achmad.finbox.core.gmail

import android.util.Base64
import dev.achmad.finbox.parser.EmailMessage
import dev.achmad.finbox.core.gmail.model.HistoryResponse
import dev.achmad.finbox.core.gmail.model.ProfileResponse
import dev.achmad.finbox.core.gmail.model.MessageRef
import dev.achmad.finbox.core.gmail.model.MessageResponse
import dev.achmad.finbox.core.gmail.model.Payload

/**
 * The reads a transaction update makes against a mailbox.
 *
 * An interface so a debug build can run the whole import offline against
 * `GmailApiMockImpl`; [GmailApiImpl] is the one that talks to Gmail. Which one is
 * bound is a build-type decision — see `di/GmailModule.kt` under `src/debug`
 * and `src/release`.
 *
 * The window is passed as timestamps rather than a Gmail query string: what
 * the search text has to look like is the real client's business, and a fake
 * shouldn't have to speak it.
 */
interface GmailApi {

    /** The mailbox's current [ProfileResponse.historyId], used to seed the sync cursor. */
    suspend fun getProfile(accountId: String): ProfileResponse

    /**
     * Ids of the messages in [after]..[before], newest first, [narrow]ed by a
     * Gmail search term.
     *
     * Capped at [maxMessages] rather than left unbounded — a source with a
     * too-wide query would otherwise walk the entire mailbox.
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

    /** The full message, normalized for parsers. */
    suspend fun getEmail(accountId: String, messageId: String): EmailMessage

    companion object {
        /** Safety net against walking an entire mailbox. */
        const val MAX_MESSAGES = 5_000

        /** Builds a normalized [EmailMessage] for parsers. */
        fun toEmailMessage(response: MessageResponse): EmailMessage {
            val headers = response.payload?.headers.orEmpty()
            fun header(name: String): String? = headers.firstOrNull { it.name.equals(name, true) }?.value

            val (text, html) = collectBodies(response.payload)
            return EmailMessage(
                id = response.id.hashCode().toLong(),
                messageId = header("Message-ID") ?: response.id,
                threadId = response.threadId,
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
         * decision, and it belongs to whoever knows the bank. Parsers do it
         * with the receipt library in finbox-parser.
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
