plugins {
    `kotlin-dsl`
}

// An included build, not buildSrc. buildSrc's classes load in the root build's
// own classloader, which cannot see the AGP the root resolves through the
// plugins DSL — and giving buildSrc a real AGP dependency puts AGP on the root
// classpath with no version, which breaks every other module's
// `alias(libs.plugins.android.application)`. An included build contributes
// plugins through plugin resolution instead, so `compileOnly` is enough: the
// consuming project brings its own AGP.
dependencies {
    compileOnly("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    compileOnly("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")
}

gradlePlugin {
    plugins {
        create("finboxSource") {
            id = "finbox.source"
            implementationClass = "dev.achmad.finbox.gradle.SourcePlugin"
        }
    }
}
