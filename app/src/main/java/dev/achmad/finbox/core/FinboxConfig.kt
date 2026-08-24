package dev.achmad.finbox.core

/**
 * App-wide constants.
 *
 * The OAuth client id isn't one of them: it belongs to whoever built the APK,
 * so it comes from `local.properties` as `oauthClientId` and reaches the app as
 * `BuildConfig.OAUTH_CLIENT_ID`.
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
     * What the consent screen asks for: the mail itself, and the name and
     * picture shown against the account. `profile` is non-sensitive — it adds a
     * line to the consent screen, not a verification review, which the
     * restricted Gmail scope already requires.
     */
    const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly openid profile"

    /** Gmail API base. */
    const val GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me"

    /** Name and picture of whoever just signed in. Needs `profile` above. */
    const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"

    /**
     * The parser API this app ships. A parser declares the one it was built
     * against via `finbox.parser.lib`, or by the leading components of its
     * versionName ("1.0.5" -> 1.0).
     */
    const val LIB_VERSION = 1.0

    /**
     * The oldest parser API still loadable.
     *
     * Adding to the API raises [LIB_VERSION] and leaves this alone, so every
     * published parser keeps working. Only a breaking change raises this, and
     * that is meant to hurt: it orphans every APK below it until each is
     * rebuilt.
     */
    const val MIN_LIB_VERSION = 1.0

    /**
     * Whether a parser API version is one this app can load.
     *
     * The tolerance is not slack: an APK's version comes out of the binary
     * manifest as a float, and 1.2f widens to 1.2000000476837158, which no
     * Double literal here will ever equal.
     *
     * ponytail: version as a Double, so 1.10 sorts below 1.9. Fine while minor
     * versions stay single-digit; past that this needs a real comparison.
     */
    fun supportsLibVersion(version: Double): Boolean =
        version >= MIN_LIB_VERSION - VERSION_TOLERANCE &&
            version <= LIB_VERSION + VERSION_TOLERANCE

    private const val VERSION_TOLERANCE = 0.001
}
