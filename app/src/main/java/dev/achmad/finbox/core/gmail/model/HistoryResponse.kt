package dev.achmad.finbox.core.gmail.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileResponse(
    @SerialName("emailAddress") val emailAddress: String = "",
    @SerialName("historyId") val historyId: String = "",
)

/** OpenID userinfo. Both fields are absent for an account that set neither. */
@Serializable
data class UserInfoResponse(
    @SerialName("name") val name: String? = null,
    @SerialName("picture") val picture: String? = null,
)

@Serializable
data class HistoryResponse(
    @SerialName("history") val history: List<HistoryRecord> = emptyList(),
    @SerialName("nextPageToken") val nextPageToken: String? = null,
    /** The mailbox's current historyId, present even when nothing changed. */
    @SerialName("historyId") val historyId: String? = null,
)

@Serializable
data class HistoryRecord(
    @SerialName("id") val id: String = "",
    @SerialName("messages") val messages: List<MessageRef> = emptyList(),
    @SerialName("messagesAdded") val messagesAdded: List<HistoryMessage> = emptyList(),
    @SerialName("messagesDeleted") val messagesDeleted: List<HistoryMessage> = emptyList(),
    @SerialName("labelsAdded") val labelsAdded: List<HistoryMessage> = emptyList(),
    @SerialName("labelsRemoved") val labelsRemoved: List<HistoryMessage> = emptyList(),
)

@Serializable
data class HistoryMessage(
    @SerialName("message") val message: MessageRef,
)
