package dev.achmad.finbox.extension

import android.content.Context
import android.content.pm.PackageManager
import dev.achmad.finbox.config.FinboxConfig
import dev.achmad.finbox.extension.TransactionSource
import dev.achmad.finbox.extension.SourceFactory
import java.io.File

/**
 * Loads parser extensions from private APK files in `filesDir/exts/`.
 *
 * For each APK:
 * 1. Parses its manifest via [PackageManager.getPackageArchiveInfo]
 * 2. Validates the `finbox.extension` feature and `finbox.extension.lib`
 *    against [FinboxConfig.SUPPORTED_LIB_VERSIONS]
 * 3. Instantiates the class in `finbox.extension.class` via a
 *    [ChildFirstPathClassLoader]; if it's a [SourceFactory] its sources are
 *    collected, otherwise it must be a [TransactionSource]
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
        if (meta == null || pkgInfo == null) {
            return LoadResult.Error(apk.name, "Invalid or unreadable APK")
        }

        // aapt stores "1.0" as a float in the binary manifest, so getString can
        // return null while getInt/getFloat return the numeric value.
        val libVersionRaw = meta.getString("finbox.extension.lib")
            ?: meta.getFloat("finbox.extension.lib").toString()
            ?: meta.getInt("finbox.extension.lib").toString()
        val libVersion = libVersionRaw.toDoubleOrNull()
            ?.let { d -> if (d == d.toInt().toDouble()) "${d.toInt()}.0" else d.toString() }
            ?: libVersionRaw
        if (libVersion !in FinboxConfig.SUPPORTED_LIB_VERSIONS) {
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
            versionCode = meta.getInt("finbox.extension.version_code", 1),
            versionName = meta.getString("finbox.extension.version_name")
                ?: pkgInfo.versionName ?: "1.0.1",
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
            val instance = clazz.getDeclaredConstructor().newInstance()
            val sources = when (instance) {
                is SourceFactory -> instance.createSources()
                is TransactionSource -> listOf(instance)
                else -> return LoadResult.Error(
                    apk.name,
                    "Class $className implements neither TransactionSource nor SourceFactory",
                )
            }
            if (sources.isEmpty()) {
                LoadResult.Error(apk.name, "Extension provides no sources")
            } else {
                LoadResult.Success(info, sources)
            }
        } catch (e: Throwable) {
            LoadResult.Error(apk.name, "Failed to instantiate: ${e.message}")
        }
    }
}
