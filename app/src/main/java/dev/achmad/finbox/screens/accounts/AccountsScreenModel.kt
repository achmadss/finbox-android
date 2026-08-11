package dev.achmad.finbox.screens.accounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.repository.AccountExtensionRepository
import dev.achmad.data.repository.AccountRepository
import dev.achmad.finbox.core.util.inject
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.extension.TransactionSource
import dev.achmad.finbox.core.gmail.GmailAuthManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountsScreenModel(
    private val accountRepository: AccountRepository = inject(),
    private val accountExtensionRepository: AccountExtensionRepository = inject(),
    private val authManager: GmailAuthManager = inject(),
    private val extensionManager: ExtensionManager = inject(),
) : ScreenModel {

    val accounts: StateFlow<List<EmailAccount>> = accountRepository.accounts()
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    val enabledByAccount: StateFlow<Map<String, Set<Long>>> =
        accountExtensionRepository.allAssignments()
            .map { list ->
                list.filter { it.enabled }
                    .groupBy({ it.accountId }, { it.sourceId })
                    .mapValues { it.value.toSet() }
            }
            .stateIn(screenModelScope, SharingStarted.Eagerly, emptyMap())

    var parsersDialogAccount by mutableStateOf<EmailAccount?>(null)

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
