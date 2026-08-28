package dev.achmad.finbox.core.extension

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import dev.achmad.finbox.core.FinboxConfig
import dev.achmad.finbox.extension.EmailSource
import java.io.File

/** Loads extensions from the APK files in `filesDir/extensions/`. */
class ExtensionLoader(
    private val context: Context,
) {

    fun extensionsDir(): File = File(context.filesDir, "extensions").apply { mkdirs() }

    fun loadExtensions(): List<LoadResult> {
        val files = extensionsDir().listFiles { f -> f.isFile && f.extension == "apk" }
            .orEmpty()
            .sortedBy { it.name }
        return files.map { loadApk(it) }
    }

    fun loadApk(apk: File): LoadResult {
        val pkgInfo = try {
            context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.GET_META_DATA,
            )
        } catch (e: Exception) {
            null
        }
        val meta = pkgInfo?.applicationInfo?.metaData
            ?: return LoadResult.Error(apk.name, "Invalid or unreadable APK")

        // The APK's own versionName/versionCode: an extension that fails to declare
        // them is broken, not defaultable.
        val versionName = pkgInfo.versionName
        if (versionName.isNullOrEmpty()) {
            return LoadResult.Error(apk.name, "Missing versionName")
        }
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        // aapt stores "1.0" as a float, so getString returns null; 0f means the
        // metadata is absent and the versionName prefix carries it ("1.0.3" -> 1.0).
        val libVersion = meta.getFloat("finbox.extension.lib")
            .takeUnless { it == 0f }
            // Via the string: widening the float to double reads 1.4 as
            // 1.3999999761581421.
            ?.toString()
            ?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || !FinboxConfig.supportsLibVersion(libVersion)) {
            return LoadResult.Error(
                apk.name,
                "Unsupported extension lib version '$libVersion' (supported: " +
                    "${FinboxConfig.MIN_LIB_VERSION} to ${FinboxConfig.LIB_VERSION})",
            )
        }

        val className = meta.getString("finbox.extension.class")
            ?: return LoadResult.Error(apk.name, "Missing finbox.extension.class metadata")

        val info = InstalledExtensionInfo(
            pkg = pkgInfo.packageName,
            provider = meta.getString("finbox.extension.provider") ?: "unknown",
            name = meta.getString("finbox.extension.name") ?: apk.name,
            versionCode = versionCode.toInt(),
            versionName = versionName,
            libVersion = libVersion,
            className = className,
        )

        return try {
            // Also here, not only at install time: an APK from an older build
            // is still sitting there, still writable.
            if (apk.canWrite()) apk.setReadOnly()
            val classLoader = ChildFirstPathClassLoader(
                dexPath = apk.absolutePath,
                parent = javaClass.classLoader ?: context.classLoader,
                optimizedDirectory = context.codeCacheDir.absolutePath,
            )
            val clazz = Class.forName(className, false, classLoader)
            val extension = clazz.getDeclaredConstructor().newInstance() as? EmailSource
                ?: return LoadResult.Error(apk.name, "Class $className is not an EmailSource")
            LoadResult.Success(
                info,
                LoadedExtension(
                    id = extensionIdOf(info.pkg),
                    pkg = info.pkg,
                    provider = info.provider,
                    name = info.name,
                    extension = extension,
                ),
            )
        } catch (e: Throwable) {
            LoadResult.Error(apk.name, "Failed to instantiate: ${e.message}")
        }
    }
}
