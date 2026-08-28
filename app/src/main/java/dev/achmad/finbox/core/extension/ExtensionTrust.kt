package dev.achmad.finbox.core.extension

import android.content.pm.PackageInfo
import android.os.Build
import androidx.annotation.VisibleForTesting
import dev.achmad.finbox.util.preference.PreferenceStore
import java.security.MessageDigest

/**
 * Which extensions are allowed to run.
 *
 * An extension is arbitrary code loaded into this process, and `parse()` is
 * handed email bodies — the most sensitive thing the app holds. While extensions
 * were downloaded from one repo, the index's sha256 was the whole of the trust:
 * one source, hashes checked, nothing else got in. Installing them as apps
 * removes that, because the point is that an APK can now arrive from anywhere.
 *
 * So the signing certificate is the trust instead. A signature is stable across
 * an author's releases and cannot be forged by renaming a package, which is what
 * a hash of one particular build cannot say.
 *
 * The index's sha256 still verifies that a download arrived intact. It is no
 * longer the security boundary.
 */
class ExtensionTrust(
    private val preferenceStore: PreferenceStore,
) {

    /**
     * `pkg:signature` for everything the user has allowed.
     *
     * The pair, not the signature alone: trusting a signer for one extension is
     * not trusting them for every package they ever sign.
     */
    private val trusted = preferenceStore.getStringSet("trusted_extensions", DEFAULT_TRUSTED)

    fun isTrusted(pkg: String, signature: String): Boolean =
        signature.isNotEmpty() && key(pkg, signature) in trusted.get()

    /**
     * Allows this exact package and signature.
     *
     * If the same package is later signed by someone else it is untrusted again,
     * because the old entry does not match — which is the point. A different
     * signer is a different author, whatever the package name says.
     */
    fun trust(pkg: String, signature: String) {
        if (signature.isEmpty()) return
        trusted.set(trusted.get() + key(pkg, signature))
    }

    fun revoke(pkg: String) {
        trusted.set(trusted.get().filterNot { it.startsWith("$pkg:") }.toSet())
    }

    fun changes() = trusted.changes()

    /**
     * SHA-256 of the package's signing certificate, or empty when it has none
     * that can be read.
     *
     * Empty never matches a trusted entry, so an unreadable signature fails
     * closed.
     */
    fun signatureOf(pkgInfo: PackageInfo): String {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.signingInfo?.let {
                if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
        // Exactly one signer, or none of them is "the" signature: a package
        // signed by several parties is not something to reduce to one string.
        val signature = signatures?.singleOrNull() ?: return ""
        return hash(signature.toByteArray())
    }

    private fun key(pkg: String, signature: String) = "$pkg:$signature"

    companion object {
        @VisibleForTesting
        fun hash(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }

        /**
         * The signature the official extensions are published under, trusted
         * without asking.
         *
         * Empty for now, and deliberately so: the published extensions are
         * signed with a debug keystore, whose certificate differs per machine.
         * Pinning one here would trust whichever machine last built them and
         * nobody else. It gets a value when the extensions are signed with a
         * release key.
         *
         * ponytail: until then every extension prompts once, including the
         * official ones. Correct, just not friendly.
         */
        val DEFAULT_TRUSTED: Set<String> = emptySet()
    }
}
