package dev.achmad.finbox.core.gmail

import android.content.Context
import android.content.Intent
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.repository.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Adding an account: an intent a screen launches for result, and the account
 * that comes back out of the result.
 *
 * An interface so a debug build can fake it. See `di/GmailModule.kt`.
 */
interface GmailAuthManager {

    /**
     * The authorization flow (account picker), for a screen to launch for
     * result. Returned, not started, because AppAuth reports back through
     * `setResult` from an Activity.
     */
    fun authorizationIntent(): Intent

    /** Called with the result of [authorizationIntent]. Returns the added account. */
    suspend fun handleCallback(data: Intent): EmailAccount
}

/**
 * The id a mailbox always gets: its own address, lowercased — the one thing
 * that survives an account being removed and added back, so a re-add lands on
 * the mail and transactions already stored under it.
 */
internal fun accountIdOf(email: String): String = email.trim().lowercase()

/** Coordinates the OAuth add-account flow. */
class GmailAuthManagerImpl(
    private val context: Context,
    private val store: GmailTokenStore,
    private val tokens: GmailTokenManager,
    private val accountRepository: AccountRepository,
) : GmailAuthManager {

    private val service = AuthorizationService(context)

    override fun authorizationIntent(): Intent =
        service.getAuthorizationRequestIntent(GmailOAuth.authorizationRequest())

    override suspend fun handleCallback(data: Intent): EmailAccount = withContext(Dispatchers.IO) {
        val response = AuthorizationResponse.fromIntent(data)
        val error = AuthorizationException.fromIntent(data)
        if (response == null) throw error ?: IllegalStateException("No auth response")
        if (response.authorizationCode == null) throw IllegalStateException("No authorization code")

        val exchange = performTokenRequest(response.createTokenExchangeRequest())

        val accessToken = exchange.accessToken.orEmpty()
        val refreshToken = exchange.refreshToken.orEmpty()
        val email = tokens.resolveEmail(accessToken)
        val info = tokens.resolveUserInfo(accessToken)

        val now = System.currentTimeMillis()
        val accountId = accountIdOf(email)
        // Keyed on the account id, which is what every read asks for.
        store.save(accountId, accessToken, refreshToken)

        val existing = accountRepository.accounts().first().firstOrNull { it.id == accountId }
        if (existing != null) {
            // Re-authorizing catches up a renamed account; what came back wins.
            val updated = existing.copy(
                authTokenRef = accountId,
                displayName = info.name ?: existing.displayName,
                photoUrl = info.picture ?: existing.photoUrl,
                updatedAt = now,
            )
            accountRepository.upsert(updated)
            updated
        } else {
            val account = EmailAccount(
                id = accountId,
                email = email,
                displayName = info.name ?: email,
                authTokenRef = accountId,
                enabled = true,
                createdAt = now,
                updatedAt = now,
                lastSyncAt = null,
                photoUrl = info.picture,
            )
            accountRepository.upsert(account)
            account
        }
    }

    /** Wraps AppAuth's callback-based token exchange in a suspend function. */
    private suspend fun performTokenRequest(request: TokenRequest): TokenResponse =
        suspendCancellableCoroutine { cont ->
            service.performTokenRequest(request) { response, error ->
                if (error != null) {
                    cont.resumeWithException(error)
                } else if (response != null) {
                    cont.resume(response)
                } else {
                    cont.resumeWithException(IllegalStateException("Token exchange failed"))
                }
            }
        }
}
