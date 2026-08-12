package dev.achmad.finbox.core

/**
 * App-wide constants.
 *
 * TODO(auth): create OAuth 2.0 credentials at
 * https://console.cloud.google.com/apis/credentials ("Mobile and desktop
 * applications" client), enable the Gmail API, then fill in [OAUTH_CLIENT_ID].
 * Keep the client in "Testing" mode for personal use (up to 100 users).
 */
object FinboxConfig {

    /** The single extension repo index (see finbox-extension). */
    const val EXTENSION_INDEX_URL =
        "https://raw.githubusercontent.com/achmadss/finbox-extension/main/repo/index.json"

    /** OAuth client id for the finbox app (set up on Google Cloud Console). */
    const val OAUTH_CLIENT_ID = "REPLACE_WITH_YOUR_CLIENT_ID.apps.googleusercontent.com"

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
    val SUPPORTED_LIB_VERSIONS = listOf(1.2)
}
