package dev.achmad.finbox.core

/**
 * App-wide constants.
 *
 * The OAuth client id isn't one of them: it belongs to whoever built the APK,
 * so it comes from `local.properties` as `oauthClientId` and reaches the app as
 * `BuildConfig.OAUTH_CLIENT_ID`.
 */
object FinboxConfig {

    /** The single extension repo index (see finbox-extension). */
    const val EXTENSION_INDEX_URL =
        "https://raw.githubusercontent.com/achmadss/finbox-extension/main/repo/index.json"

    const val OAUTH_REDIRECT_SCHEME = "dev.achmad.finbox"
    const val OAUTH_REDIRECT_URI = "dev.achmad.finbox:/oauth2callback"

    const val OAUTH_AUTHORIZE_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    const val OAUTH_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

    /** Gmail API access scopes. */
    const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

    /** Gmail API base. */
    const val GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me"

    /**
     * Supported parser API versions (checked by ExtensionLoader). An extension
     * declares its own via `finbox.extension.lib`, or by the leading components
     * of its versionName ("1.0.3" -> 1.0).
     */
    val SUPPORTED_LIB_VERSIONS = listOf(1.3)

    /**
     * Whether a parser API version is one this app can load.
     *
     * Compared with a tolerance rather than for equality: an APK's version comes
     * out of the binary manifest as a float, and 1.2f widens to
     * 1.2000000476837158, which no Double literal here will ever match.
     */
    fun supportsLibVersion(version: Double): Boolean =
        SUPPORTED_LIB_VERSIONS.any { kotlin.math.abs(it - version) < 0.001 }
}
