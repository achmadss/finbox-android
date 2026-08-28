package dev.achmad.finbox.core.parser

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
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
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.drawable.toBitmap
import dev.achmad.data.model.InstalledParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Painter for an installed parser's own icon, read from its APK. */
@Composable
fun rememberParserPainter(parser: InstalledParser): Painter {
    val context = LocalContext.current
    val densityDpi = (LocalDensity.current.density * DisplayMetrics.DENSITY_DEFAULT).toInt()
    val icon by produceState<ImageBitmap?>(initialValue = null, parser.file) {
        value = withContext(Dispatchers.IO) {
            loadApkIcon(context, parser.file, densityDpi)?.toBitmap()?.asImageBitmap()
        }
    }
    val fallback = rememberVectorPainter(Icons.Filled.Extension)
    return icon?.let { BitmapPainter(it) } ?: fallback
}

/**
 * Reads a parser's launcher icon out of its APK.
 *
 * The APK is never installed as a package, so [ApplicationInfo.sourceDir] must
 * be pointed back at the file before its resources can be read.
 *
 * Blocking; call it off the main thread. Null when the APK is gone or declares
 * no icon.
 */
private fun loadApkIcon(context: Context, apkPath: String, density: Int): Drawable? = try {
    val pm = context.packageManager
    val appInfo = pm.getPackageArchiveInfo(apkPath, 0)?.applicationInfo
    appInfo?.sourceDir = apkPath
    appInfo?.publicSourceDir = apkPath
    appInfo
        ?.takeIf { it.icon != 0 && File(apkPath).exists() }
        ?.let { pm.getResourcesForApplication(it).getDrawableForDensity(it.icon, density, null) }
} catch (e: Exception) {
    null
}
