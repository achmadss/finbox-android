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
     * The parser API this app ships. An extension declares the one it was built
     * against via `finbox.extension.lib`, or by the leading components of its
     * versionName ("1.3.5" -> 1.3).
     */
    const val LIB_VERSION = 1.3

    /**
     * The oldest parser API still loadable.
     *
     * Adding to the API raises [LIB_VERSION] and leaves this alone, so every
     * extension already published keeps working — it simply never uses what it
     * doesn't know about. Only a change that breaks an existing extension
     * raises this, and that is meant to hurt: it orphans every APK below it
     * until each is rebuilt.
     *
     * It starts at 1.3 rather than 1.0 because `TransactionSource.emailQuery`
     * is abstract, so a 1.2 extension throws AbstractMethodError on first use.
     */
    const val MIN_LIB_VERSION = 1.3

    /**
     * Whether a parser API version is one this app can load.
     *
     * Bounded rather than matched exactly, and with a tolerance at each end: an
     * APK's version comes out of the binary manifest as a float, and 1.2f
     * widens to 1.2000000476837158, which no Double literal here will ever
     * equal.
     *
     * ponytail: version as a Double, so 1.10 sorts below 1.9. Fine while minor
     * versions stay single-digit; past that this needs a real comparison.
     */
    fun supportsLibVersion(version: Double): Boolean =
        version >= MIN_LIB_VERSION - VERSION_TOLERANCE &&
            version <= LIB_VERSION + VERSION_TOLERANCE

    private const val VERSION_TOLERANCE = 0.001
}
