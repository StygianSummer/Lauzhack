// Android shell (plan §3, §16.10). Included by settings.gradle.kts only when an Android SDK is
// found (or -Ptea.android=true). AGP 9 compiles Kotlin itself: no kotlin-android plugin.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Optional backend URL baked into the APK (plan C5 "env var"): -Ptea.api=https://... or TEA_API.
val teaApi: String = providers.gradleProperty("tea.api")
    .orElse(providers.environmentVariable("TEA_API"))
    .getOrElse("")
    .trim()

android {
    namespace = "app.thisistea"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.thisistea"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "TEA_API", "\"${teaApi.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
