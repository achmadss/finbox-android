import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The contract, and nothing that implements it. A plain JVM module, not an
// Android library: nothing here needs Android, and a source is forbidden to
// fetch or schedule anyway — the app owns all of that and hands over an Email.
// Making that a compiler fact rather than a rule costs nothing.
//
// Java 11 to match :app, which consumes the class files.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // Receipt flattens html to one line per row. The only real dependency here,
    // and the reason it is `api`: a source written against Receipt may reach for
    // jsoup itself.
    api(libs.jsoup)

    testImplementation(libs.junit)
}
