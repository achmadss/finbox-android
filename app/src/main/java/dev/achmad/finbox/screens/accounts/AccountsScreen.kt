package dev.achmad.finbox.screens.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import dev.achmad.domain.model.EmailAccount
import dev.achmad.domain.repository.AccountExtensionRepository
import dev.achmad.domain.repository.AccountRepository
import dev.achmad.finbox.extension.ExtensionManager
import dev.achmad.finbox.extension.TransactionSource
import dev.achmad.finbox.gmail.GmailAuthManager
import dev.achmad.finbox.util.formatDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object AccountsScreen : Screen, KoinComponent {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val model = rememberScreenModel { AccountsScreenModel() }
        val accounts by model.accounts.collectAsState()
        val enabledByAccount by model.enabledByAccount.collectAsState()

        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = model::addAccount) {
                    Icon(Icons.Filled.Add, contentDescription = "Add account")
                }
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(accounts, key = { it.id }) { account ->
                    AccountCard(
                        account = account,
                        onToggleSync = { model.setSyncEnabled(account.id, it) },
                        onRemove = { model.remove(account.id) },
                        onOpenParsers = { model.openParsersDialog(account) },
                    )
                }
            }
        }

        model.parsersDialogAccount?.let { account ->
            ParserAssignmentDialog(
                account = account,
                sources = model.availableSources(),
                enabled = enabledByAccount[account.id].orEmpty(),
                onToggle = { sourceId, enabled -> model.setParserEnabled(account.id, sourceId, enabled) },
                onDismiss = { model.parsersDialogAccount = null },
            )
        }
    }
}

class AccountsScreenModel : ScreenModel, KoinComponent {

    private val accountRepository: AccountRepository by inject()
    private val accountExtensionRepository: AccountExtensionRepository by inject()
    private val authManager: GmailAuthManager by inject()
    private val extensionManager: ExtensionManager by inject()

    private val _accounts = MutableStateFlow<List<EmailAccount>>(emptyList())
    val accounts: StateFlow<List<EmailAccount>> = _accounts

    private val assignments = accountExtensionRepository.allAssignments()
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    val enabledByAccount: StateFlow<Map<String, Set<Long>>> = assignments
        .map { list ->
            list.filter { it.enabled }
                .groupBy({ it.accountId }, { it.sourceId })
                .mapValues { it.value.toSet() }
        }
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptyMap())

    var parsersDialogAccount by mutableStateOf<EmailAccount?>(null)

    init {
        screenModelScope.launch {
            accountRepository.accounts().collect { _accounts.value = it }
        }
    }

    fun addAccount() = authManager.startAuthFlow()

    fun setSyncEnabled(id: String, enabled: Boolean) {
        screenModelScope.launch { accountRepository.setEnabled(id, enabled) }
    }

    fun remove(id: String) {
        screenModelScope.launch {
            accountRepository.delete(id)
            accountExtensionRepository.deleteForAccount(id)
        }
    }

    fun openParsersDialog(account: EmailAccount) {
        parsersDialogAccount = account
    }

    fun availableSources(): List<TransactionSource> = extensionManager.sources

    fun setParserEnabled(accountId: String, sourceId: Long, enabled: Boolean) {
        screenModelScope.launch {
            accountExtensionRepository.setEnabled(accountId, sourceId, enabled)
        }
    }
}

@Composable
private fun AccountCard(
    account: EmailAccount,
    onToggleSync: (Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onOpenParsers: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(account.email, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (account.enabled) {
                        "Sync enabled" + (account.lastSyncAt?.let { " \u2022 ${formatDate(it)}" } ?: "")
                    } else {
                        "Sync disabled"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenParsers) { Text("Parsers") }
            Switch(checked = account.enabled, onCheckedChange = onToggleSync)
            IconButton(onClick = { onRemove(account.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
        }
    }
}

@Composable
private fun ParserAssignmentDialog(
    account: EmailAccount,
    sources: List<TransactionSource>,
    enabled: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parsers for ${account.email}") },
        text = {
            Column {
                if (sources.isEmpty()) {
                    Text("No parsers installed. Install extensions first.")
                }
                sources.forEach { source ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(source.name, Modifier.weight(1f))
                        Switch(
                            checked = source.id in enabled,
                            onCheckedChange = { onToggle(source.id, it) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
