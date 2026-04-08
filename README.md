# Arcana

One membership. The best studios in your city. No compromises.

---

## Tech

Kotlin Compose Multiplatform — one shared UI codebase targeting both Android and iOS.

- `composeApp/src/commonMain` — shared UI and business logic
- `composeApp/src/androidMain` — Android-specific code
- `iosApp/` — iOS entry point (SwiftUI wrapper around the Compose UI)

## Build

**Android**
```shell
./gradlew :composeApp:assembleDebug
```

**iOS**

Open `/iosApp` in Xcode and run from there.
