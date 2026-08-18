package dev.achmad.finbox.features.accounts

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.data.model.EmailAccount
import dev.achmad.finbox.util.formatter.formatDate
import dev.achmad.finbox.util.ui.rememberUse24HourClock

data class AccountDetailsScreen(private val id: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        // The list's model already holds everything this screen needs — the accounts, the
        // assignments, the actions — so it reads the same data rather than a second copy of it.
        val model = rememberScreenModel(tag = id) { AccountsScreenModel() }
        val accounts by model.accounts.collectAsState()
        val disabledByAccount by model.disabledByAccount.collectAsState()
        val sources by model.sources.collectAsState()
        var confirmRemove by remember { mutableStateOf(false) }

        val account = accounts.firstOrNull { it.id == id }
        // Gone once it has been here means removed, here or anywhere else. Only once it has been
        // here: the list starts empty and fills a moment later, which is not the same thing.
        var everLoaded by remember { mutableStateOf(false) }
        LaunchedEffect(account) {
            if (account != null) everLoaded = true else if (everLoaded) navigator.pop()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Account info") },
                    navigationIcon = {
                        IconButton(onClick = navigator::pop) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            if (account == null) {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            val disabled = disabledByAccount[account.id].orEmpty()
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                DetailsHeader(
                    account = account,
                    parsers = sources.count { it.id !in disabled },
                    totalParsers = sources.size,
                    onClickRemove = { confirmRemove = true },
                )
                HorizontalDivider()
                SwitchRow(
                    title = "Sync",
                    subtitle = "Read new statements from this mailbox",
                    checked = account.enabled,
                    onCheckedChange = { model.setSyncEnabled(account.id, it) },
                )
                HorizontalDivider()
                Text(
                    text = "Parsers",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 4.dp),
                )
                if (sources.isEmpty()) {
                    Text(
                        text = "No parsers installed. Install an extension first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                sources.forEach { source ->
                    SwitchRow(
                        title = source.name,
                        checked = source.id !in disabled,
                        onCheckedChange = { model.setParserEnabled(account.id, source.id, it) },
                    )
                }
                HorizontalDivider()
            }
        }

        if (confirmRemove) {
            RemoveAccountConfirmation(
                email = account?.email.orEmpty(),
                onConfirm = { model.remove(id) },
                onDismiss = { confirmRemove = false },
            )
        }
    }
}

@Composable
private fun DetailsHeader(
    account: EmailAccount,
    parsers: Int,
    totalParsers: Int,
    onClickRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AccountAvatar(photoUrl = account.photoUrl, size = 112.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = account.email,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = account.lastSyncAt?.let { "Synced ${formatDate(it, rememberUse24HourClock())}" }
                ?: "Never synced",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Row(
        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClickRemove,
        ) { Text("Remove account") }
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
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
