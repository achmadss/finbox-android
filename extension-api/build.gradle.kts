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
//
// Only the fallback for a local publishToMavenLocal, though: see `version`.
val apiVersion = "1.0"

// JitPack's coordinates, matched exactly so a locally published build and a
// JitPack one are interchangeable. Its multi-module form joins the owner and
// the repo with a dot in the group, leaving the artifact to name the module:
// com.github.achmadss.finbox-android:extension-api.
//
// This used to be the collapsed <owner>:<repo> form, with a comment saying a
// nicer name would cost splitting the API into its own repo. That was wrong:
// JitPack publishes whatever coordinates the build declares and rewrites
// nothing, and both halves are declared right here, so the nicer name costs
// these two lines.
group = "com.github.achmadss.finbox-android"

// JitPack builds whatever git ref was asked for and passes it as -Pversion,
// expecting the build to publish under that ref. Hardcoding apiVersion worked
// only while the tag and apiVersion happened to be the same string; ask for a
// commit hash and the build publishes "1.0", so the lookup finds nothing. That
// is the wall finbox.apiRef hits from the other side, and neither half works
// alone. Local publishToMavenLocal passes nothing, so it falls back.
version = providers.gradleProperty("version").getOrElse(apiVersion)

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
            artifactId = "extension-api"
            afterEvaluate { from(components["release"]) }
        }
    }
}

// No publishing repository is declared: finbox-extension resolves this from
// JitPack, which builds it on demand from a tag or a commit hash (jitpack.yml)
// and needs no account or token from anyone building there. Local iteration
// goes through publishToMavenLocal, which needs no credentials either.
