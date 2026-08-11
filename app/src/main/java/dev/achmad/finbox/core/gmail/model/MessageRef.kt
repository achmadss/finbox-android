package dev.achmad.finbox.core.gmail.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageRef(
    @SerialName("id") val id: String,
    @SerialName("threadId") val threadId: String? = null,
)