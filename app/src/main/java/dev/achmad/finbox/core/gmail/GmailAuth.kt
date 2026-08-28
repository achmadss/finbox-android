package dev.achmad.finbox.core.gmail

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.achmad.finbox.BuildConfig
import dev.achmad.finbox.core.FinboxConfig
import dev.achmad.finbox.util.network.get
import dev.achmad.finbox.util.network.json
import dev.achmad.finbox.util.network.parseAs
import dev.achmad.finbox.util.network.post
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.achmad.finbox.core.gmail.model.TokenResponse
import dev.achmad.finbox.core.gmail.model.ProfileResponse
import dev.achmad.finbox.core.gmail.model.UserInfoResponse

/** Per-account OAuth tokens, stored in Keystore-backed encrypted prefs. */
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
                .add("client_id", BuildConfig.OAUTH_CLIENT_ID)
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

    /**
     * Resolves the email of the account just authorized (also the dedup key).
     *
     * Profile rather than OpenID userinfo: it names the mailbox being
     * identified, and fails loudly when the Gmail scope is missing.
     */
    suspend fun resolveEmail(accessToken: String): String {
        // Deliberately not swallowed: this first call is what the sign-in
        // toast can show when the account fails (403 API disabled, 401 bad token).
        val response = client.get(
            url = "${FinboxConfig.GMAIL_API_BASE}/profile",
            headers = Headers.headersOf("Authorization", "Bearer $accessToken"),
            cacheControl = null,
            ensureSuccess = false,
        )
        val body = response.body.string()
        if (!response.isSuccessful) error("profile ${response.code}: ${body.take(300)}")
        return json.decodeFromString<ProfileResponse>(body).emailAddress
            .ifEmpty { error("Gmail returned a profile with no email address") }
    }

    /**
     * Name and picture of the account just authorized, or nulls. Swallowed,
     * unlike [resolveEmail]: sign-in works without either.
     */
    suspend fun resolveUserInfo(accessToken: String): UserInfoResponse =
        runCatching {
            client.get(
                url = FinboxConfig.USERINFO_ENDPOINT,
                headers = Headers.headersOf("Authorization", "Bearer $accessToken"),
                cacheControl = null,
            ).parseAs<UserInfoResponse>()
        }.getOrElse { UserInfoResponse() }
}

object GmailOAuth {
    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.Builder(
            AuthorizationServiceConfiguration(
                FinboxConfig.OAUTH_AUTHORIZE_ENDPOINT.toUri(),
                FinboxConfig.OAUTH_TOKEN_ENDPOINT.toUri(),
            ),
            BuildConfig.OAUTH_CLIENT_ID,
            ResponseTypeValues.CODE,
            FinboxConfig.OAUTH_REDIRECT_URI.toUri(),
        )
            .setScope(FinboxConfig.GMAIL_SCOPE)
            // select_account adds a second account; consent makes Google
            // reissue a refresh token.
            .setPrompt("select_account consent")
            .build()
}
