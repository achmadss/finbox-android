plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

// The extension API, published as a library so finbox-extension can
// compile against it instead of keeping its own copy of these types.
//
// Extensions depend on it with `compileOnly`: the real classes come from the
// app at runtime, resolved through ChildFirstPathClassLoader's parent. Bump
// `apiVersion` on every change, and keep it in step with the app's
// FinboxConfig.LIB_VERSION — plus MIN_LIB_VERSION when the change breaks
// already-published extensions, as removing a field does.
val apiVersion = "1.0"

// JitPack's coordinates for this repo, matched so a locally published build and
// a JitPack one are interchangeable. It collapses a repo with one publishable
// module to <owner>:<repo>, which is why this is not
// `...finbox-android:extension-api`.
//
// Tested on this branch rather than assumed, because the plan assumed
// otherwise: declaring group `com.github.achmadss.finbox-android` and artifact
// `extension-api` changes nothing JitPack serves. It reads whatever the build
// published — the log says `Found artifact: ...:extension-api:1.0` — and then
// republishes it under the coordinates that were *asked for*, rewriting both
// the artifact name and the version to the git ref. So the module name and the
// version here reach nobody, and splitting the API into its own repo (as
// Tachiyomi does with extensions-lib) is still what would earn a nicer name.
group = "com.github.achmadss"
version = apiVersion

android {
    namespace = "dev.achmad.finbox.extension"
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

// No publishing repository is declared: finbox-extension resolves this from
// JitPack, which builds it on demand from a tag or a commit hash (jitpack.yml)
// and needs no account or token from anyone building there. Local iteration
// goes through publishToMavenLocal, which needs no credentials either.
