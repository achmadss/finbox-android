package dev.achmad.finbox.features.settings.llm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.finbox.R
import dev.achmad.finbox.core.llm.LlmProvider
import dev.achmad.finbox.theme.components.AppBar

/**
 * The endpoints available to classify with, and which one is in use.
 *
 * Reachable and empty is the normal state. Nothing in the app requires a
 * provider, so this screen has to read as an offer rather than as a gap.
 */
object SettingsLlmScreen : Screen {
    private fun readResolve(): Any = SettingsLlmScreen

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = remember { SettingsLlmScreenModel() }
        val state by model.state.collectAsState()
        var confirmDelete by remember { mutableStateOf<LlmProvider?>(null) }

        // The edit screen writes straight to the store, so what is on screen
        // here is stale the moment one is saved.
        LaunchedEffect(navigator.lastItem) { model.refresh() }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(R.string.llm_providers_title),
                    navigateUp = navigator::pop,
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { navigator.push(SettingsLlmProviderScreen(null)) }) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.llm_add_provider))
                }
            },
        ) { padding ->
            if (state.providers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.llm_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                return@Scaffold
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.providers, key = { it.id }) { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navigator.push(SettingsLlmProviderScreen(provider.id)) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = provider.id == state.activeId,
                            onClick = { model.setActive(provider.id) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(provider.name)
                            Text(
                                text = provider.model.ifBlank { provider.endpoint },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { confirmDelete = provider }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.action_remove))
                        }
                    }
                }
            }
        }

        confirmDelete?.let { provider ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                text = {
                    Text(stringResource(R.string.llm_delete_confirmation, provider.name))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            model.delete(provider.id)
                            confirmDelete = null
                        },
                    ) { Text(stringResource(R.string.action_remove)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}
