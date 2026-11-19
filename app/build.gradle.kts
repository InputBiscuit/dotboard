plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.inputbiscuit.dotboard"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.inputbiscuit.dotboard"
        minSdk = 31
        targetSdk = 37
        versionCode = 6
        versionName = "0.2.2_02"

        // Dotboard's API proxy (see proxy/). Used for live data unless the user enters their own key.
        buildConfigField("String", "PROXY_BASE_URL", "\"https://departures.platform-da5.workers.dev/\"")
    }

    // Release signing comes from the environment (CI secrets); without it, release builds are unsigned.
    // See .github/workflows/release.yml. Never commit a keystore or its passwords.
    val releaseKeystore = System.getenv("DOTBOARD_KEYSTORE_PATH")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("DOTBOARD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DOTBOARD_KEY_ALIAS")
                keyPassword = System.getenv("DOTBOARD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // "github" (GitHub releases and F-Droid) has the optional location features; "play" (Google Play)
    // leaves them out, along with the location permissions (see src/github/AndroidManifest.xml).
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "LOCATION", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "LOCATION", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // F-Droid: don't embed Google's encrypted dependency-info blob in APKs/bundles.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
