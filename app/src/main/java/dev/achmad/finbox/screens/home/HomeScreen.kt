package dev.achmad.finbox.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.achmad.finbox.screens.accounts.AccountsScreen
import dev.achmad.finbox.screens.extensions.ExtensionsScreen
import dev.achmad.finbox.screens.transactions.TransactionsScreen
import dev.achmad.finbox.screens.unrecognized.UnrecognizedScreen

object HomeScreen : Screen {

    @Composable
    override fun Content() {
        TabNavigator(TransactionsTab) { navigator ->
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = navigator.current == tab,
                                onClick = { navigator.current = tab },
                                icon = { tab.options.icon?.let { Icon(it, contentDescription = null) } },
                                label = { Text(tab.options.title) },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (val content = navigator.current) {
                        is TransactionsTab -> TransactionsScreen.Content()
                        is AccountsTab -> AccountsScreen.Content()
                        is ExtensionsTab -> ExtensionsScreen.Content()
                        is UnrecognizedTab -> UnrecognizedScreen.Content()
                    }
                }
            }
        }
    }

    private val tabs = listOf(
        TransactionsTab,
        AccountsTab,
        ExtensionsTab,
        UnrecognizedTab,
    )
}

private object TransactionsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 0u.toUShort(),
            title = "Transactions",
            icon = rememberVectorPainter(Icons.Filled.AccountBalanceWallet),
        )

    @Composable
    override fun Content() {}
}

private object AccountsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 1u.toUShort(),
            title = "Accounts",
            icon = rememberVectorPainter(Icons.Filled.AlternateEmail),
        )

    @Composable
    override fun Content() {}
}

private object ExtensionsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 2u.toUShort(),
            title = "Extensions",
            icon = rememberVectorPainter(Icons.Filled.Extension),
        )

    @Composable
    override fun Content() {}
}

private object UnrecognizedTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 3u.toUShort(),
            title = "Unrecognized",
            icon = rememberVectorPainter(Icons.Filled.MailOutline),
        )

    @Composable
    override fun Content() {}
}
