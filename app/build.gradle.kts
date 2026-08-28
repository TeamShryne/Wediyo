import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.teamshryne.wediyo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.teamshryne.wediyo"
        minSdk = 21 // HIGHLY COMPATIBLE: covers Android 5.0+ (~99.5% devices in 2026)
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // NDK: keep API 21 for max compatibility
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Go gomobile AAR — committed by go.yml to app/libs/wediyo.aar
    implementation(files("libs/wediyo.aar"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// ---------------------------------------------------------------------------
// Go + gomobile — builds app/libs/wediyo.aar via `gomobile bind -target=android -androidapi 21`
//   go.yml commits AAR, android.yml just uses it. Local machine is bad.
// ---------------------------------------------------------------------------
val goDir = file("../go/wediyo")
val goAar = file("libs/wediyo.aar")
val gomobileBind by tasks.registering(Exec::class) {
    description = "Build Go AAR via gomobile bind (manual — see go.yml)"
    group = "go"
    workingDir(goDir)
    commandLine(
        "gomobile", "bind",
        "-target=android",
        "-androidapi", "21",
        "-javapkg", "com.teamshryne.wediyo",
        "-o", goAar.absolutePath,
        "."
    )
    onlyIf { System.getenv("CI") == "true" || file("/home/shrawan/go/bin/gomobile").exists() }
    doFirst { file("libs").mkdirs() }
}

// NOTE: preBuild does NOT depend on gomobileBind — APK uses committed AAR.
