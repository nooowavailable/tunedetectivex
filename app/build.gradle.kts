plugins {
    id("org.jetbrains.kotlin.android") version "2.2.0-RC"
    id("com.android.application") version "8.10.0"
    id("kotlin-android")
    id("kotlin-kapt")
    id("com.google.devtools.ksp") version "2.2.0-RC-2.0.1"
}

android {
    namespace = "com.dev.tunedetectivex"
    compileSdk = 35
    flavorDimensions += "default"
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.tunedetectivex.nooowavailable"
        minSdk = 31
        targetSdk = 35
        versionCode = 7
        versionName = "1.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            versionName = "1.4-github"
        }
        create("accrescent") {
            dimension = "default"
            applicationId = "com.tunedetectivex.accrescent"
            versionName = "1.4-accrescent"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.taptargetview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.kotlin.stdlib)
    implementation(libs.glide)
    annotationProcessor(libs.compiler)
    implementation(libs.okhttp3.integration)
    ksp(libs.ksp)
}