package dev.achmad.finbox.core.gmail.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Body(
    @SerialName("size") val size: Int = 0,
    @SerialName("data") val data: String? = null,
)