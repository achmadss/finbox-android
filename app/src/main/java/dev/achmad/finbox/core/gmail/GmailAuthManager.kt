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
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Adding an account: an intent a screen launches for result, and the account
 * that comes back out of the result.
 *
 * An interface for the same reason [GmailApi] is one — a debug build stands in
 * a fake so testing needs no Google account. See `di/GmailModule.kt`.
 */
interface GmailAuthManager {

    /**
     * The authorization flow (account picker), for a screen to launch for
     * result.
     *
     * Returned rather than started here: AppAuth reports back through
     * `setResult`, so it has to be started from an Activity, and this manager
     * only holds the application context.
     */
    fun authorizationIntent(): Intent

    /** Called with the result of [authorizationIntent]. Returns the added account. */
    suspend fun handleCallback(data: Intent): EmailAccount
}

/**
 * Coordinates the OAuth "add account" flow. Each launch shows Google's
 * account picker, so multiple accounts can be added with a single OAuth
 * client id; each account gets its own token pair.
 */
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

        val now = System.currentTimeMillis()
        val accountId = UUID.randomUUID().toString()
        store.save(accountId, accessToken, refreshToken)

        val existing = accountRepository.accounts().first().firstOrNull { it.email == email }
        if (existing != null) {
            accountRepository.upsert(existing.copy(authTokenRef = accountId, updatedAt = now))
            existing.copy(authTokenRef = accountId, updatedAt = now)
        } else {
            val account = EmailAccount(
                id = accountId,
                email = email,
                displayName = email,
                authTokenRef = accountId,
                enabled = true,
                createdAt = now,
                updatedAt = now,
                lastSyncAt = null,
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
