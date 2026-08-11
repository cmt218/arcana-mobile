# Arcana

One membership. The best studios in your city. No compromises.

---

## Tech

Kotlin Compose Multiplatform — one shared UI codebase targeting both Android and iOS.

- `sharedLogic/` — Compose-free Kotlin core: networking, DTOs, auth, analytics, ViewModels, session (holds the main test suite; platform actuals in its androidMain/iosMain)
- `sharedUI/` — shared Compose Multiplatform UI + platform entry points (MainActivity/ArcanaApplication on Android, MainViewController + the iOS framework)
- `androidApp/` — installable Android application shell (applicationId, versions, signing, manifest only)
- `iosApp/` — Xcode project: the SwiftUI Liquid Glass shell (native TabView + auth/splash chrome on iOS) hosting per-tab Compose content

## Build

**Android**
```shell
./gradlew :androidApp:assembleDebug
```

**iOS**

Open `/iosApp` in Xcode and run from there.

## Agent tooling

Google's `android` CLI (`~/.local/bin/android`) is installed for on-device inspection of the Android build: `android layout` dumps the UI semantics as JSON and `android screen capture -a` produces an annotated screenshot whose regions can be tapped via `adb shell input`. Four Android skills live in `.claude/skills/`.

It is Android-only, and several catalogue skills give advice that is wrong for a Compose Multiplatform project. **Read the "Android CLI — agent tooling" section of [CLAUDE.md](CLAUDE.md) before using any of it** — particularly the "Skills that must NOT be used" list.
