package dev.achmad.finbox.core.extension

import androidx.annotation.DrawableRes
import dev.achmad.finbox.R

/**
 * An extension's icon, shipped in the app's own resources.
 *
 * A `when` rather than a name lookup: `getIdentifier` is reflection by string,
 * which R8 cannot see through and a typo turns into a blank row at runtime.
 * Four entries; when it reaches twenty, a map.
 */
@DrawableRes
fun extensionIcon(id: String): Int? = when (id) {
    "bni" -> R.drawable.ic_extension_bni
    "bri" -> R.drawable.ic_extension_bri
    "jago" -> R.drawable.ic_extension_jago
    "mandiri" -> R.drawable.ic_extension_mandiri
    else -> null
}
