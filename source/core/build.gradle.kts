import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The contract, and the processor that reads it. A plain JVM module, not an
// Android library: nothing here needs Android, and a source is forbidden to
// fetch or schedule anyway — the app owns all of that and hands over an Email.
// Making that a compiler fact rather than a rule costs nothing.
//
// Java 11 to match :app, which consumes the class files. The processor runs on
// the compiler's JVM, which is newer, and 11 bytecode is fine there.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// The processor ships in this module rather than one of its own so that it can
// name Source and the annotations as *classes*. A processor that spells its own
// contract out in string literals keeps compiling after that contract is
// renamed and fails at the far end, as a source that mysteriously stopped being
// collected.
//
// It is a source set rather than more files under main, because everything in
// main is on :app's classpath and a compiler plugin has no business in an APK.
// This way the processor sees the contract, and nothing that depends on the
// contract sees the processor.
val processor by sourceSets.creating {
    compileClasspath += sourceSets["main"].output
    runtimeClasspath += sourceSets["main"].output
}

// Self-contained: KSP loads this off a classpath of its own, so the contract
// classes the processor reflects over have to travel with it.
val processorJar by tasks.registering(Jar::class) {
    archiveClassifier.set("processor")
    from(processor.output, sourceSets["main"].output)
}

// What `ksp(project(path = ":source:core", configuration = "processor"))`
// resolves to. Named for the source set; it carries the one jar and no
// dependencies, since the processor imports nothing but KSP and the contract.
configurations.consumable("processor") {
    outgoing.artifact(processorJar)
}

dependencies {
    // Receipt flattens html to one line per row. The only real dependency here,
    // and the reason it is `api`: a source written against Receipt may reach for
    // jsoup itself.
    api(libs.jsoup)

    // compileOnly, and only for the processor source set: KSP supplies these
    // classes when it loads a processor, and nothing else here may see them.
    "processorCompileOnly"(libs.ksp.api)

    testImplementation(libs.junit)
}
