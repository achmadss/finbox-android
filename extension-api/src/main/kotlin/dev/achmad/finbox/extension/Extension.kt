package dev.achmad.finbox.extension

/** The APK's entry point: exactly one [EmailSource], with a no-argument constructor. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class Extension
