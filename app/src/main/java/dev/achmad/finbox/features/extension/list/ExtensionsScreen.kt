package dev.achmad.finbox.features.extension.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.finbox.R
import dev.achmad.finbox.core.extension.extensionIcon
import dev.achmad.finbox.features.extension.detail.ExtensionDetailScreen
import dev.achmad.finbox.theme.AppTheme

object ExtensionsScreen : Screen {

    private fun readResolve(): Any = ExtensionsScreen

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { ExtensionsScreenModel() }
        val state by model.state.collectAsState()

        ExtensionsScreenContent(
            state = state,
            onBack = navigator::pop,
            onOpenExtension = { navigator.push(ExtensionDetailScreen(it)) },
            onEnabledChange = model::setEnabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreenContent(
    state: ExtensionsScreenModel.State,
    onBack: () -> Unit = {},
    onOpenExtension: (String) -> Unit = {},
    onEnabledChange: (String, Boolean) -> Unit = { _, _ -> },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.extensions)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_bar_up_description),
                        )
                    }
                },
            )
        },
    ) { padding ->
        // No pull-to-refresh, no empty state: the list is a compile-time
        // constant, so it is never stale and never empty.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(state.rows, key = { it.id }) { row ->
                ExtensionListItem(
                    row = row,
                    onClick = { onOpenExtension(row.id) },
                    onEnabledChange = { onEnabledChange(row.id, it) },
                )
            }
        }
    }
}

@Composable
private fun ExtensionListItem(
    row: ExtensionRow,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIcon(row.id, Modifier.size(40.dp))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(text = row.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = row.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = row.enabled, onCheckedChange = onEnabledChange)
    }
}

/** The extension's own icon, or the generic one for an id with no drawable. */
@Composable
fun ExtensionIcon(id: String, modifier: Modifier = Modifier) {
    val res = extensionIcon(id)
    if (res == null) {
        Icon(
            imageVector = Icons.Filled.Extension,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    } else {
        Image(painter = painterResource(res), contentDescription = null, modifier = modifier)
    }
}

@Preview
@Composable
private fun ExtensionsScreenPreview() {
    AppTheme {
        ExtensionsScreenContent(state = ExtensionsScreenModel.State(rows = emptyList()))
    }
}
