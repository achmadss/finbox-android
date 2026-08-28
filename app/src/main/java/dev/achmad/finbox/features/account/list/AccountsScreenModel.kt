package dev.achmad.finbox.features.account.list

import android.content.Intent
import android.util.Log
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.repository.AccountExtensionRepository
import dev.achmad.data.repository.AccountRepository
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.extension.Extension
import dev.achmad.finbox.core.gmail.GmailAuthManager
import dev.achmad.finbox.core.gmail.GmailTokenStore
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class AccountRow(
    val account: EmailAccount,
    val extensionCount: Int,
)

class AccountsScreenModel(
    private val accountRepository: AccountRepository = inject(),
    private val accountExtensionRepository: AccountExtensionRepository = inject(),
    private val tokenStore: GmailTokenStore = inject(),
    private val authManager: GmailAuthManager = inject(),
    private val extensionManager: ExtensionManager = inject(),
) : ScreenModel {

    val accounts: StateFlow<List<EmailAccount>> = accountRepository.accounts()
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Which extensions each account has switched off, not on: an extension with no row runs, and
     * an account with no rows at all runs everything installed.
     */
    val disabledByAccount: StateFlow<Map<String, Set<String>>> =
        accountExtensionRepository.allAssignments()
            .map { list ->
                list.filterNot { it.enabled }
                    .groupBy({ it.accountId }, { it.extensionId })
                    .mapValues { it.value.toSet() }
            }
            .stateIn(screenModelScope, SharingStarted.Eagerly, emptyMap())

    /** The extensions that run — what an assignment's `extensionId` points at. */
    val extensions: StateFlow<List<Extension>> = extensionManager.enabled

    val rows: StateFlow<List<AccountRow>> =
        combine(accounts, disabledByAccount, extensions) { accounts, disabled, extensions ->
            accounts.map { account ->
                val off = disabled[account.id].orEmpty()
                // Counted off the shipped extensions, not off the rows: an extension with
                // no row of its own is one this account reads with.
                AccountRow(account = account, extensionCount = extensions.count { it.id !in off })
            }
        }.stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    fun authorizationIntent(): Intent = authManager.authorizationIntent()

    fun addAccount(data: Intent?) {
        if (data == null) return
        screenModelScope.launch {
            runCatching { authManager.handleCallback(data) }
                .onFailure { Log.e("Accounts", "Add account failed", it) }
        }
    }

    /**
     * Removes the account and what it was configured with. Mail and transactions stay: the
     * account is keyed on its address, so re-adding the same mailbox adopts them.
     */
    fun remove(id: String) {
        screenModelScope.launch {
            accountRepository.delete(id)
            accountExtensionRepository.deleteForAccount(id)
            // A removed account keeping a live refresh token is a token nothing will ever use.
            tokenStore.clear(id)
        }
    }
}
