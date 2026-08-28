package dev.achmad.finbox.features.account.detail

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.repository.AccountExtensionRepository
import dev.achmad.data.repository.AccountRepository
import dev.achmad.finbox.core.gmail.GmailTokenStore
import dev.achmad.finbox.core.extension.LoadedExtension
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.util.koin.inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountDetailsScreenModel(
    private val id: String,
    private val accountRepository: AccountRepository = inject(),
    private val accountExtensionRepository: AccountExtensionRepository = inject(),
    private val tokenStore: GmailTokenStore = inject(),
    extensionManager: ExtensionManager = inject(),
) : ScreenModel {

    /**
     * Null until the read comes back, and null again once the account is removed — the
     * screen leaves on its own however the removal happened.
     */
    val account: StateFlow<EmailAccount?> = accountRepository.accounts()
        .map { accounts -> accounts.firstOrNull { it.id == id } }
        .stateIn(screenModelScope, SharingStarted.Eagerly, null)

    /**
     * Which extensions this account has switched off, not on: an extension with no row runs, and
     * an account with no rows at all runs everything installed.
     */
    val disabled: StateFlow<Set<String>> = accountExtensionRepository.forAccount(id)
        .map { assignments -> assignments.filterNot { it.enabled }.mapTo(mutableSetOf()) { it.extensionId } }
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptySet())

    /** Extensions currently loaded — what an assignment's `extensionId` points at. */
    val extensions: StateFlow<List<LoadedExtension>> = extensionManager.extensionsFlow

    fun setSyncEnabled(enabled: Boolean) {
        screenModelScope.launch { accountRepository.setEnabled(id, enabled) }
    }

    fun setExtensionEnabled(extensionId: String, enabled: Boolean) {
        screenModelScope.launch { accountExtensionRepository.setEnabled(id, extensionId, enabled) }
    }

    /**
     * Removes the account and what it was configured with. Mail and transactions stay: the
     * account is keyed on its address, so re-adding the same mailbox adopts them.
     */
    fun remove() {
        screenModelScope.launch {
            accountRepository.delete(id)
            accountExtensionRepository.deleteForAccount(id)
            // A removed account keeping a live refresh token is a token nothing will ever use.
            tokenStore.clear(id)
        }
    }
}
