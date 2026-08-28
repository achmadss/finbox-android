package dev.achmad.finbox.features.extension.detail

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
import dev.achmad.finbox.core.extension.InstallStep
import dev.achmad.finbox.features.extension.list.ExtensionIcon
import dev.achmad.finbox.features.extension.list.ExtensionUiModel
import dev.achmad.finbox.features.extension.list.UninstallConfirmation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.achmad.finbox.theme.AppTheme
import dev.achmad.data.model.InstalledExtension
import dev.achmad.finbox.R
import dev.achmad.finbox.features.extension.list.labelRes

data class ExtensionDetailScreen(private val pkg: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel(tag = pkg) { ExtensionDetailScreenModel(pkg) }
        val state by model.state.collectAsState()

        LaunchedEffect(state.uninstalled) {
            if (state.uninstalled) navigator.pop()
        }

        ExtensionDetailScreenContent(
            state = state,
            onBack = navigator::pop,
            onClickUpdate = model::update,
            onEnabledChange = model::setEnabled,
            onUninstall = model::uninstall,
        )
    }
}

/** A null `state.extension` is the first read still running. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionDetailScreenContent(
    state: ExtensionDetailScreenModel.State,
    onBack: () -> Unit = {},
    onClickUpdate: () -> Unit = {},
    onEnabledChange: (Boolean) -> Unit = {},
    onUninstall: () -> Unit = {},
) {
    var confirmUninstall by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_extension_info)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_bar_up_description))
                    }
                },
            )
        },
    ) { padding ->
        val extension = state.extension
        if (extension == null) {
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
                extension = extension,
                sizeBytes = state.sizeBytes,
                installStep = extension.installStep,
                onClickUpdate = onClickUpdate,
                onClickUninstall = { confirmUninstall = true },
            )
            HorizontalDivider()
            SwitchRow(
                title = stringResource(R.string.label_enabled),
                checked = extension.enabled,
                onCheckedChange = onEnabledChange,
            )
            HorizontalDivider()
            // ponytail: stub standing in for the source list. Stage 3 replaces
            // it with the sources this extension actually implements, read off
            // LoadedExtension by type check. Said out loud rather than left
            // blank, because an empty space where a feature used to be reads as
            // a bug and gets debugged as one.
            Text(
                text = stringResource(R.string.extension_sources_not_built),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            HorizontalDivider()
        }
    }

    if (confirmUninstall) {
        UninstallConfirmation(
            name = state.extension?.name.orEmpty(),
            onConfirm = onUninstall,
            onDismiss = { confirmUninstall = false },
        )
    }
}

@Composable
private fun DetailsHeader(
    extension: ExtensionUiModel.Installed,
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
        ExtensionIcon(extension, Modifier.size(112.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = extension.name,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = extension.pkg, // TODO remove the base package like dev.achmad.finbox
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
        InfoText(Modifier.weight(1f), extension.versionName, stringResource(R.string.version))
        InfoDivider()
        InfoText(
            modifier = Modifier.weight(1f),
            // The platform's own formatter, so the units read the way every
            // other size on the phone does.
            primary = sizeBytes
                ?.let { Formatter.formatShortFileSize(LocalContext.current, it) }
                ?: "—",
            secondary = stringResource(R.string.size),
        )
    }

    Row(
        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onClickUninstall,
        ) { Text(stringResource(R.string.action_uninstall)) }

        if (extension.update != null) {
            val running = !installStep.isCompleted()
            Button(
                modifier = Modifier.weight(1f),
                enabled = !running,
                onClick = onClickUpdate,
            ) {
                Text(
                    when {
                        running -> stringResource(
                            R.string.extension_step_ongoing,
                            installStep.labelRes()?.let { stringResource(it) }.orEmpty(),
                        )
                        installStep == InstallStep.Error -> stringResource(R.string.extension_retry_update)
                        else -> stringResource(R.string.extension_update_to, extension.update.versionName)
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

@Preview
@Composable
private fun ExtensionDetailPreview() {
    AppTheme {
        ExtensionDetailScreenContent(
            state = ExtensionDetailScreenModel.State(
                extension = ExtensionUiModel.Installed(
                    extension = InstalledExtension(
                        pkg = "dev.achmad.extension.jago",
                        name = "Jago",
                        file = "/data/extensions/jago.apk",
                        versionCode = 3,
                        versionName = "1.2.0",
                        libVersion = "1.0",
                        sha256 = "0".repeat(64),
                        extensionIds = listOf(1L),
                        enabled = true,
                    ),
                    update = null,
                    installStep = InstallStep.Idle,
                ),
                sizeBytes = 482_000,
            ),
        )
    }
}

@Preview
@Composable
private fun ExtensionDetailLoadingPreview() {
    AppTheme {
        ExtensionDetailScreenContent(state = ExtensionDetailScreenModel.State())
    }
}
