package dev.achmad.finbox.features.account.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import dev.achmad.finbox.features.account.list.AccountAvatar
import dev.achmad.finbox.features.account.list.RemoveAccountConfirmation
import dev.achmad.finbox.util.formatter.formatDate
import dev.achmad.finbox.util.ui.rememberUse24HourClock
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.achmad.finbox.theme.AppTheme
import dev.achmad.finbox.util.preview.previewAccount
import dev.achmad.finbox.R

data class AccountDetailsScreen(private val id: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel(tag = id) { AccountDetailsScreenModel(id) }
        val account by model.account.collectAsState()
        val disabled by model.disabled.collectAsState()
        val parsers by model.parsers.collectAsState()

        // Gone once it has been here means removed, here or anywhere else. Only once it has been
        // here: the read starts null and fills a moment later, which is not the same thing.
        var everLoaded by remember { mutableStateOf(false) }
        LaunchedEffect(account) {
            if (account != null) everLoaded = true else if (everLoaded) navigator.pop()
        }

        AccountDetailsScreenContent(
            account = account,
            parsers = parsers.map { ParserToggle(it.id, it.name, enabled = it.id !in disabled) },
            use24Hour = rememberUse24HourClock(),
            onBack = navigator::pop,
            onSyncEnabledChange = model::setSyncEnabled,
            onParserEnabledChange = model::setParserEnabled,
            onRemove = model::remove,
        )
    }
}

/** One parser row: what it is called, and whether this account reads with it. */
data class ParserToggle(val id: Long, val name: String, val enabled: Boolean)

/** Null [account] is the first read still running. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailsScreenContent(
    account: EmailAccount?,
    parsers: List<ParserToggle>,
    use24Hour: Boolean,
    onBack: () -> Unit = {},
    onSyncEnabledChange: (Boolean) -> Unit = {},
    onParserEnabledChange: (Long, Boolean) -> Unit = { _, _ -> },
    onRemove: () -> Unit = {},
) {
    var confirmRemove by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_account_info)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_bar_up_description))
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

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            DetailsHeader(
                account = account,
                parsers = parsers.count { it.enabled },
                totalParsers = parsers.size,
                use24Hour = use24Hour,
                onClickRemove = { confirmRemove = true },
            )
            HorizontalDivider()
            SwitchRow(
                title = stringResource(R.string.sync),
                subtitle = stringResource(R.string.account_sync_summ),
                checked = account.enabled,
                onCheckedChange = onSyncEnabledChange,
            )
            HorizontalDivider()
            Text(
                text = stringResource(R.string.parsers),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 4.dp),
            )
            if (parsers.isEmpty()) {
                Text(
                    text = stringResource(R.string.account_no_parsers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            parsers.forEach { parser ->
                SwitchRow(
                    title = parser.name,
                    checked = parser.enabled,
                    onCheckedChange = { onParserEnabledChange(parser.id, it) },
                )
            }
            HorizontalDivider()
        }
    }

    if (confirmRemove) {
        RemoveAccountConfirmation(
            email = account?.email.orEmpty(),
            onConfirm = onRemove,
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun DetailsHeader(
    account: EmailAccount,
    parsers: Int,
    totalParsers: Int,
    use24Hour: Boolean,
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
            text = account.lastSyncAt
                ?.let { stringResource(R.string.synced, formatDate(it, use24Hour)) }
                ?: stringResource(R.string.never_synced),
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
        ) { Text(stringResource(R.string.action_remove_account)) }
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

@Preview
@Composable
private fun AccountDetailsPreview() {
    AppTheme {
        AccountDetailsScreenContent(
            account = previewAccount(),
            parsers = listOf(
                ParserToggle(1L, "Jago", enabled = true),
                ParserToggle(2L, "BRI", enabled = false),
            ),
            use24Hour = true,
        )
    }
}

@Preview
@Composable
private fun AccountDetailsLoadingPreview() {
    AppTheme {
        AccountDetailsScreenContent(account = null, parsers = emptyList(), use24Hour = true)
    }
}
