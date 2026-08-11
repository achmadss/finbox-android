package dev.achmad.finbox.core.gmail.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageResponse(
    @SerialName("id") val id: String = "",
    @SerialName("threadId") val threadId: String = "",
    @SerialName("payload") val payload: Payload? = null,
    @SerialName("internalDate") val internalDate: String? = null,
)