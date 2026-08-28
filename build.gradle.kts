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
configure(subprojects.filter { it.path.startsWith(":source:lib:") }) {
    val bank = name
    val country = parent!!.name

    apply(plugin = "com.android.library")
    apply(plugin = "com.google.devtools.ksp")

    configure<com.android.build.api.dsl.LibraryExtension> {
        namespace = "dev.achmad.finbox.source.$country.$bank"
        compileSdk = 37
        defaultConfig { minSdk = 26 }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        // Every source's resources merge into one app, so an unprefixed
        // icon.png in four modules would collapse to whichever linked last —
        // silently, and looking like a UI bug rather than a build one.
        resourcePrefix = "${bank}_"
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
}

fun org.gradle.api.Project.configureKotlinJvmTarget() {
    afterEvaluate {
        extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension::class.java)?.compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}