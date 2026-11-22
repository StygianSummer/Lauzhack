plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "tea"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "tea.server.ApplicationKt"
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    // H2: the zero-setup fallback when DATABASE_URL is unset (and the test database)
    implementation(libs.h2)

    implementation(libs.bouncycastle.bcprov)
    implementation(libs.nbvcxz)
    implementation(libs.angus.mail)
    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
}

tasks.test {
    useJUnitPlatform()
}

// `./gradlew :server:run` runs from the repo root, so the default CONTENT_DIR=content finds the
// items (leaderboard XP) and the field templates. Without this the server ran in server/, found no
// content and silently fell back to verdict-only XP and the built-in placeholder email.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
