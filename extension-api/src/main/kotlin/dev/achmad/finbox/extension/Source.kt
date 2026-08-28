package dev.achmad.finbox.extension

/**
 * A capability an extension has. Everything an extension can read extends this.
 *
 * A capability that is a *type* cannot lie: implementing the interface is what
 * declaring it means, and the compiler enforces the rest. An extension that
 * declared its sources in a list could claim one it does not handle, which
 * would then need validating at load time and could still be wrong.
 *
 * Only [EmailSource] exists so far. A second one is a new interface and no
 * existing signature changes, which is the whole reason the contract admits
 * them now rather than being reshaped later.
 */
interface Source
