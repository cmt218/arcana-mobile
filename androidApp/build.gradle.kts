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

val hasReleaseKeystore: Boolean =
    signingProp("storeFile", "ARCANA_UPLOAD_STORE_FILE") != null

android {
    namespace = "org.arcana.mobile.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.arcana.mobile"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 8
        versionName = "1.0.7"
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
}

dependencies {
    implementation(project(":sharedUI"))
}
