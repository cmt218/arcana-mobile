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

## Architecture

This is a **Kotlin Compose Multiplatform** project targeting Android and iOS. All UI is written once in Compose and shared via `commonMain`.

**Source sets in `composeApp/src/`:**
- `commonMain/` — shared Kotlin/Compose code for all platforms. This is where new features should live.
- `androidMain/` — Android `actual` implementations and `MainActivity`
- `iosMain/` — iOS `actual` implementations and `MainViewController` (wrapped by SwiftUI in `iosApp/`)
- `commonTest/` — shared tests using `kotlin-test`

**Platform abstraction pattern:** `Platform.kt` in `commonMain` declares an `expect interface`; `Platform.android.kt` and `Platform.ios.kt` provide `actual` implementations. Follow this pattern for any platform-divergent behavior. `getBaseUrl()` is an existing example — returns `http://10.0.2.2:8000` on Android (emulator host alias) and `http://localhost:8000` on iOS.

**Networking:** Ktor 3.x with `kotlinx-serialization`. The `networking/` package in `commonMain` contains `CadenceApiClient.kt` with the shared `HttpClient` and suspend functions for each endpoint. Data classes live in `data/` and are annotated with `@Serializable`. ViewModels live in feature packages (e.g. `classes/ClassesViewModel.kt`) and expose `StateFlow<UiState>` sealed interfaces with `Loading`, `Success`, and `Error` states.

**Android dev networking:** The debug source set (`src/debug/`) contains a `network_security_config.xml` that permits cleartext to `localhost` and `10.0.2.2`. This is debug-only and cannot ship in release builds.

**iOS entry point flow:** `iosApp/ContentView.swift` → `MainViewControllerKt.MainViewController()` (Kotlin) → Compose UI.

**Key versions (see `gradle/libs.versions.toml`):**
- Kotlin: 2.3.0
- Compose Multiplatform: 1.10.0
- Android min/target SDK: 24/36
- Package: `org.cadence.mobile`
