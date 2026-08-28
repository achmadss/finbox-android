package dev.achmad.finbox.core.extension

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import dev.achmad.finbox.core.FinboxConfig
import dev.achmad.finbox.extension.Source

/** The feature an extension APK declares, which is how it is found at all. */
const val EXTENSION_FEATURE = "dev.achmad.finbox.extension"

private const val METADATA_CLASS = "finbox.extension.class"
private const val METADATA_NAME = "finbox.extension.name"
private const val METADATA_LIB = "finbox.extension.lib"
private const val METADATA_COUNTRY = "finbox.extension.country"

/**
 * Finds extensions among the device's installed packages and loads their code.
 *
 * They are ordinary apps: `adb install` puts one on a device and this finds it,
 * which is the whole point of installing rather than copying an APK into
 * filesDir. The repo is then a way to distribute extensions rather than the only
 * way to get one onto a phone.
 */
class ExtensionLoader(
    private val context: Context,
    private val trust: ExtensionTrust,
) {

    fun loadExtensions(): List<LoadResult> = installedPackages().map { load(it) }

    /**
     * Every installed package declaring the finbox feature.
     *
     * By feature rather than by a package-name prefix, so an extension is free
     * to be called anything. The package manager cannot filter by feature, hence
     * QUERY_ALL_PACKAGES in the manifest and this filter here.
     */
    private fun installedPackages(): List<PackageInfo> =
        context.packageManager.getInstalledPackages(PACKAGE_FLAGS)
            .filter { pkg -> pkg.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE } }
            .sortedBy { it.packageName }

    fun packageInfoOf(pkg: String): PackageInfo? = try {
        context.packageManager.getPackageInfo(pkg, PACKAGE_FLAGS)
            .takeIf { info -> info.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE } }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun load(pkgInfo: PackageInfo): LoadResult {
        val pkg = pkgInfo.packageName
        val meta = pkgInfo.applicationInfo?.metaData
            ?: return LoadResult.Error(pkg, "No metadata: not a finbox extension")

        // The APK's own versionName/versionCode: an extension that fails to
        // declare them is broken, not defaultable.
        val versionName = pkgInfo.versionName
        if (versionName.isNullOrEmpty()) return LoadResult.Error(pkg, "Missing versionName")
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        // aapt stores "2.0" as a float, so getString returns null; 0f means the
        // metadata is absent and the versionName prefix carries it ("2.0.3" -> 2.0).
        val libVersion = meta.getFloat(METADATA_LIB)
            .takeUnless { it == 0f }
            // Via the string: widening the float to double reads 1.4 as
            // 1.3999999761581421.
            ?.toString()
            ?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || !FinboxConfig.supportsLibVersion(libVersion)) {
            return LoadResult.Error(
                pkg,
                "Built for extension API $libVersion; this app supports " +
                    FinboxConfig.SUPPORTED_LIB_VERSIONS.joinToString(", "),
            )
        }

        val className = meta.getString(METADATA_CLASS)
            ?: return LoadResult.Error(pkg, "Missing $METADATA_CLASS metadata")

        val info = InstalledExtensionInfo(
            pkg = pkg,
            name = meta.getString(METADATA_NAME) ?: pkg,
            versionCode = versionCode.toInt(),
            versionName = versionName,
            libVersion = libVersion,
            country = meta.getString(METADATA_COUNTRY).orEmpty(),
            className = className,
            signature = trust.signatureOf(pkgInfo),
        )

        // Before Class.forName, and the reason this check exists at all: an
        // extension runs in this process and parse() is handed email bodies.
        // An untrusted one is listed, so the user can see it and decide, and
        // never loaded.
        if (!trust.isTrusted(info.pkg, info.signature)) {
            return LoadResult.Untrusted(info)
        }

        return try {
            val classLoader = ChildFirstPathClassLoader(
                dexPath = pkgInfo.applicationInfo!!.sourceDir,
                parent = javaClass.classLoader ?: context.classLoader,
                optimizedDirectory = context.codeCacheDir.absolutePath,
            )
            val clazz = Class.forName(className, false, classLoader)
            // Asked what it is, not cast to one fixed thing: an extension
            // declares its capabilities by implementing them, so this is the
            // only place that needs to know a second kind exists.
            val source = clazz.getDeclaredConstructor().newInstance() as? Source
                ?: return LoadResult.Error(pkg, "Class $className implements no Source")
            LoadResult.Success(
                info,
                LoadedExtension(id = info.pkg, name = info.name, source = source),
            )
        } catch (e: Throwable) {
            LoadResult.Error(pkg, "Failed to instantiate: ${e.message}")
        }
    }

    private companion object {
        val PACKAGE_FLAGS = PackageManager.GET_META_DATA or
            PackageManager.GET_CONFIGURATIONS or
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES or
            PackageManager.GET_SIGNING_CERTIFICATES
    }
}
