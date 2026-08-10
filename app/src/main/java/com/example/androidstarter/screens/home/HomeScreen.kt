package com.example.androidstarter.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen

object HomeScreen: Screen {
    private fun readResolve(): Any = HomeScreen

    @Composable
    override fun Content() {
        // Replace placeholder UI
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("Hello World!")
        }
    }
}