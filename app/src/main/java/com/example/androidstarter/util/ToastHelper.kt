package com.example.androidstarter.util

import android.content.Context
import android.widget.Toast

class ToastHelper(private val context: Context) {
    fun show(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }
}
