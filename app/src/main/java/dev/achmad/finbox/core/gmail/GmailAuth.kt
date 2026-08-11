package dev.achmad.finbox.core.gmail

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.achmad.finbox.core.FinboxConfig
import dev.achmad.finbox.core.network.get
import dev.achmad.finbox.core.network.parseAs
import dev.achmad.finbox.core.network.post
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.achmad.finbox.core.gmail.model.TokenResponse
import dev.achmad.finbox.core.gmail.model.UserInfo

/**
 * Per-account OAuth tokens, stored in Keystore-backed encrypted prefs.
 * Each account has its own access + refresh token (one OAuth client id,
 * account picker per authorization flow).
 */
class GmailTokenStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "gmail_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(accountId: String, accessToken: String, refreshToken: String) {
        prefs.edit {
            putString("access_$accountId", accessToken)
                .putString("refresh_$accountId", refreshToken)
        }
    }

    fun accessToken(accountId: String): String? = prefs.getString("access_$accountId", null)

    fun refreshToken(accountId: String): String? = prefs.getString("refresh_$accountId", null)

    fun clear(accountId: String) {
        prefs.edit {
            remove("access_$accountId")
                .remove("refresh_$accountId")
        }
    }
}

/** Refreshes access tokens and resolves the account email from the token. */
class GmailTokenManager(
    private val store: GmailTokenStore,
    private val client: OkHttpClient,
) {

    suspend fun refreshAccessToken(accountId: String): String? {
        val refreshToken = store.refreshToken(accountId) ?: return null
        return runCatching {
            val form = FormBody.Builder()
                .add("client_id", FinboxConfig.OAUTH_CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()
            val parsed = client.post(FinboxConfig.OAUTH_TOKEN_ENDPOINT, form)
                .parseAs<TokenResponse>()
            store.save(accountId, parsed.accessToken, refreshToken)
            parsed.accessToken
        }.getOrNull()
    }

    suspend fun getAccessToken(accountId: String): String? {
        store.accessToken(accountId)?.let { return it }
        return refreshAccessToken(accountId)
    }

    /** Resolves the email of the account just authorized (also the dedup key). */
    suspend fun resolveEmail(accessToken: String): String? = runCatching {
        client.get(
            url = "https://openidconnect.googleapis.com/v1/userinfo",
            headers = Headers.headersOf("Authorization", "Bearer $accessToken"),
            cacheControl = null,
        ).parseAs<UserInfo>().email
    }.getOrNull()
}

object GmailOAuth {
    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.Builder(
            AuthorizationServiceConfiguration(
                FinboxConfig.OAUTH_AUTHORIZE_ENDPOINT.toUri(),
                FinboxConfig.OAUTH_TOKEN_ENDPOINT.toUri(),
            ),
            FinboxConfig.OAUTH_CLIENT_ID,
            FinboxConfig.OAUTH_REDIRECT_URI,
            FinboxConfig.GMAIL_SCOPE.toUri(),
        ).build()
}
