package dev.achmad.finbox.features.parsers

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.finbox.core.parser.InstallStep
import dev.achmad.finbox.parser.TransactionType

data class ParserDetailsScreen(private val pkg: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel(tag = pkg) { ParserDetailsScreenModel(pkg) }
        val state by model.state.collectAsState()
        var confirmUninstall by remember { mutableStateOf(false) }

        LaunchedEffect(state.uninstalled) {
            if (state.uninstalled) navigator.pop()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Parser info") },
                    navigationIcon = {
                        IconButton(onClick = navigator::pop) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            val parser = state.parser
            if (parser == null) {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                DetailsHeader(
                    parser = parser,
                    summary = state.summary,
                    sizeBytes = state.sizeBytes,
                    installStep = parser.installStep,
                    onClickUpdate = model::update,
                    onClickUninstall = { confirmUninstall = true },
                )
                HorizontalDivider()
                SwitchRow(
                    title = "Enabled",
                    checked = parser.enabled,
                    onCheckedChange = model::setEnabled,
                )
                HorizontalDivider()
                if (state.kinds.isNotEmpty()) {
                    Text(
                        text = "Transaction types",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 4.dp),
                    )
                    state.kinds.forEach { kind ->
                        SwitchRow(
                            title = kind.name,
                            subtitle = when (kind.type) {
                                TransactionType.EXPENSE -> "Expense"
                                TransactionType.INCOME -> "Income"
                            },
                            checked = kind.enabled,
                            onCheckedChange = { model.toggleKind(kind.key) },
                        )
                    }
                    HorizontalDivider()
                }
            }
        }

        if (confirmUninstall) {
            UninstallConfirmation(
                name = state.parser?.name.orEmpty(),
                onConfirm = model::uninstall,
                onDismiss = { confirmUninstall = false },
            )
        }
    }
}

@Composable
private fun DetailsHeader(
    parser: ParserUiModel.Installed,
    summary: String,
    sizeBytes: Long?,
    installStep: InstallStep,
    onClickUpdate: () -> Unit,
    onClickUninstall: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ParserIcon(parser, Modifier.size(112.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = parser.name,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = parser.pkg, // TODO remove the base package like dev.achmad.finbox (only for UI purposes)
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfoText(Modifier.weight(1f), parser.versionName, "Version")
        InfoDivider()
        InfoText(Modifier.weight(1f), summary, "Transactions")
        InfoDivider()
        InfoText(
            modifier = Modifier.weight(1f),
            // The platform's own formatter, so the units read the way every
            // other size on the phone does.
            primary = sizeBytes
                ?.let { Formatter.formatShortFileSize(LocalContext.current, it) }
                ?: "—",
            secondary = "Size",
        )
    }

    Row(
        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onClickUninstall,
        ) { Text("Uninstall") }

        if (parser.update != null) {
            val running = !installStep.isCompleted()
            Button(
                modifier = Modifier.weight(1f),
                enabled = !running,
                onClick = onClickUpdate,
            ) {
                Text(
                    when {
                        running -> "${installStep.name}…"
                        installStep == InstallStep.Error -> "Retry update"
                        else -> "Update to v${parser.update.versionName}"
                    },
                )
            }
        }
    }
}

@Composable
private fun InfoDivider() {
    VerticalDivider(modifier = Modifier.height(32.dp))
}

@Composable
private fun InfoText(
    modifier: Modifier = Modifier,
    primary: String,
    secondary: String,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = primary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = secondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String? = null,
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
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
