// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.ksp) apply false
}

// AGP 9 has built-in Kotlin support; Kotlin JVM target is configured
// per-module via the `kotlin { compilerOptions { } }` block.
subprojects {
    plugins.withId("com.android.application") { configureKotlinJvmTarget() }
    plugins.withId("com.android.library") { configureKotlinJvmTarget() }
}

// Every source module, configured from here rather than from a build file of
// its own. A source is a directory with a class and an icon in it — there is
// nothing per-module to say, so saying it four times would only be four places
// to drift. It also keeps AGP on one classloader: a buildSrc convention plugin
// cannot see the AGP the root build resolves, and giving buildSrc its own copy
// takes the version off every other module's plugin alias.
//
// Identity comes from where the module sits, source/lib/<country>/<bank>, so
// a bank that moves country moves directory and nothing else has to agree.
/**
 * Mirrors a source's `res/` into a directory the app can merge without
 * collisions, renaming the launcher icon on the way.
 *
 * A source ships its icon as `src/main/res/mipmap-<density>/ic_launcher.png`,
 * which is the ordinary Android layout and the one Tachiyomi's
 * extensions-source uses, so an icon set copies in unchanged. It cannot merge
 * under that name: `ic_launcher` is what the app's own launcher icon answers
 * to, and an app resource beats a library one, so every source would render
 * finbox's icon. This copies it to `drawable-<density>/<id>_icon.png` instead
 * and passes the rest of `res/` through untouched.
 *
 * The whole directory is mirrored rather than filtered in place because AGP's
 * source set API has no per-file exclusion to hang that on.
 */
abstract class GenerateSourceResources : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val res: DirectoryProperty

    @get:Input
    abstract val sourceId: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val from = res.get().asFile
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()

        var icons = 0
        from.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(from)
            val bucket = relative.parentFile?.name.orEmpty()
            val target = if (bucket.startsWith("mipmap-") && file.name == "ic_launcher.png") {
                icons++
                out.resolve("drawable-${bucket.removePrefix("mipmap-")}/${sourceId.get()}_icon.png")
            } else {
                out.resolve(relative.path)
            }
            target.parentFile.mkdirs()
            file.copyTo(target, overwrite = true)
        }

        // A source with no icon would build and then show a blank row, which
        // reads as a UI bug a long way from the missing file.
        if (icons == 0) {
            throw GradleException(
                "${sourceId.get()} has no icon. Add one at " +
                    "src/main/res/mipmap-<density>/ic_launcher.png.",
            )
        }
    }
}

// Every source module, configured from here rather than from a build file of
// its own. Being a source is the same for all of them, so saying it four times
// would only be four places to drift; a module's own build file is left for
// what one bank needs on top, which is why it can be empty.
//
// It also keeps AGP on one classloader: a buildSrc convention plugin cannot see
// the AGP the root build resolves, and giving buildSrc its own copy takes the
// version off every other module's plugin alias.
//
// The filter is on the grandparent rather than on a `:source:lib:` path prefix,
// because `:source:lib:id` is a project too — the country directory — and it is
// a container, not a source.
configure(subprojects.filter { it.parent?.parent?.name == "lib" }) {
    val bank = name
    val country = parent!!.name

    apply(plugin = "com.android.library")
    apply(plugin = "com.google.devtools.ksp")

    val source = extensions.create<SourceExtension>("source")

    val resources = tasks.register<GenerateSourceResources>("generateSourceResources") {
        res.set(layout.projectDirectory.dir("src/main/res"))
        sourceId.set(source.id)
        outputDir.set(layout.buildDirectory.dir("generated/source-res/res"))
    }

    configure<com.android.build.api.dsl.LibraryExtension> {
        namespace = "dev.achmad.finbox.source.$country.$bank"
        compileSdk = 37
        defaultConfig { minSdk = 26 }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        // Every source's resources merge into one app, so a name two of them
        // share collapses to whichever linked last — silently, and looking like
        // a UI bug rather than a build one. AGP warns on anything unprefixed,
        // and the generated <id>_icon is prefixed by construction.
        resourcePrefix = "${bank}_"

        // src/main/res is the mirror's input, not a resource directory: what
        // AGP merges is what the task produced.
        sourceSets.getByName("main").res.setSrcDirs(emptyList<String>())
    }

    // The variant API rather than a res srcDir: it takes the task provider, so
    // the ordering is declared instead of inferred from task names — a srcDir
    // rejects providers outright, and guessing at names missed
    // processDebugNavigationResources.
    extensions.configure<com.android.build.api.variant.LibraryAndroidComponentsExtension> {
        onVariants { variant ->
            variant.sources.res?.addGeneratedSourceDirectory(
                resources,
                GenerateSourceResources::outputDir,
            )
        }
    }

    dependencies {
        add("api", project(":source:core"))
        // Leaves this source's calling card for :app to collect. It does not
        // aggregate; only :app passes finbox.source.aggregate.
        add("ksp", project(mapOf("path" to ":source:core", "configuration" to "processor")))

        // Plain JVM tests against saved receipts. No Android, no
        // instrumentation: a source is string work over an email body.
        val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
        add("testImplementation", libs.findLibrary("junit").get())
        add("testImplementation", libs.findLibrary("coroutines-core").get())
    }

    // After the module's own build file has run, which is where `source {}` is.
    // Only the identity is read this late; namespace and resourcePrefix come
    // from the directory, so AGP has them before it needs them.
    afterEvaluate {
        val id = source.id.orNull
            ?: throw GradleException("$path needs a source { id = \"…\" } block.")
        val sourceName = source.name.orNull
            ?: throw GradleException("$path needs a source { name = \"…\" } block.")
        if (id != bank) {
            throw GradleException(
                "$path declares id \"$id\" but sits in a directory called \"$bank\". " +
                    "The directory is how a source is found and the id is what the " +
                    "database stores; they have to be the same word.",
            )
        }
        extensions.configure<com.google.devtools.ksp.gradle.KspExtension> {
            arg("finbox.source.id", id)
            arg("finbox.source.name", sourceName)
            arg("finbox.source.namespace", "dev.achmad.finbox.source.$country.$bank")
        }
    }
}

fun org.gradle.api.Project.configureKotlinJvmTarget() {
    afterEvaluate {
        extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension::class.java)?.compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}