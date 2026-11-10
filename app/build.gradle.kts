plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.osintgram.chronos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.osintgram.chronos"
        minSdk = 26
        targetSdk = 35
        versionCode = 30
        versionName = "6.8.3"
    }

    // Release signing is read from environment variables so no key material
    // ever lands in the repo. CI decodes the keystore and exports these.
    signingConfigs {
        create("release") {
            val path = System.getenv("KEYSTORE_PATH") ?: "release.p12"
            // Resolved from the repo root, where CI decodes the keystore.
            storeFile = rootProject.file(path)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Optional Health Connect sync for steps, water and sleep.
    implementation(libs.androidx.health.connect.client)
    // Six-hourly background sync of those numbers.
    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.androidx.ui.tooling)

    // Unit tests for the wellness maths and its JSON round-trip.
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
