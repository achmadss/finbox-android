plugins {
    `kotlin-dsl`
}

// Gradle API only. Nothing about AGP or KSP belongs here: buildSrc's classes
// load in the root build's classloader, which cannot see the plugins the root
// resolves through the plugins DSL, and giving buildSrc its own copy of AGP
// takes the version off every other module's plugin alias. This exists solely
// so that a source module's build file can name the type of its `source {}`
// block, which a class declared in the root build script cannot provide.
