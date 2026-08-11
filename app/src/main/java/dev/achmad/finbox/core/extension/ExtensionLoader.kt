package dev.achmad.finbox.core.extension

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import dev.achmad.finbox.core.FinboxConfig
import dev.achmad.finbox.extension.TransactionSource
import java.io.File

/**
 * Loads parser extensions from private APK files in `filesDir/exts/`.
 *
 * For each APK:
 * 1. Parses its manifest via [PackageManager.getPackageArchiveInfo]
 * 2. Validates the `finbox.extension` feature and `finbox.extension.lib`
 *    against [FinboxConfig.SUPPORTED_LIB_VERSIONS]
 * 3. Instantiates the [TransactionSource] named by `finbox.extension.class`
 *    via a [ChildFirstPathClassLoader] and pairs it with the identity from
 *    the manifest
 */
class ExtensionLoader(
    private val context: Context,
) {

    fun extsDir(): File = File(context.filesDir, "exts").apply { mkdirs() }

    fun loadExtensions(): List<LoadResult> {
        val files = extsDir().listFiles { f -> f.isFile && f.extension == "apk" }
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

        // The APK's own versionName/versionCode, not custom metadata: an extension
        // that fails to declare them is broken, not defaultable.
        val versionName = pkgInfo.versionName
        if (versionName.isNullOrEmpty()) {
            return LoadResult.Error(apk.name, "Missing versionName")
        }
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        // aapt stores "1.0" as a float in the binary manifest, so getString returns
        // null; 0f means the metadata is absent, in which case the versionName
        // prefix carries it ("1.0.3" -> 1.0).
        val libVersion = meta.getFloat("finbox.extension.lib")
            .takeUnless { it == 0f }
            ?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion !in FinboxConfig.SUPPORTED_LIB_VERSIONS) {
            return LoadResult.Error(
                apk.name,
                "Unsupported extension lib version '$libVersion' " +
                    "(supported: ${FinboxConfig.SUPPORTED_LIB_VERSIONS.joinToString()})",
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
            val classLoader = ChildFirstPathClassLoader(
                dexPath = apk.absolutePath,
                parent = javaClass.classLoader ?: context.classLoader,
                optimizedDirectory = context.codeCacheDir.absolutePath,
            )
            val clazz = Class.forName(className, false, classLoader)
            val source = clazz.getDeclaredConstructor().newInstance() as? TransactionSource
                ?: return LoadResult.Error(apk.name, "Class $className is not a TransactionSource")
            // An empty query would pull the entire mailbox on every sync.
            if (source.emailQuery.isEmpty) {
                return LoadResult.Error(apk.name, "Source declares an empty emailQuery")
            }
            LoadResult.Success(
                info,
                LoadedSource(
                    id = sourceIdOf(info.name, info.versionCode),
                    name = info.name,
                    source = source,
                ),
            )
        } catch (e: Throwable) {
            LoadResult.Error(apk.name, "Failed to instantiate: ${e.message}")
        }
    }
}
