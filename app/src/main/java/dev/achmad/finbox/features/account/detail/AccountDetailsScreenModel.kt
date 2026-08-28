package dev.achmad.finbox.features.account.detail

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.repository.AccountParserRepository
import dev.achmad.data.repository.AccountRepository
import dev.achmad.finbox.core.gmail.GmailTokenStore
import dev.achmad.finbox.core.parser.LoadedParser
import dev.achmad.finbox.core.parser.ParserManager
import dev.achmad.finbox.util.koin.inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountDetailsScreenModel(
    private val id: String,
    private val accountRepository: AccountRepository = inject(),
    private val accountParserRepository: AccountParserRepository = inject(),
    private val tokenStore: GmailTokenStore = inject(),
    parserManager: ParserManager = inject(),
) : ScreenModel {

    /**
     * Null until the read comes back, and null again once the account is removed — the
     * screen leaves on its own however the removal happened.
     */
    val account: StateFlow<EmailAccount?> = accountRepository.accounts()
        .map { accounts -> accounts.firstOrNull { it.id == id } }
        .stateIn(screenModelScope, SharingStarted.Eagerly, null)

    /**
     * Which parsers this account has switched off, not on: a parser with no row runs, and
     * an account with no rows at all runs everything installed.
     */
    val disabled: StateFlow<Set<Long>> = accountParserRepository.forAccount(id)
        .map { assignments -> assignments.filterNot { it.enabled }.mapTo(mutableSetOf()) { it.parserId } }
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptySet())

    /** Parsers currently loaded — what an assignment's `parserId` points at. */
    val parsers: StateFlow<List<LoadedParser>> = parserManager.parsersFlow

    fun setSyncEnabled(enabled: Boolean) {
        screenModelScope.launch { accountRepository.setEnabled(id, enabled) }
    }

    fun setParserEnabled(parserId: Long, enabled: Boolean) {
        screenModelScope.launch { accountParserRepository.setEnabled(id, parserId, enabled) }
    }

    /**
     * Removes the account and what it was configured with. Mail and transactions stay: the
     * account is keyed on its address, so re-adding the same mailbox adopts them.
     */
    fun remove() {
        screenModelScope.launch {
            accountRepository.delete(id)
            accountParserRepository.deleteForAccount(id)
            // A removed account keeping a live refresh token is a token nothing will ever use.
            tokenStore.clear(id)
        }
    }
}
