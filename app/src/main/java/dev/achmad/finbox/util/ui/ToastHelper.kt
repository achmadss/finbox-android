package dev.achmad.finbox.util.ui

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

class ToastHelper(private val context: Context) {

    fun show(
        message: String,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        Toast.makeText(context, message, duration).show()
    }

    fun show(
        @StringRes resId: Int,
        args: List<Any> = emptyList(),
        duration: Int = Toast.LENGTH_SHORT
    ) {
        val message = context.getString(resId, *args.toTypedArray())
        show(message, duration)
    }

    /** The same, without building a list at the call site. */
    fun show(
        @StringRes resId: Int,
        vararg args: Any,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        show(resId, args.toList(), duration)
    }

}
