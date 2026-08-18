package dev.achmad.finbox.util.ui

import android.content.Context
import android.widget.Toast

class ToastHelper(private val context: Context) {

    fun show(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    fun show(resId: Int, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, resId, duration).show()
    }

}