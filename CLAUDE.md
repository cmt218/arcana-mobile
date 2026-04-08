# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

**Android:**
```bash
./gradlew :composeApp:assembleDebug
```

**iOS:** Open `/iosApp/iosApp.xcodeproj` in Xcode and run from there. The Kotlin framework is compiled as a static framework and linked by Xcode.

**Tests (shared/common):**
```bash
./gradlew :composeApp:test
```

No linter is configured (no ktlint/detekt).

**Clearing the iOS Simulator Keychain** (useful when testing auth flows): Simulator app → Device → Erase All Content and Settings. Uninstalling the app alone does not clear Keychain items.

## Architecture

This is a **Kotlin Compose Multiplatform** project targeting Android and iOS. All UI is written once in Compose and shared via `commonMain`.

**Source sets in `composeApp/src/`:**
- `commonMain/` — shared Kotlin/Compose code for all platforms. This is where new features should live.
- `androidMain/` — Android `actual` implementations and `MainActivity`
- `iosMain/` — iOS `actual` implementations and `MainViewController` (wrapped by SwiftUI in `iosApp/`)
- `commonTest/` — shared tests using `kotlin-test`

**Platform abstraction pattern:** `Platform.kt` in `commonMain` declares an `expect interface`; `Platform.android.kt` and `Platform.ios.kt` provide `actual` implementations. Follow this pattern for any platform-divergent behavior. `getBaseUrl()` is an existing example — returns `http://10.0.2.2:8000` on Android (emulator host alias) and `http://localhost:8000` on iOS.

**Dependency injection:** Koin 4.x. `di/AppModule.kt` in `commonMain` defines all bindings. Koin is started at the platform entry point — `ArcanaApplication.onCreate()` on Android, `MainViewController()` on iOS — before any Compose code runs. Use `koinInject()` for values and `koinViewModel()` for ViewModels in composables.

**Auth architecture:**
- `ArcanaApiClient` owns a `StateFlow<Boolean>` (`isAuthenticated`) that is the single source of truth for session state. It is initialized from `TokenStorage.isLoggedIn` at startup and updated by `login()`, `register()`, `logout()`, and token refresh failure.
- `App.kt` collects `isAuthenticated` and gates the entire main scaffold behind it. A session-scoped `ViewModelStore` (provided via `LocalViewModelStoreOwner`) wraps `MainScaffold` so all authenticated ViewModels are destroyed on logout and recreated fresh on next login.
- `auth/SecureStorage` is an `expect/actual` class: `EncryptedSharedPreferences` on Android, iOS Keychain via CoreFoundation/Security on iOS. `TokenStorage` wraps it with typed `accessToken`/`refreshToken` properties.
- The Ktor `Auth` plugin handles Bearer header injection and 401/token-refresh automatically for all authenticated requests. `sendWithoutRequest` excludes the login, register, and refresh endpoints from receiving auth headers.

**Networking:** Ktor 3.x with `kotlinx-serialization`. `networking/ArcanaApiClient.kt` contains the `HttpClient` and all endpoint methods. Data classes live in `data/` and are annotated with `@Serializable`. ViewModels live in feature packages (e.g. `classes/ClassesViewModel.kt`) and expose `StateFlow<UiState>` sealed interfaces with `Loading`, `Success`, and `Error` states.

**Android dev networking:** The debug source set (`src/debug/`) contains a `network_security_config.xml` that permits cleartext to `localhost` and `10.0.2.2`. This is debug-only and cannot ship in release builds.

**iOS entry point flow:** `iosApp/ContentView.swift` → `MainViewControllerKt.MainViewController()` (Kotlin) → Compose UI.

**Key versions (see `gradle/libs.versions.toml`):**
- Kotlin: 2.3.0
- Compose Multiplatform: 1.10.0
- Koin: 4.2.0
- Ktor: 3.1.2
- Android min/target SDK: 24/36
- Package: `org.arcana.mobile`
