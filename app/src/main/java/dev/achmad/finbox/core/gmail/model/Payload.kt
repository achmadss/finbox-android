package dev.achmad.finbox.core.gmail.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Payload(
    @SerialName("headers") val headers: List<Header> = emptyList(),
    @SerialName("mimeType") val mimeType: String? = null,
    @SerialName("body") val body: Body = Body(),
    @SerialName("parts") val parts: List<Payload> = emptyList(),
)