package dev.achmad.finbox.gradle

import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * Everything a source module is, so that a source's own build file says only
 * which bank it is.
 *
 * A source is an Android library — it carries its own icon, and the app merges
 * its resources — applied here rather than from the root build so that the
 * module declares itself. Cross-project configuration built the same thing and
 * Gradle was happy with it, but Android Studio models a module from its own
 * build file and showed four plain Kotlin modules with loose `src` and `res`
 * folders.
 *
 * Identity beyond the name comes from where the module sits,
 * `source/lib/<country>/<bank>`, so a bank that moves country moves directory
 * and nothing else has to agree.
 */
class SourcePlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        val bank = name
        val country = parent?.name
            ?: throw GradleException("A source lives at source/lib/<country>/<bank>.")
        val namespace = "dev.achmad.finbox.source.$country.$bank"

        pluginManager.apply("com.android.library")
        pluginManager.apply("com.google.devtools.ksp")

        val source = extensions.create<SourceExtension>("source")

        val icon = tasks.register<GenerateSourceIcon>("generateSourceIcon") {
            res.set(layout.projectDirectory.dir("src/main/res"))
            sourceId.set(source.id)
            outputDir.set(layout.buildDirectory.dir("generated/source-icon/res"))
        }

        extensions.configure<LibraryExtension> {
            this.namespace = namespace
            compileSdk = 37
            defaultConfig { minSdk = 26 }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
            // Every source's resources merge into one app, so a name two of
            // them share collapses to whichever linked last — silently, and
            // looking like a UI bug rather than a build one. AGP warns on
            // anything unprefixed. `ic_launcher` is the deliberate exception:
            // it is never read, only copied to a prefixed drawable.
            resourcePrefix = "${bank}_"

            // Saved emails, not Android resources — they are on the test
            // classpath and never reach the APK. The default name for this is
            // src/test/resources, one letter and a world away from src/main/res
            // next door; naming it for what is in it is worth the deviation.
            sourceSets.getByName("test").resources.setSrcDirs(listOf("src/test/emails"))
        }

        // The variant API rather than a res srcDir: it takes the task provider,
        // so the ordering is declared instead of inferred from task names — a
        // srcDir rejects providers outright, and depending on guessed names
        // missed processDebugNavigationResources.
        extensions.configure<LibraryAndroidComponentsExtension> {
            onVariants { variant ->
                variant.sources.res?.addGeneratedSourceDirectory(icon, GenerateSourceIcon::outputDir)
            }
        }

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            add("api", project(":source:core"))
            // Leaves this source's calling card for :app to collect. It does not
            // aggregate; only :app passes finbox.source.aggregate.
            add("ksp", project(mapOf("path" to ":source:core", "configuration" to "processor")))

            // Plain JVM tests against saved receipts. No Android, no
            // instrumentation: a source is string work over an email body.
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("coroutines-core").get())
        }

        // After the module's own build file has run, which is where `source {}`
        // is. Only the identity is read this late; the namespace comes from the
        // directory, so AGP has it before it needs it.
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
            extensions.configure<KspExtension> {
                arg("finbox.source.id", id)
                arg("finbox.source.name", sourceName)
                arg("finbox.source.namespace", namespace)
            }
        }
    }
}
