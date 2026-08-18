package dev.achmad.finbox.core.parser

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import dev.achmad.finbox.core.FinboxConfig
import dev.achmad.finbox.parser.TransactionSource
import java.io.File

/**
 * Loads parsers from private APK files in `filesDir/parsers/`.
 *
 * For each APK:
 * 1. Parses its manifest via [PackageManager.getPackageArchiveInfo]
 * 2. Validates the `finbox.parser` feature and `finbox.parser.lib`
 *    against [FinboxConfig.supportsLibVersion]
 * 3. Instantiates the [TransactionSource] named by `finbox.parser.class`
 *    via a [ChildFirstPathClassLoader] and pairs it with the identity from
 *    the manifest
 */
class ParserLoader(
    private val context: Context,
) {

    fun parsersDir(): File = File(context.filesDir, "parsers").apply { mkdirs() }

    fun loadParsers(): List<LoadResult> {
        val files = parsersDir().listFiles { f -> f.isFile && f.extension == "apk" }
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

        // The APK's own versionName/versionCode, not custom metadata: a parser
        // that fails to declare them is broken, not defaultable.
        val versionName = pkgInfo.versionName
        if (versionName.isNullOrEmpty()) {
            return LoadResult.Error(apk.name, "Missing versionName")
        }
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        // aapt stores "1.0" as a float in the binary manifest, so getString returns
        // null; 0f means the metadata is absent, in which case the versionName
        // prefix carries it ("1.0.3" -> 1.0).
        val libVersion = meta.getFloat("finbox.parser.lib")
            .takeUnless { it == 0f }
            // Via the string: widening the float straight to double reads 1.4 as
            // 1.3999999761581421, which is what the parser info screen then shows.
            ?.toString()
            ?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || !FinboxConfig.supportsLibVersion(libVersion)) {
            return LoadResult.Error(
                apk.name,
                "Unsupported parser lib version '$libVersion' (supported: " +
                    "${FinboxConfig.MIN_LIB_VERSION} to ${FinboxConfig.LIB_VERSION})",
            )
        }

        val className = meta.getString("finbox.parser.class")
            ?: return LoadResult.Error(apk.name, "Missing finbox.parser.class metadata")

        val info = InstalledParserInfo(
            pkg = pkgInfo.packageName,
            provider = meta.getString("finbox.parser.provider") ?: "unknown",
            name = meta.getString("finbox.parser.name") ?: apk.name,
            versionCode = versionCode.toInt(),
            versionName = versionName,
            libVersion = libVersion,
            className = className,
        )

        return try {
            // Also here, not only at install time: an APK written by an older
            // build of the app is still sitting there, still writable.
            if (apk.canWrite()) apk.setReadOnly()
            val classLoader = ChildFirstPathClassLoader(
                dexPath = apk.absolutePath,
                parent = javaClass.classLoader ?: context.classLoader,
                optimizedDirectory = context.codeCacheDir.absolutePath,
            )
            val clazz = Class.forName(className, false, classLoader)
            val source = clazz.getDeclaredConstructor().newInstance() as? TransactionSource
                ?: return LoadResult.Error(apk.name, "Class $className is not a TransactionSource")
            LoadResult.Success(
                info,
                LoadedSource(
                    id = sourceIdOf(info.name, info.versionCode),
                    pkg = info.pkg,
                    provider = info.provider,
                    name = info.name,
                    source = source,
                ),
            )
        } catch (e: Throwable) {
            LoadResult.Error(apk.name, "Failed to instantiate: ${e.message}")
        }
    }
}
