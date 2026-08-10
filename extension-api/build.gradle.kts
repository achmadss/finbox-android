plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.achmad.finbox.extension"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
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
}

// The parser extension API. Must stay in sync with `core/` in
// finbox-extension (same FQNs, same shapes) — the libVersion check in the
// app's ExtensionLoader guards against drift.
