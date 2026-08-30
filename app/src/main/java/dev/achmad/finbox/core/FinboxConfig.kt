package dev.achmad.finbox.core

/**
 * App-wide constants.
 *
 * The OAuth client id is not here: it belongs to whoever built the APK, and
 * arrives as `BuildConfig.OAUTH_CLIENT_ID` from `local.properties`.
 */
object FinboxConfig {

    /** Where a newer build of the app itself is published. */
    const val APP_RELEASES_URL =
        "https://api.github.com/repos/achmadss/finbox-android/releases/latest"

    const val OAUTH_REDIRECT_SCHEME = "dev.achmad.finbox"
    const val OAUTH_REDIRECT_URI = "dev.achmad.finbox:/oauth2callback"

    const val OAUTH_AUTHORIZE_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    const val OAUTH_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

    /**
     * The consent screen asks for the mail plus the account's name and picture.
     * `profile` is non-sensitive, so it costs a consent line, not a verification review.
     */
    const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly openid profile"

    const val GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me"

    /** Needs `profile`, declared in [GMAIL_SCOPE]. */
    const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"
}
