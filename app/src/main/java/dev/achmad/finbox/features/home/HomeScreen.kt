package dev.achmad.finbox.features.home

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey

object HomeScreen : Screen {
    private fun readResolve(): Any = HomeScreen

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {

    }

}
