plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.cinehub.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.cinehub.tv"
        minSdk = 23
        targetSdk = 28
        versionCode = 130
        versionName = "1.3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Release signing is enabled only when the CI signing-key environment is present.
    // Debug builds remain independent from release credentials.
    val releaseStorePath = System.getenv("CINEHUB_TV_KEYSTORE_PATH")
    signingConfigs {
        if (!releaseStorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = System.getenv("CINEHUB_TV_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("CINEHUB_TV_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("CINEHUB_TV_KEY_PASSWORD") ?: ""
            }
        }
    }
    // This APK is distributed directly, not through Google Play. Keep the tested TV targetSdk
    // unchanged and suppress only Play-store target-SDK policy lint for sideloaded releases.
    lint {
        disable += "ExpiredTargetSdkVersion"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!releaseStorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("io.coil-kt:coil:2.7.0")
}
