import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Runs inside the Kotlin compiler, not on a device, so it targets 17 like the
// toolchain rather than 11 like :extension. It must not depend on :extension:
// that would make the module KSP processes a dependency of its own processor.
// It names the annotations by string instead, which is how KSP looks them up
// anyway.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.ksp.api)
}
