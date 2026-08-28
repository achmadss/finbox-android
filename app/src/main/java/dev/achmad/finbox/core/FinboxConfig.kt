package dev.achmad.finbox.core

/**
 * App-wide constants.
 *
 * The OAuth client id is not here: it belongs to whoever built the APK, and
 * arrives as `BuildConfig.OAUTH_CLIENT_ID` from `local.properties`.
 */
object FinboxConfig {

    /** The single parser repo index (see finbox-parser). */
    const val PARSER_INDEX_URL =
        "https://raw.githubusercontent.com/achmadss/finbox-parser/main/repo/index.json"

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

    /**
     * The parser API this app ships. A parser declares it via `finbox.parser.lib`,
     * or by the leading components of its versionName ("1.0.5" -> 1.0).
     */
    const val LIB_VERSION = 1.0

    /**
     * The oldest parser API still loadable. Raising it orphans every APK below it,
     * so it moves only on a breaking change.
     */
    const val MIN_LIB_VERSION = 1.0

    /**
     * Whether a parser API version is one this app can load.
     *
     * The tolerance absorbs the float widening of an APK's reported version:
     * 1.2f reads back as 1.2000000476837158.
     *
     * ponytail: a Double sorts 1.10 below 1.9. Fine while minor versions stay
     * single-digit.
     */
    fun supportsLibVersion(version: Double): Boolean =
        version >= MIN_LIB_VERSION - VERSION_TOLERANCE &&
            version <= LIB_VERSION + VERSION_TOLERANCE

    private const val VERSION_TOLERANCE = 0.001
}
