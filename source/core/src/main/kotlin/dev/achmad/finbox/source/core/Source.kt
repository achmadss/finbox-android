package dev.achmad.finbox.source.core

/**
 * What a source can do. Everything a source can read extends this.
 *
 * A capability that is a *type* cannot lie: implementing the interface is what
 * declaring it means, and the compiler enforces the rest. A source that
 * declared its capabilities in a list could claim one it does not handle, which
 * would then need validating somewhere and could still be wrong.
 *
 * Nothing here says which bank it is. Identity lives in the module's build file
 * and reaches the app as a [SourceEntry], so a class in `source/lib` is only
 * ever the reading of one bank's mail.
 *
 * Only [dev.achmad.finbox.source.core.email.EmailSource] exists so far. A second
 * kind is a new interface extending this one, and no existing signature changes.
 */
interface Source
