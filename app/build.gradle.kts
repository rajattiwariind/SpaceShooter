plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.modernandroidtemplate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.modernandroidtemplate"
        minSdk = 24
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
}

dependencies {

    // Standard Jetpack & UI Elements
    implementation(libs.appcompat)
            implementation(libs.material)
            implementation(libs.constraintlayout)

            // Activity & Core Engine
            implementation(libs.activity)
            implementation(libs.androidx.core.ktx)

            // Test Assertions
            testImplementation(libs.junit)
            androidTestImplementation(libs.ext.junit)
            androidTestImplementation(libs.espresso.core)
}