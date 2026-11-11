import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    // Type-safe Navigation Compose routes are @Serializable classes.
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        optIn.addAll("kotlin.time.ExperimentalTime", "androidx.compose.material3.ExperimentalMaterial3Api")
    }
}
// The Android UI is native Compose in this module; the iOS app has its own SwiftUI. Only sharedLogic is shared.
dependencies {
    implementation(project(":sharedLogic"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.navigation.compose)
    // The check-in map (CHECKIN-01). iOS uses Apple's MapKit instead.
    implementation(libs.maplibre.android.sdk)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "tech.vauldex.dekita"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "tech.vauldex.dekita"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        // The MapLibre style for the check-in map. The default is OpenFreeMap's Positron style: free,
        // no key, OpenStreetMap data. It is an outside service still pending supplier review.
        // Override with dekita.mapStyleUrl in ~/.gradle/gradle.properties or with -P; never commit a
        // URL that carries an API key. MapLibre's demo style, with no streets and no extra supplier,
        // is https://demotiles.maplibre.org/style.json.
        val mapStyleUrl = providers.gradleProperty("dekita.mapStyleUrl")
            .getOrElse("https://tiles.openfreemap.org/styles/positron")
        buildConfigField("String", "MAP_STYLE_URL", "\"$mapStyleUrl\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
    buildFeatures {
        compose = true
        // MainActivity passes BuildConfig.DEBUG to App() so release builds never show the debug panel.
        buildConfig = true
    }
}