package dev.achmad.finbox.extension.annotation

/**
 * The APK's entry point: exactly one class implementing at least one
 * [dev.achmad.finbox.extension.Source], with a no-argument constructor.
 *
 * Deliberately the same name as the interface it marks a class as, in a package
 * of its own. An extension imports this and reaches the interface through
 * [dev.achmad.finbox.extension.EmailSource], so the two never both need naming
 * in one file. Tachiyomi splits `keiyoushi.annotation.Source` from
 * `eu.kanade.tachiyomi.source.Source` exactly this way.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class Source
