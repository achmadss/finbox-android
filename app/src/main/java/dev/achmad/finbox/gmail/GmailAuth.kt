package dev.achmad.finbox.gmail

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.achmad.finbox.config.FinboxConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

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
        prefs.edit()
            .putString("access_$accountId", accessToken)
            .putString("refresh_$accountId", refreshToken)
            .apply()
    }

    fun accessToken(accountId: String): String? = prefs.getString("access_$accountId", null)

    fun refreshToken(accountId: String): String? = prefs.getString("refresh_$accountId", null)

    fun clear(accountId: String) {
        prefs.edit()
            .remove("access_$accountId")
            .remove("refresh_$accountId")
            .apply()
    }
}

@Serializable
data class TokenResponse(
    val access_token: String = "",
    val refresh_token: String? = null,
)

@Serializable
data class UserInfo(val email: String = "")

/** Refreshes access tokens and resolves the account email from the token. */
class GmailTokenManager(
    private val store: GmailTokenStore,
    private val client: OkHttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun refreshAccessToken(accountId: String): String? = withContext(Dispatchers.IO) {
        val refreshToken = store.refreshToken(accountId) ?: return@withContext null
        runCatching {
            val form = FormBody.Builder()
                .add("client_id", FinboxConfig.OAUTH_CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()
            val request = Request.Builder()
                .url(FinboxConfig.OAUTH_TOKEN_ENDPOINT)
                .post(form)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Token refresh failed: ${response.code}")
                val parsed = json.decodeFromString<TokenResponse>(response.body?.string().orEmpty())
                store.save(accountId, parsed.access_token, refreshToken)
                parsed.access_token
            }
        }.getOrNull()
    }

    suspend fun getAccessToken(accountId: String): String? {
        store.accessToken(accountId)?.let { return it }
        return refreshAccessToken(accountId)
    }

    /** Resolves the email of the account just authorized (also the dedup key). */
    suspend fun resolveEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://openidconnect.googleapis.com/v1/userinfo")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                json.decodeFromString<UserInfo>(response.body?.string().orEmpty()).email
            }
        }.getOrNull()
    }
}

object GmailOAuth {
    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.Builder(
            AuthorizationServiceConfiguration(
                Uri.parse(FinboxConfig.OAUTH_AUTHORIZE_ENDPOINT),
                Uri.parse(FinboxConfig.OAUTH_TOKEN_ENDPOINT),
            ),
            FinboxConfig.OAUTH_CLIENT_ID,
            FinboxConfig.OAUTH_REDIRECT_URI,
            Uri.parse(FinboxConfig.GMAIL_SCOPE),
        ).build()
}
