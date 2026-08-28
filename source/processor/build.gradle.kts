import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Runs inside the Kotlin compiler, not on a device, so it targets 17 like the
// toolchain rather than 11 like :source-api. It deliberately does not depend on
// :source-api: a processor that depended on the contract would put
// symbol-processing-api on the compile classpath of everything that uses the
// contract. It names the annotations by string, which is how KSP resolves them
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
