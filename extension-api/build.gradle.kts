plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

// The parser extension API, published as a library so finbox-extension can
// compile against it instead of keeping its own copy of these types.
//
// Extensions depend on it with `compileOnly`: the real classes come from the
// app at runtime, resolved through ChildFirstPathClassLoader's parent. Bump
// `apiVersion` whenever a change breaks already-published extensions, and keep
// it in step with FinboxConfig.SUPPORTED_LIB_VERSIONS in the app.
val apiVersion = "1.1"

group = "com.github.achmadss.finbox-android"
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
            artifactId = "extension-api"
            afterEvaluate { from(components["release"]) }
        }
    }
}
