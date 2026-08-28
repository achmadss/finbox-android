plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

// The parser API, published as a library so finbox-parser can
// compile against it instead of keeping its own copy of these types.
//
// Parsers depend on it with `compileOnly`: the real classes come from the
// app at runtime, resolved through ChildFirstPathClassLoader's parent. Bump
// `apiVersion` on every change, and keep it in step with the app's
// FinboxConfig.LIB_VERSION — plus MIN_LIB_VERSION when the change breaks
// already-published parsers, as removing a field does.
//
// Only the fallback for a local publishToMavenLocal, though: see `version`.
val apiVersion = "1.0"

// JitPack's coordinates for this repo, matched exactly so a locally published
// build and a JitPack one are interchangeable. It collapses a repo with one
// publishable module to <owner>:<repo>, which is why this is not
// `...finbox-android:parser-api` — splitting the API into its own repo (as
// Tachiyomi does with extensions-lib) is what would earn a nicer name.
group = "com.github.achmadss"

// JitPack builds whatever git ref was asked for and passes it as -Pversion,
// expecting the build to publish under that ref. Hardcoding apiVersion worked
// only while the tag and apiVersion happened to be the same string; ask for a
// commit hash and the build publishes "1.0", so the lookup finds nothing. That
// is the wall finbox.apiRef hits from the other side, and neither half works
// alone. Local publishToMavenLocal passes nothing, so it falls back.
version = providers.gradleProperty("version").getOrElse(apiVersion)

android {
    namespace = "dev.achmad.finbox.parser"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "finbox-android"
            afterEvaluate { from(components["release"]) }
        }
    }
}

// No publishing repository is declared: finbox-parser resolves this from
// JitPack, which builds it on demand from a tag or a commit hash (jitpack.yml)
// and needs no account or token from anyone building there. Local iteration
// goes through publishToMavenLocal, which needs no credentials either.
