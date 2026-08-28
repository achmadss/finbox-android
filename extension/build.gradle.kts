import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Deliberately a plain JVM module, not an Android library. Nothing an extension
// does needs Android: the contract is interfaces and data classes, Receipt is
// jsoup and string work, and an extension never fetches, schedules or touches a
// token — the app owns all of that and hands over an Email. Making that a
// compiler fact rather than a rule costs nothing, and it means the tests here
// are plain JVM tests run by `test` rather than `testDebugUnitTest`.
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
    // and the reason it is `api`: nothing in :app uses jsoup, but an extension
    // written against Receipt may reach for it.
    api(libs.jsoup)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.core)
}
