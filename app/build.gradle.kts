plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pureplus.delivery"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pureplus.delivery"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "BASE_URL", "\"https://pureplusapp.in/\"")
        // DP-02 geofence map is OpenStreetMap (osmdroid) — no Maps API key or billing needed.
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"https://pureplusapp.in/\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "BASE_URL", "\"https://pureplusapp.in/\"")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true   // java.time.* on minSdk 24
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.workmanager.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Location
    implementation(libs.play.services.location)

    // OpenStreetMap (DP-02 geofence map) — free tiles, no API key
    implementation(libs.osmdroid.android)

    // Coroutines + Play Services (.await() on Tasks)
    implementation(libs.coroutines.play.services)

    // Material Icons Extended (outlined icons used in screens)
    implementation(libs.androidx.material.icons.extended)

    // Java 8+ API desugaring (java.time.* on minSdk 24)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Unit tests (pure JVM logic: money formatting, reconciliation math, product heuristics)
    testImplementation("junit:junit:4.13.2")
}
