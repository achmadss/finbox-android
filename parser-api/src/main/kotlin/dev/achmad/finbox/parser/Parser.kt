package dev.achmad.finbox.parser

/**
 * The APK's entry point: exactly one [EmailParser], with a no-argument
 * constructor.
 *
 * The build generates a delegate with a fixed name from it, so the manifest can
 * name a class no parser has to repeat in Gradle.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class Parser
