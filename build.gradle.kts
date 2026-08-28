// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.sqldelight) apply false
}

// AGP 9 has built-in Kotlin support; Kotlin JVM target is configured
// per-module via the `kotlin { compilerOptions { } }` block.
subprojects {
    plugins.withId("com.android.application") { configureKotlinJvmTarget() }
    plugins.withId("com.android.library") { configureKotlinJvmTarget() }
}

fun org.gradle.api.Project.configureKotlinJvmTarget() {
    afterEvaluate {
        extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension::class.java)?.compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}