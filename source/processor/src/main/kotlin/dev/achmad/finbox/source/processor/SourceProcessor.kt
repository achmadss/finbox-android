package dev.achmad.finbox.source.processor

import com.google.devtools.ksp.KspExperimental
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
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

private const val ENTRYPOINT = "dev.achmad.finbox.source.core.annotation.SourceEntrypoint"
private const val PROVIDER = "dev.achmad.finbox.source.core.annotation.SourceProvider"
private const val SOURCE = "dev.achmad.finbox.source.core.Source"

/**
 * Where a source module leaves its calling card, and the only place the
 * aggregator looks. Every module writes into this one package, which is what
 * makes the app's list assemble without anyone maintaining it.
 */
private const val MANIFEST_PACKAGE = "dev.achmad.finbox.source.generated"

/** Set on whichever module assembles the registry — `:app`. */
private const val AGGREGATE_OPTION = "finbox.source.aggregate"

private const val REGISTRY_PACKAGE = "dev.achmad.finbox.source"
private const val REGISTRY_CLASS = "GeneratedSources"

/**
 * Collects `@SourceEntrypoint` classes into a registry the app can read,
 * across module boundaries.
 *
 * Two halves, because a source lives in its own Gradle module and KSP only ever
 * sees one module's sources at a time:
 *
 * - **In a source module**, it validates the entrypoint and writes a tiny holder
 *   into [MANIFEST_PACKAGE] — a calling card naming the class.
 * - **In `:app`**, where `finbox.source.aggregate` is set, it reads every holder
 *   already on the classpath and writes `GeneratedSources.all`.
 *
 * The alternative was a hand-written list, and the argument against it is not
 * that a list is hard to write. It is that forgetting a line produces a source
 * that compiles, passes its own tests, and is never once handed an email.
 */
class SourceProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val aggregating: Boolean,
) : SymbolProcessor {

    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) return emptyList()
        invoked = true

        validateProviders(resolver)

        resolver.getSymbolsWithAnnotation(ENTRYPOINT)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
            .let(::writeManifests)

        if (aggregating) writeRegistry(resolver)
        return emptyList()
    }

    /**
     * `@SourceProvider` says the app can drive this kind of source. On something
     * that is not a [SOURCE] it would let an entrypoint pass the check below
     * while implementing nothing the app can call.
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

    private fun writeManifests(entrypoints: List<KSClassDeclaration>) {
        // One module, one source. Two would both be valid and only one would be
        // reachable through the module's own package, so this is the module
        // telling you it is really two modules.
        if (entrypoints.size > 1) {
            entrypoints.forEach {
                logger.error("A source module declares exactly one @SourceEntrypoint.", it)
            }
            return
        }

        entrypoints.forEach { decl ->
            val fqn = decl.qualifiedName?.asString() ?: run {
                logger.error("@SourceEntrypoint must be on a top-level named class.", decl)
                return@forEach
            }

            // What it can actually do, rather than what it says it can. A class
            // implementing only the bare Source marker would be collected,
            // shipped, and then asked for nothing.
            if (decl.getAllSuperTypes().none { supertype ->
                    supertype.declaration.annotations.any { it.qualifiedName() == PROVIDER }
                }
            ) {
                logger.error(
                    "@SourceEntrypoint must implement at least one @SourceProvider " +
                        "interface, such as EmailSource.",
                    decl,
                )
                return@forEach
            }
            if (decl.classKind != ClassKind.CLASS && decl.classKind != ClassKind.OBJECT) {
                logger.error("@SourceEntrypoint must be on a class or object.", decl)
                return@forEach
            }
            if (Modifier.ABSTRACT in decl.modifiers) {
                logger.error("@SourceEntrypoint class must be concrete.", decl)
                return@forEach
            }
            if (decl.primaryConstructor?.parameters?.isNotEmpty() == true) {
                logger.error(
                    "@SourceEntrypoint class must have a no-argument constructor; the " +
                        "registry constructs it with nothing to hand it.",
                    decl,
                )
                return@forEach
            }

            // The FQN flattened, so two banks that both call their class Bri
            // cannot collide in the shared package.
            val holder = fqn.replace('.', '_')
            codeGenerator.createNewFile(
                Dependencies(aggregating = false, decl.containingFile!!),
                MANIFEST_PACKAGE,
                holder,
            ).bufferedWriter().use { out ->
                val instance = if (decl.classKind == ClassKind.OBJECT) fqn else "$fqn()"
                out.write(
                    """
                    |// Generated from @SourceEntrypoint on $fqn. Do not edit.
                    |package $MANIFEST_PACKAGE
                    |
                    |public object $holder {
                    |    public val source: $SOURCE = $instance
                    |}
                    |
                    """.trimMargin(),
                )
            }
        }
    }

    /**
     * Reads the calling cards every source module left behind.
     *
     * `getDeclarationsFromPackage` is the one KSP call that looks past this
     * module's own sources into the compile classpath, which is why the holders
     * go in a package of their own: it is asked for everything in there, so
     * anything else living at that name would be collected as a source.
     */
    @OptIn(KspExperimental::class)
    private fun writeRegistry(resolver: Resolver) {
        val holders = resolver.getDeclarationsFromPackage(MANIFEST_PACKAGE)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.OBJECT }
            .mapNotNull { it.qualifiedName?.asString() }
            // KSP does not promise an order, and a reshuffled list would show up
            // as the sources screen rearranging itself between builds.
            .sorted()
            .toList()

        if (holders.isEmpty()) {
            logger.warn(
                "No sources found. Every directory under source/lib/<country>/<bank> is a " +
                    "source module, and :app depends on all of them.",
            )
        }

        codeGenerator.createNewFile(
            Dependencies(aggregating = true),
            REGISTRY_PACKAGE,
            REGISTRY_CLASS,
        ).bufferedWriter().use { out ->
            val listed = holders.joinToString("\n") { "|        $it.source," }
            out.write(
                """
                |// Generated from every @SourceEntrypoint on the classpath. Do not edit.
                |package $REGISTRY_PACKAGE
                |
                |internal object $REGISTRY_CLASS {
                |    val all: List<$SOURCE> = listOf(
                $listed
                |    )
                |}
                |
                """.trimMargin(),
            )
        }
    }
}

private fun KSType.qualifiedName(): String? = declaration.qualifiedName?.asString()

private fun KSAnnotation.qualifiedName(): String? =
    annotationType.resolve().declaration.qualifiedName?.asString()

class SourceProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        SourceProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            aggregating = environment.options[AGGREGATE_OPTION].toBoolean(),
        )
}
