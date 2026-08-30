package dev.achmad.finbox.source.core.annotation

/**
 * Marks an interface as a kind of source the app knows how to drive.
 *
 * [dev.achmad.finbox.source.core.Source] alone says "this is a provider";
 * this says "and the app has something that feeds it". `EmailSource` carries it
 * because the app owns a Gmail client that can honour a query. A half-written
 * `PdfSource` would extend `Source` without this, and a bank implementing it
 * would fail the build rather than ship as a source the app cannot run.
 *
 * That is the whole job: the processor treats it as the allowlist, so adding a
 * source kind is annotating one interface and nothing enumerates them by name.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class SourceProvider
