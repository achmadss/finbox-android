package dev.achmad.finbox.core.gmail.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageListResponse(
    @SerialName("messages") val messages: List<MessageRef> = emptyList(),
    @SerialName("nextPageToken") val nextPageToken: String? = null,
)