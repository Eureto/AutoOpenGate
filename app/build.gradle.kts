import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "eureto.opendoor"
    compileSdk = 35

    defaultConfig {
        applicationId = "eureto.opendoor"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {

    // Standardowe AndroidX
    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.7.1") // Updated as requested
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui:1.6.8") // Use the latest stable version
    implementation("androidx.compose.material3:material3:1.2.1") // Use latest
    implementation("androidx.compose.ui:ui-viewbinding:1.6.8") // For ComposeView in XML
    implementation("androidx.activity:activity-compose:1.9.0") // For ComponentActivity.setContent if you use it elsewhere


    // Google Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0") // Dla FusedLocationProviderClient i Geofencing
    implementation("com.google.maps.android:android-maps-utils:3.4.0") // Dla PolyUtil do obsługi wielokątów

    // Sieć
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // Do logowania zapytań (do debugowania)
    implementation("com.google.code.gson:gson:2.10.1") // Dla GSON

    // Asynchroniczność (Coroutines)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0") // viewModelScope
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0") // lifecycleScope
    //Dodano
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")

    // Bezpieczne przechowywanie danych
    implementation("androidx.security:security-crypto:1.1.0-alpha06") // EncryptedSharedPreferences

    // WorkManager (dla zadań w tle)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Biblioteka do kodowania Base64 (java.util.Base64 jest od API 26+)
    implementation("commons-codec:commons-codec:1.15") // Dla starszych API niż 26, alternatywnie android.util.Base64


    implementation(libs.androidx.core.ktx)
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
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}