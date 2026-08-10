package com.example.androidstarter.util

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest

sealed class UiImage {

    data class DynamicImage(
        val imageUrl: String,
        val error: Painter? = null,
        val placeholder: Painter? = null,
    ) : UiImage()

    data class DrawableResource(
        @param:DrawableRes val imageDrawable: Int,
    ) : UiImage()

    @Composable
    fun asPainter(): Painter {
        return when (this) {
            is DynamicImage -> {
                rememberAsyncImagePainter(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .build(),
                    placeholder = placeholder,
                    error = error
                )
            }
            is DrawableResource -> painterResource(imageDrawable)
        }
    }
}