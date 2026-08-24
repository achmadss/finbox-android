import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.serialization)
}

// OAuth client ids live in local.properties (gitignored). Google ties a client
// to the signing certificate registered with it, so debug and release builds
// need one each — a missing entry builds fine and fails at sign-in instead.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun oauthClientId(key: String) = "\"${localProperties.getProperty(key, "")}\""

android {
    namespace = "dev.achmad.finbox"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.achmad.finbox"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["appAuthRedirectScheme"] = "dev.achmad.finbox"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "OAUTH_CLIENT_ID", oauthClientId("oauthClientIdDebug"))
        }
        release {
            buildConfigField("String", "OAUTH_CLIENT_ID", oauthClientId("oauthClientIdRelease"))
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // The locale list comes from the res folders: adding values-in/strings.xml
        // is all it takes for Indonesian to appear in the system language picker
        // and in the app's own language screen. res/resources.properties names the
        // language values/ itself is written in.
        generateLocaleConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    // Per-app languages: AppCompatDelegate carries them back to Android 8.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.material.icons)
    implementation(libs.material.motion.compose.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.voyager.navigator)
    implementation(libs.voyager.tabNavigator)
    implementation(libs.voyager.transitions)
    implementation(libs.voyager.screenmodel)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    // No app code uses jsoup — parsers do, and they resolve it from here
    // through ChildFirstPathClassLoader's parent rather than bundling their own.
    // Dropping it as "unused" breaks every parser at runtime.
    implementation(libs.jsoup)
    implementation(libs.security.crypto)
    implementation(libs.appauth)
    implementation(libs.work.runtime.ktx)
    implementation(libs.accompanist.permissions)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.serialization.json.okio)

    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    implementation(project(":data"))
    implementation(project(":parser-api"))
}