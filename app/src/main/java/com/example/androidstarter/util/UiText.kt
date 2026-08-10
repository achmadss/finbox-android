package com.example.androidstarter.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed class UiText {

    data class DynamicText(
        val text: String,
    ) : UiText()

    data class StringResource(
        @param:StringRes val res: Int,
    ) : UiText()

    @Composable
    fun asString(): String {
        return when (this) {
            is DynamicText -> text
            is StringResource -> stringResource(res)
        }
    }

    fun asString(context: Context): String {
        return when (this) {
            is DynamicText -> text
            is StringResource -> context.getString(res)
        }
    }

}