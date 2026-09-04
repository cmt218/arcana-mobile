import java.io.FileInputStream
import java.util.Properties

/*
 * :androidApp — the installable Android application shell. Holds ONLY
 * packaging concerns: applicationId, version fields, release signing, and the
 * top-level manifest. All code (including MainActivity/ArcanaApplication) and
 * resources live in :sharedUI (androidMain) and :sharedLogic, whose manifests and
 * res merge into this module's build.
 *
 * Release: ./gradlew :androidApp:bundleRelease
 *   → androidApp/build/outputs/bundle/release/androidApp-release.aab
 */
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.sentry)
}

// Release signing credentials, resolved in priority order:
//   1. androidApp/keystore.properties   (new canonical location)
//   2. sharedUI/keystore.properties     (pre-rename location — still honored so
//      existing local setups keep working without moving files)
//   3. Gradle/env property              (CI: e.g. -PARCANA_UPLOAD_STORE_FILE)
// When no keystore is configured (fresh clone, debug-only CI), the release
// build type stays unsigned so `assembleDebug` and dev builds keep working.
val keystorePropertiesDir: File? = run {
    val here = file("keystore.properties")
    val legacy = rootProject.file("sharedUI/keystore.properties")
    if (here.exists()) here.parentFile else if (legacy.exists()) legacy.parentFile else null
}
val keystoreProperties = Properties().apply {
    keystorePropertiesDir?.let { dir ->
        FileInputStream(File(dir, "keystore.properties")).use { load(it) }
    }
}

// Resolve a (possibly relative) storeFile path against the directory the
// properties file came from, so a legacy sharedUI/keystore.properties with a
// relative path keeps working post-split.
fun resolveStoreFile(path: String): File =
    File(path).let { if (it.isAbsolute) it else File(keystorePropertiesDir ?: projectDir, path) }

fun signingProp(fileKey: String, gradleOrEnvKey: String): String? =
    keystoreProperties.getProperty(fileKey)
        ?: (project.findProperty(gradleOrEnvKey) as String?)
        ?: System.getenv(gradleOrEnvKey)

// Sentry org auth token (scope org:ci) for the release R8 mapping upload.
// Resolved from the env/Gradle property CI uses, then the sentry-cli config the
// iOS dSYM upload already reads. Absent token = build still succeeds, no upload.
val sentryAuthToken: String? =
    (System.getenv("SENTRY_AUTH_TOKEN") ?: project.findProperty("SENTRY_AUTH_TOKEN") as String?)
        ?: listOf(
            File(System.getProperty("user.home"), ".sentryclirc"),
            rootProject.file("iosApp/.sentryclirc"),
        ).firstOrNull { it.exists() }
            ?.readLines()
            ?.firstOrNull { it.trimStart().startsWith("token") && it.contains("=") }
            ?.substringAfter("=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

val hasReleaseKeystore: Boolean =
    signingProp("storeFile", "ARCANA_UPLOAD_STORE_FILE") != null

android {
    namespace = "org.arcana.mobile.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.arcana.mobile"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 11
        versionName = "1.1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = resolveStoreFile(signingProp("storeFile", "ARCANA_UPLOAD_STORE_FILE")!!)
                storePassword = signingProp("storePassword", "ARCANA_UPLOAD_STORE_PASSWORD")
                keyAlias = signingProp("keyAlias", "ARCANA_UPLOAD_KEY_ALIAS")
                keyPassword = signingProp("keyPassword", "ARCANA_UPLOAD_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // `qa` is `release` plus cleartext to the emulator host loopback, so the
        // regression suite can drive a minified, non-debuggable build against a
        // local server. Debug-signed, never published; `release` is unaffected.
        create("qa") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

sentry {
    org.set("arcana-i4")
    projectName.set("arcana-android")
    // Ship and upload the R8 mapping: without it a minified release crash reaches
    // Sentry as unreadable one-letter frames, which also breaks issue grouping.
    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(sentryAuthToken != null)
    sentryAuthToken?.let { authToken.set(it) }
    ignoredBuildTypes.set(setOf("debug", "qa"))
    // The SDK is declared by :sharedUI; let neither auto-installation nor
    // bytecode instrumentation change what ships behind our back.
    autoInstallation { enabled.set(false) }
    tracingInstrumentation { enabled.set(false) }
}

dependencies {
    implementation(project(":sharedUI"))
}
