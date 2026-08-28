package dev.achmad.finbox.core

/**
 * App-wide constants.
 *
 * The OAuth client id is not here: it belongs to whoever built the APK, and
 * arrives as `BuildConfig.OAUTH_CLIENT_ID` from `local.properties`.
 */
object FinboxConfig {

    /** The single extension repo index (see finbox-extension). */
    const val EXTENSION_INDEX_URL =
        "https://raw.githubusercontent.com/achmadss/finbox-extension/main/repo/index.json"

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
     * Every extension API version this app can load, newest last.
     *
     * A list of exact versions, not a range with a floor and a ceiling. A range
     * says "everything between these", which is a promise about versions that
     * do not exist yet and about ones nobody checked. This says which builds
     * were actually kept working.
     *
     * **The API may only grow.** There is one set of classes at runtime — the
     * app's — and ChildFirstPathClassLoader resolves an extension's compileOnly
     * references against them, so an older extension keeps loading exactly as
     * long as nothing it referenced was removed or changed shape. Nothing
     * enforces that: not the compiler, not a test. It fails at Class.forName on
     * a stranger's phone, months later. Adding a version here is a claim that
     * the change since the last one was additive.
     *
     * 2.0 rather than 1.1 because the refactor deleted EmailParser, methods()
     * and TransactionMethod. No versioning keeps a 1.0 extension alive against
     * classes that are gone, so the major number is the honest signal that
     * nothing below it can load.
     */
    val SUPPORTED_LIB_VERSIONS = listOf(2.0)

    /** The version a freshly built extension should declare. */
    val CURRENT_LIB_VERSION = SUPPORTED_LIB_VERSIONS.last()

    /**
     * Whether an extension API version is one this app can load.
     *
     * The tolerance absorbs the float widening of an APK's reported version:
     * 1.2f reads back as 1.2000000476837158.
     */
    fun supportsLibVersion(version: Double): Boolean =
        SUPPORTED_LIB_VERSIONS.any { kotlin.math.abs(it - version) < VERSION_TOLERANCE }

    private const val VERSION_TOLERANCE = 0.001
}
