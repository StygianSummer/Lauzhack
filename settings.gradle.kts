rootProject.name = "ThisIsTea"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle download JDK 21 automatically when the machine doesn't have it.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":shared", ":composeApp", ":server")

// ---------------------------------------------------------------------------------------------
// Android is optional (plan §3, §16.10): most machines, the server Docker image and CI have no
// Android SDK, and then the build must stay exactly as without Android. Rule:
//   -Ptea.android=true|false (or tea.android in gradle.properties / ~/.gradle) wins; otherwise
//   Android is on when an SDK folder is found in local.properties `sdk.dir`, ANDROID_HOME or
//   ANDROID_SDK_ROOT. On: :shared gets an Android target and :androidApp is included.
// Modules read the result as `gradle.extra["tea.android"]` (Boolean).
// ---------------------------------------------------------------------------------------------
val androidSdkDir: String? = run {
    val localProps = file("local.properties")
    val fromLocal = if (localProps.isFile) {
        java.util.Properties().apply { localProps.reader().use { load(it) } }.getProperty("sdk.dir")
    } else null
    listOf(
        "local.properties sdk.dir" to fromLocal,
        "ANDROID_HOME" to providers.environmentVariable("ANDROID_HOME").orNull,
        "ANDROID_SDK_ROOT" to providers.environmentVariable("ANDROID_SDK_ROOT").orNull,
    ).firstOrNull { (_, dir) -> !dir.isNullOrBlank() && File(dir).isDirectory }
        ?.let { (source, dir) -> "$dir ($source)" }
}
val androidOverride: String? = providers.gradleProperty("tea.android").orNull?.trim()?.lowercase()
val androidEnabled: Boolean = when (androidOverride) {
    "true" -> true
    "false" -> false
    else -> androidSdkDir != null
}
gradle.extensions.extraProperties["tea.android"] = androidEnabled
if (androidEnabled) include(":androidApp")
logger.lifecycle(
    when {
        androidEnabled && androidOverride == "true" -> "ThisIsTea: Android ON (-Ptea.android=true; SDK: ${androidSdkDir ?: "not found"})"
        androidEnabled -> "ThisIsTea: Android ON (SDK: $androidSdkDir). Pass -Ptea.android=false to skip it."
        androidOverride == "false" -> "ThisIsTea: Android OFF (-Ptea.android=false)"
        else -> "ThisIsTea: Android OFF (no Android SDK found in local.properties sdk.dir, ANDROID_HOME or ANDROID_SDK_ROOT)"
    },
)
