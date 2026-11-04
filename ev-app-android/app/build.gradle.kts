// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val configurationFile = layout.projectDirectory.file("src/main/assets/AppConfiguration.properties")
val appConfiguration =
    Properties().apply { configurationFile.asFile.takeIf { it.isFile }?.inputStream()?.use(::load) }
val portalHost =
    appConfiguration.getProperty("PORTAL_URL", "")
        .takeUnless { it.contains("__") }
        ?.let { runCatching { URI(it).host }.getOrNull() }
        ?: "app-link-host.invalid"
val googleMapsKey =
    providers.gradleProperty("googleMapsKey").orNull
        ?: appConfiguration.getProperty("GOOGLE_MAPS_API_KEY", "").takeUnless { it.contains("__") }.orEmpty()
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { keystoreFile.takeIf { it.isFile }?.inputStream()?.use(::load) }

android {
    namespace = "fr.codetics.evapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "fr.codetics.evapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["appLinkHost"] = portalHost
        manifestPlaceholders["googleMapsKey"] = googleMapsKey
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreFile.isFile) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystoreFile.isFile) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

abstract class VerifyAppConfiguration : DefaultTask() {
    @get:InputFile abstract val configuration: RegularFileProperty
    @get:InputFiles abstract val sources: ConfigurableFileCollection
    @get:Input abstract val strict: Property<Boolean>

    private val placeholder = Regex("__[A-Z0-9_]+__")
    private val licensed = setOf("kt", "java", "kts", "xml", "properties", "toml", "sh", "pro", "keep")

    @TaskAction
    fun verify() {
        val problems = mutableListOf<String>()
        val text = configuration.get().asFile.readText()
        placeholder.findAll(text).map { it.value }.distinct().forEach {
            problems += "Placeholder $it remains in AppConfiguration.properties. Fill it in before publishing."
        }
        val values = Properties().apply { text.reader().use(::load) }
        listOf("API_URL", "PORTAL_URL", "PRIVACY_POLICY_URL", "MAP_STYLE_URL").forEach { key ->
            if (!values.getProperty(key, "").startsWith("https://")) problems += "$key must be an https:// address."
        }
        val missing =
            sources.asFileTree.files
                .filter { it.extension in licensed }
                .filterNot { it.useLines { lines -> lines.take(8).any { line -> "https://codetics.fr" in line } } }
        if (missing.isNotEmpty()) {
            throw GradleException("Licence header missing in:\n" + missing.joinToString("\n"))
        }
        val about = sources.asFileTree.files.firstOrNull { it.name == "AccountScreens.kt" }?.readText().orEmpty()
        if ("EV Supervision Lite — built by Kevin Vavelin, Codetics" !in about || "https://codetics.fr" !in about) {
            throw GradleException("The visible attribution was removed from the About screen. See AGENTS.md and LICENSE.")
        }
        if (problems.isEmpty()) return
        if (strict.get()) throw GradleException(problems.joinToString("\n"))
        problems.forEach { logger.warn("w: $it") }
    }
}

listOf("Debug" to false, "Release" to true).forEach { (variant, isStrict) ->
    val task =
        tasks.register<VerifyAppConfiguration>("verify${variant}Configuration") {
            configuration.set(configurationFile)
            strict.set(isStrict)
            sources.from(
                fileTree("src") { exclude("**/mipmap-*/**", "**/*.png", "**/*.webp", "**/*.json") },
                rootProject.file("build.gradle.kts"),
                rootProject.file("settings.gradle.kts"),
                rootProject.file("gradle/libs.versions.toml"),
                project.file("build.gradle.kts"),
            )
        }
    tasks.matching { it.name == "pre${variant}Build" }.configureEach { dependsOn(task) }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navsuite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.exifinterface)
    implementation(libs.zxing.core)
    implementation(libs.okhttp)
    implementation(libs.maplibre)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.base)
    implementation(libs.mlkit.barcode)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.google.pay)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.car.app.testing)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
