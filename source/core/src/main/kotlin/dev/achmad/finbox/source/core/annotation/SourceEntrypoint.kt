package dev.achmad.finbox.source.core.annotation

/**
 * Marks the one class in a source module that the app instantiates.
 *
 * A bare marker: everything the registry needs is on
 * [dev.achmad.finbox.source.core.Source] itself, so this only has to be findable.
 * The processor collects it and checks what the compiler cannot — that the class
 * is concrete, constructible with no arguments, and implements at least one
 * [SourceProvider] interface. All three were runtime problems on a device before
 * they were build errors.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class SourceEntrypoint
