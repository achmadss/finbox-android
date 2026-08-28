package dev.achmad.finbox.extension.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier

private const val ENTRYPOINT = "dev.achmad.finbox.extension.core.annotation.SourceEntrypoint"
private const val PROVIDER = "dev.achmad.finbox.extension.core.annotation.SourceProvider"
private const val SOURCE = "dev.achmad.finbox.extension.core.source.Source"

private const val GENERATED_PACKAGE = "dev.achmad.finbox.extension"
private const val GENERATED_CLASS = "GeneratedExtensions"

/** One validated `@SourceEntrypoint`, ready to be written out. */
private class Entry(
    val id: String,
    val name: String,
    /** Already `Foo()` or `Foo`, depending on whether it is a class or an object. */
    val instantiation: String,
    val declaration: KSClassDeclaration,
)

/**
 * Collects every `@SourceEntrypoint` into `GeneratedExtensions.all`.
 *
 * The list used to be written by hand, which was defensible while there were
 * four of them and indefensible as a habit: adding a bank meant editing a file
 * in another package, and forgetting to meant a class that compiled, tested
 * green, and was never once asked to read an email. The compiler already knows
 * which classes exist, so it writes the list.
 *
 * Everything this checks is a build error rather than a runtime one, because
 * there is no runtime discovery left — extensions compile into the app, so a
 * mistake found late is found by a user with an unread inbox.
 */
class ExtensionProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) return emptyList()
        invoked = true

        validateProviders(resolver)

        val entries = resolver.getSymbolsWithAnnotation(ENTRYPOINT)
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull(::validateEntrypoint)
            // KSP does not promise an order and a reshuffled list would show up
            // as the extension screen rearranging itself between builds.
            .sortedBy { it.id }
            .toList()

        // Two extensions answering to one id would write transactions the other
        // then claims. Reported against both, since neither is the wrong one.
        entries.groupBy { it.id }
            .filterValues { it.size > 1 }
            .forEach { (id, clashing) ->
                clashing.forEach {
                    logger.error("Duplicate extension id \"$id\".", it.declaration)
                }
            }

        write(entries)
        return emptyList()
    }

    /**
     * `@SourceProvider` says the app can drive this kind of source. Putting it
     * on something that is not a [SOURCE] would let an entrypoint satisfy the
     * check below while implementing nothing the app can call.
     */
    private fun validateProviders(resolver: Resolver) {
        resolver.getSymbolsWithAnnotation(PROVIDER)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { decl ->
                if (decl.classKind != ClassKind.INTERFACE) {
                    logger.error("@SourceProvider belongs on an interface.", decl)
                } else if (decl.getAllSuperTypes().none { it.qualifiedName() == SOURCE }) {
                    logger.error("@SourceProvider interface must extend Source.", decl)
                }
            }
    }

    private fun validateEntrypoint(decl: KSClassDeclaration): Entry? {
        val fqn = decl.qualifiedName?.asString() ?: run {
            logger.error("@SourceEntrypoint must be on a top-level named class.", decl)
            return null
        }

        // What it can actually do, rather than what it says it can. A class
        // implementing only the bare Source marker would be collected, shipped,
        // and then asked for nothing.
        val providers = decl.getAllSuperTypes().filter { supertype ->
            supertype.declaration.annotations.any { it.qualifiedName() == PROVIDER }
        }
        if (providers.none()) {
            logger.error(
                "@SourceEntrypoint must implement at least one @SourceProvider interface, " +
                    "such as EmailSource.",
                decl,
            )
            return null
        }

        if (decl.classKind != ClassKind.CLASS && decl.classKind != ClassKind.OBJECT) {
            logger.error("@SourceEntrypoint must be on a class or object.", decl)
            return null
        }
        if (Modifier.ABSTRACT in decl.modifiers) {
            logger.error("@SourceEntrypoint class must be concrete.", decl)
            return null
        }
        if (decl.primaryConstructor?.parameters?.isNotEmpty() == true) {
            logger.error(
                "@SourceEntrypoint class must have a no-argument constructor; the " +
                    "registry constructs it with nothing to hand it.",
                decl,
            )
            return null
        }

        val arguments = decl.annotations
            .first { it.qualifiedName() == ENTRYPOINT }
            .arguments
            .associate { it.name?.asString() to it.value }
        val id = arguments["id"] as? String ?: ""
        val name = arguments["name"] as? String ?: ""

        // Blank rather than absent: the annotation requires both, so this only
        // catches @SourceEntrypoint(id = "", ...), which would otherwise become
        // an extension nothing can address.
        if (id.isBlank() || name.isBlank()) {
            logger.error("@SourceEntrypoint needs a non-blank id and name.", decl)
            return null
        }
        if (id != id.lowercase() || id.any { !it.isLetterOrDigit() }) {
            logger.error(
                "Extension id \"$id\" must be lowercase letters and digits: it names a " +
                    "package, a resource, and a column value.",
                decl,
            )
            return null
        }

        return Entry(
            id = id,
            name = name,
            instantiation = if (decl.classKind == ClassKind.OBJECT) fqn else "$fqn()",
            declaration = decl,
        )
    }

    private fun write(entries: List<Entry>) {
        // Aggregating: this one file is rebuilt whenever any annotated class
        // changes, which is the point of it.
        val dependencies = Dependencies(
            aggregating = true,
            sources = entries.mapNotNull { it.declaration.containingFile }.toTypedArray(),
        )
        codeGenerator.createNewFile(dependencies, GENERATED_PACKAGE, GENERATED_CLASS)
            .bufferedWriter()
            .use { out ->
                // Fully qualified rather than imported: generated code should not
                // have to reason about two banks with the same class name.
                val listed = entries.joinToString("\n") {
                    """        Extension(id = "${it.id}", name = "${it.name}", source = ${it.instantiation}),"""
                }
                out.write(
                    """
                    |// Generated from @SourceEntrypoint. Do not edit.
                    |package $GENERATED_PACKAGE
                    |
                    |internal object $GENERATED_CLASS {
                    |    val all: List<Extension> = listOf(
                    ${listed.prependIndent("|")}
                    |    )
                    |}
                    |
                    """.trimMargin(),
                )
            }
    }
}

private fun com.google.devtools.ksp.symbol.KSType.qualifiedName(): String? =
    declaration.qualifiedName?.asString()

private fun com.google.devtools.ksp.symbol.KSAnnotation.qualifiedName(): String? =
    annotationType.resolve().declaration.qualifiedName?.asString()

class ExtensionProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ExtensionProcessor(environment.codeGenerator, environment.logger)
}
