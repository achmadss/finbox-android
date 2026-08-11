package dev.achmad.finbox.extension

/**
 * Marks the entry point of an extension: exactly one [TransactionParser] per
 * APK, with a no-argument constructor.
 *
 * The build generates a class with a fixed name from it, which is what the
 * APK's `finbox.extension.class` metadata points at — so an extension never
 * has to repeat its own class name in Gradle.
 *
 * Source retention: the marker is consumed at compile time and never needs to
 * exist at runtime.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class Parser
