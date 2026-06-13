import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Release signing credentials, resolved in priority order:
//   1. composeApp/keystore.properties  (gitignored — local release builds)
//   2. Gradle/env property               (CI: e.g. -PARCANA_UPLOAD_STORE_FILE or env var)
// When no keystore is configured (fresh clone, debug-only CI), the release
// build type stays unsigned so `assembleDebug` and dev builds keep working.
val keystoreProperties = Properties().apply {
    val f = file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

fun signingProp(fileKey: String, gradleOrEnvKey: String): String? =
    keystoreProperties.getProperty(fileKey)
        ?: (project.findProperty(gradleOrEnvKey) as String?)
        ?: System.getenv(gradleOrEnvKey)

val hasReleaseKeystore: Boolean =
    signingProp("storeFile", "ARCANA_UPLOAD_STORE_FILE") != null

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.android)
            implementation(libs.androidx.security.crypto)
            implementation(libs.installreferrer)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.navigation.compose)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "org.arcana.mobile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.arcana.mobile"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    // Generate BuildConfig so the app version (versionName) is readable at
    // runtime via BuildConfig.VERSION_NAME (see Platform.android.kt).
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(signingProp("storeFile", "ARCANA_UPLOAD_STORE_FILE")!!)
                storePassword = signingProp("storePassword", "ARCANA_UPLOAD_STORE_PASSWORD")
                keyAlias = signingProp("keyAlias", "ARCANA_UPLOAD_KEY_ALIAS")
                keyPassword = signingProp("keyPassword", "ARCANA_UPLOAD_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        // commonTest runs on the JVM where android.util.Log is a stub that
        // throws "not mocked". Return defaults instead so code under test can
        // call logWarning() in error paths (e.g. FavoritesRepository.refresh).
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

