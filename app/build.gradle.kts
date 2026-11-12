import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
}

// RapidAPI key for the Spotify lyrics provider. Optional: local.properties (RAPIDAPI_KEY=...) or the
// RAPIDAPI_KEY environment variable / GitHub secret. Empty = users can enter their own key in Settings.
val rapidApiKey: String = run {
    val f = rootProject.file("local.properties")
    val fromFile = if (f.exists()) Properties().apply { f.inputStream().use { load(it) } }.getProperty("RAPIDAPI_KEY") else null
    (fromFile ?: System.getenv("RAPIDAPI_KEY") ?: "").trim()
}

// Contact email sent to lrc.red in the User-Agent / X-Client-ID (required by their terms for bulk use).
// Optional: local.properties (LRCRED_EMAIL=...) or the LRCRED_EMAIL environment variable / GitHub secret.
// Empty = lrc.red is still searched, just without an email in the client identification.
val lrcRedEmail: String = run {
    val f = rootProject.file("local.properties")
    val fromFile = if (f.exists()) Properties().apply { f.inputStream().use { load(it) } }.getProperty("LRCRED_EMAIL") else null
    (fromFile ?: System.getenv("LRCRED_EMAIL") ?: "").trim().replace("\"", "")
}

android {
    namespace = "dev.koshik.lrcembedder"
    compileSdk = 35

    defaultConfig {
        applicationId = "in.koshik.lrcembedder"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.4"

        buildConfigField("String", "RAPIDAPI_KEY", "\"$rapidApiKey\"")
        buildConfigField("String", "LRCRED_EMAIL", "\"$lrcRedEmail\"")

        resourceConfigurations += listOf("en")
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs { useLegacyPackaging = false }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    implementation(libs.taglib)
}
