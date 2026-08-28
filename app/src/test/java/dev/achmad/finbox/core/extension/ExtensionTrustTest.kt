package dev.achmad.finbox.core.extension

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionTrustTest {

    private val trust = ExtensionTrust(FakePreferenceStore())

    private val bri = "dev.achmad.finbox.extension.bri"
    private val signature = ExtensionTrust.hash("a signing certificate".toByteArray())
    private val other = ExtensionTrust.hash("someone else's certificate".toByteArray())

    @Test
    fun `an unknown signature is not trusted`() {
        assertFalse(trust.isTrusted(bri, signature))
    }

    @Test
    fun `trusting a package allows exactly that package and signature`() {
        trust.trust(bri, signature)

        assertTrue(trust.isTrusted(bri, signature))
        // The same signer, a different package: trusting an author for one
        // extension is not trusting them for everything they ever sign.
        assertFalse(trust.isTrusted("dev.achmad.finbox.extension.bni", signature))
    }

    @Test
    fun `a package resigned by someone else is untrusted again`() {
        trust.trust(bri, signature)

        // Same package name, different signer. That is a different author, and
        // the package name is the part an attacker gets to choose.
        assertFalse(trust.isTrusted(bri, other))
    }

    @Test
    fun `an unreadable signature fails closed`() {
        // signatureOf returns empty when it cannot read one. Empty must never
        // match, including right after a trust() call that was handed it.
        trust.trust(bri, "")

        assertFalse(trust.isTrusted(bri, ""))
    }

    @Test
    fun `revoking removes every entry for that package`() {
        trust.trust(bri, signature)
        trust.trust(bri, other)

        trust.revoke(bri)

        assertFalse(trust.isTrusted(bri, signature))
        assertFalse(trust.isTrusted(bri, other))
    }
}
