import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * :sharedLogic — the Compose-free Kotlin core: networking, DTOs, auth/token storage,
 * analytics facade + taxonomy, DI, ViewModels, and the session controller.
 * Consumed by :sharedUI (Compose UI for both platforms + the iOS framework).
 * No Compose dependency may ever be added here — this module is what survives
 * any UI-framework change and what a native SwiftUI layer would consume directly.
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // api: these types appear in the module's public API surface
            // (StateFlows, LocalDate fields, Koin Module, ViewModel supertype).
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
            api(libs.androidx.lifecycle.viewmodel)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.androidx.security.crypto)
            implementation(libs.installreferrer)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

android {
    namespace = "org.arcana.mobile.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
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
