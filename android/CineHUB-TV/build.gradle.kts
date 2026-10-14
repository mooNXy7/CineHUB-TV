plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cinehub.tv"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.cinehub.tv"
        minSdk = 23
        targetSdk = 35
        versionCode = 129
        versionName = "1.2.9"
    }

    signingConfigs {
        getByName("debug") {}
        create("release") {
            val ks = System.getenv("CINEHUB_TV_KEYSTORE_PATH")
            val kp = System.getenv("CINEHUB_TV_KEYSTORE_PASSWORD")
            val ka = System.getenv("CINEHUB_TV_KEY_ALIAS")
            val ap = System.getenv("CINEHUB_TV_KEY_PASSWORD")
            check(!ks.isNullOrBlank() && !kp.isNullOrBlank() && !ka.isNullOrBlank() && !ap.isNullOrBlank()) {
                "Production signing is required: configure CINEHUB_TV_KEYSTORE_PATH, CINEHUB_TV_KEYSTORE_PASSWORD, CINEHUB_TV_KEY_ALIAS and CINEHUB_TV_KEY_PASSWORD."
            }
            storeFile = file(ks)
            storePassword = kp
            keyAlias = ka
            keyPassword = ap
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { buildConfig = true }
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
}
