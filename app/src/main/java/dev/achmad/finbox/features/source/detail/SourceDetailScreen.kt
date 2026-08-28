package dev.achmad.finbox.features.source.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.finbox.R
import dev.achmad.finbox.source.core.email.EmailSource
import dev.achmad.finbox.source.core.SourceEntry
import dev.achmad.finbox.features.source.list.SourceIcon
import dev.achmad.finbox.theme.AppTheme

data class SourceDetailScreen(private val id: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel(tag = id) { SourceDetailScreenModel(id) }
        val state by model.state.collectAsState()

        SourceDetailScreenContent(
            state = state,
            onBack = navigator::pop,
            onEnabledChange = model::setEnabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceDetailScreenContent(
    state: SourceDetailScreenModel.State,
    onBack: () -> Unit = {},
    onEnabledChange: (Boolean) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_source_info)) },
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
        val source = state.source
        if (source == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(
                    text = stringResource(R.string.source_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SourceIcon(source, Modifier.size(112.dp))
                Spacer(Modifier.height(24.dp))
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = source.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // No version and no size: a source has no release of its own to
            // report, and it is not a file on disk. The app's version is both.
            HorizontalDivider()
            SwitchRow(
                title = stringResource(R.string.label_enabled),
                checked = state.enabled,
                onCheckedChange = onEnabledChange,
            )
            HorizontalDivider()
            SourceRow(source)
            HorizontalDivider()
        }
    }
}

/**
 * What this source can read, asked of the class rather than of anything it
 * declares — see the capability-as-type argument in the contract. Email is the
 * only kind so far, so this is one check; a second is one more.
 */
@Composable
private fun SourceRow(source: SourceEntry) {
    val sources = buildList {
        if (source.source is EmailSource) add(stringResource(R.string.source_email))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.source_reads),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = sources.joinToString(", ").ifEmpty { stringResource(R.string.source_none) },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Preview
@Composable
private fun SourceDetailUnknownPreview() {
    AppTheme {
        SourceDetailScreenContent(
            state = SourceDetailScreenModel.State(source = null, enabled = false),
        )
    }
}
