package dev.achmad.finbox.core.extension

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import dev.achmad.data.model.InstalledExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Painter for an installed extension's own icon.
 *
 * It is an installed app, so the icon is already on the device and the system
 * picks the right density. Nothing is downloaded and nothing is cached — the
 * repo's `iconUrl` is only for extensions that are not installed, where there
 * is no package to ask.
 */
@Composable
fun rememberExtensionPainter(extension: InstalledExtension): Painter {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, extension.pkg) {
        value = withContext(Dispatchers.IO) {
            packageIcon(context, extension.pkg)?.toBitmap()?.asImageBitmap()
        }
    }
    val fallback = rememberVectorPainter(Icons.Filled.Extension)
    return icon?.let { BitmapPainter(it) } ?: fallback
}

/** Blocking; call it off the main thread. Null when the package is gone. */
private fun packageIcon(context: Context, pkg: String): Drawable? = try {
    context.packageManager.getApplicationIcon(pkg)
} catch (e: PackageManager.NameNotFoundException) {
    null
}
