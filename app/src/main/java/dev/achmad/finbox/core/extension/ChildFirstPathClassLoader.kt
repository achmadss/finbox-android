package dev.achmad.finbox.core.extension

import dalvik.system.DexClassLoader

/**
 * Classloader that checks the extension APK's own classes before delegating
 * to the app classloader (parent-last). This lets extensions define classes
 * with the same FQNs as app-provided APIs while still being resolved against
 * the app's versions of shared dependencies (OkHttp, jsoup, serialization...).
 */
class ChildFirstPathClassLoader(
    dexPath: String,
    parent: ClassLoader,
    optimizedDirectory: String,
) : DexClassLoader(dexPath, optimizedDirectory, null, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        var c = findLoadedClass(name)
        if (c == null) {
            c = try {
                findClass(name)
            } catch (e: ClassNotFoundException) {
                super.loadClass(name, resolve)
            }
        }
        if (resolve) resolveClass(c)
        return c
    }
}
