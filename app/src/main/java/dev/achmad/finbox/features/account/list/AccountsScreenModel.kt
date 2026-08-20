package dev.achmad.finbox.features.account.list

import android.content.Intent
import android.util.Log
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.repository.AccountParserRepository
import dev.achmad.data.repository.AccountRepository
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.core.parser.ParserManager
import dev.achmad.finbox.core.parser.LoadedSource
import dev.achmad.finbox.core.gmail.GmailAuthManager
import dev.achmad.finbox.core.gmail.GmailTokenStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountsScreenModel(
    private val accountRepository: AccountRepository = inject(),
    private val accountParserRepository: AccountParserRepository = inject(),
    private val tokenStore: GmailTokenStore = inject(),
    private val authManager: GmailAuthManager = inject(),
    private val parserManager: ParserManager = inject(),
) : ScreenModel {

    val accounts: StateFlow<List<EmailAccount>> = accountRepository.accounts()
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Which parsers each account has switched **off**, not which it has switched on.
     *
     * That is what an assignment means to the update: a source with no row runs, and an
     * account with no rows at all runs everything installed (`TransactionUpdater.sourcesFor`).
     * Reading the rows the other way round showed every switch off on an account nobody had
     * configured yet — while it was in fact reading with all of them.
     */
    val disabledByAccount: StateFlow<Map<String, Set<Long>>> =
        accountParserRepository.allAssignments()
            .map { list ->
                list.filterNot { it.enabled }
                    .groupBy({ it.accountId }, { it.sourceId })
                    .mapValues { it.value.toSet() }
            }
            .stateIn(screenModelScope, SharingStarted.Eagerly, emptyMap())

    /** Parsers currently loaded — what an assignment's `sourceId` points at. */
    val sources: StateFlow<List<LoadedSource>> = parserManager.sourcesFlow

    /** The browser flow to launch for result; hand the result back to [addAccount]. */
    fun authorizationIntent(): Intent = authManager.authorizationIntent()

    fun addAccount(data: Intent?) {
        if (data == null) return
        screenModelScope.launch {
            runCatching { authManager.handleCallback(data) }
                .onFailure { Log.e("Accounts", "Add account failed", it) }
        }
    }

    /**
     * Removes the account and forgets what it was configured with. Its mail and transactions
     * stay: the account is keyed on its address, so adding the same mailbox back adopts them
     * rather than fetching and writing the lot a second time.
     */
    fun remove(id: String) {
        screenModelScope.launch {
            accountRepository.delete(id)
            accountParserRepository.deleteForAccount(id)
            // A removed account keeping a live refresh token is a token nothing will ever use.
            tokenStore.clear(id)
        }
    }
}
