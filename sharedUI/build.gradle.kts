import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

/*
 * :sharedUI — the shared Compose Multiplatform UI layer, plus the platform
 * entry points (MainActivity/ArcanaApplication on Android, MainViewController +
 * the iOS framework for the Xcode project). All business logic lives in :sharedLogic
 * (which this module re-exports through the iOS framework). The installable
 * Android APK/AAB is built by :androidApp — this module is an androidLibrary,
 * per AGP 9's requirement that the application plugin leave KMP modules.
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Analytics/observability keys live in a gitignored sharedUI/analytics.properties
// (unchanged location across the module split, so existing local setups keep
// working). CI can instead supply them as Gradle properties or env vars.
val analyticsProperties = Properties().apply {
    val f = file("analytics.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

// Client-safe analytics/observability keys (PostHog project key, Sentry DSN).
// Resolved from a Gradle property or env var; default empty so a fresh clone
// still builds (SDK init no-ops when the value is blank). Provide via
// sharedUI/analytics.properties (gitignored) for local dev, or -P flags / CI
// env vars: ARCANA_POSTHOG_API_KEY, ARCANA_POSTHOG_HOST, ARCANA_SENTRY_DSN.
fun analyticsProp(gradleOrEnvKey: String, default: String = ""): String =
    analyticsProperties.getProperty(gradleOrEnvKey)
        ?: (project.findProperty(gradleOrEnvKey) as String?)
        ?: System.getenv(gradleOrEnvKey)
        ?: default

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
            // Silence the "cannot infer a bundle ID" K/N link warning introduced
            // by exporting :sharedLogic (harmless for a static framework).
            binaryOption("bundleId", "org.arcana.mobile.compose")
            // Surface :sharedLogic's declarations (Analytics/CrashReporter interfaces,
            // IosDeepLinkBridge, DTOs, ViewModels) to Swift with real types
            // instead of opaque ones. Requires the api() dependency below.
            export(project(":sharedLogic"))
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.posthog.android)
            implementation(libs.sentry.android)
        }
        commonMain.dependencies {
            api(project(":sharedLogic"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.navigation.compose)
            // DancingWordmark parses the wordmark grid JSON directly.
            implementation(libs.kotlinx.serialization.json)
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
        minSdk = libs.versions.android.minSdk.get().toInt()

        // Client-safe analytics keys, embedded for runtime SDK init (see
        // analytics/ in androidMain). Blank by default — init no-ops when empty.
        buildConfigField("String", "POSTHOG_API_KEY", "\"${analyticsProp("ARCANA_POSTHOG_API_KEY")}\"")
        buildConfigField("String", "POSTHOG_HOST", "\"${analyticsProp("ARCANA_POSTHOG_HOST", "https://us.i.posthog.com")}\"")
        buildConfigField("String", "SENTRY_DSN", "\"${analyticsProp("ARCANA_SENTRY_DSN")}\"")
    }
    // Generate BuildConfig for the analytics keys above (library BuildConfig —
    // app-version fields live in :androidApp; Platform.android.kt reads the
    // version via PackageManager instead).
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        // The few UI-coupled commonTests here run on the JVM where android.util.Log
        // is a stub that throws "not mocked" — return defaults instead.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}
