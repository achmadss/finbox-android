package dev.achmad.finbox.core.extension

import dalvik.system.DexClassLoader

/**
 * Checks the extension APK's classes before delegating to the app classloader, so
 * extensions can define classes with the same FQNs as app-provided APIs while
 * shared dependencies still resolve to the app's versions.
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
