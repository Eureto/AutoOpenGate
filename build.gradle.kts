// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        google() // Essential for Google-provided plugins
        mavenCentral()
        // Other repositories if needed
    }
    dependencies {
        // ... other classpath dependencies like the Android Gradle Plugin (AGP)
        // classpath("com.android.tools.build:gradle:...") // Example, you'll have your AGP version here

        // Add this line for the Secrets Gradle Plugin:
        classpath("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1") // Use the latest version
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}