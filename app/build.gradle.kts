
plugins {
    id("com.android.application") version "9.4.1"
    id("com.google.devtools.ksp") version "2.3.12"
}

android {
    namespace = "com.dev.tunedetectivex"
    compileSdk = 37

    flavorDimensions += "default"

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.tunedetectivex.nooowavailable"
        minSdk = 33
        targetSdk = 37
        versionCode = 11
        versionName = "1.6"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs.getByName("debug")
        }
    }

    productFlavors {
        create("github") {
            dimension = "default"
            applicationId = "com.dev.tunedetectivex"
            versionName = "1.6-github"
        }

        create("dev") {
            dimension = "default"
            applicationId = "com.tunedetectivex.dev"
            versionName = "1.6-dev"
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)

    // Material Design
    implementation(libs.material)
    implementation(libs.taptargetview)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp3.integration)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Images
    implementation(libs.glide)
    annotationProcessor(libs.compiler)

    // Other existing dependencies
    implementation(libs.kotlin.stdlib)
    ksp(libs.ksp)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
