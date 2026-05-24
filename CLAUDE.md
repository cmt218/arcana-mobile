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

**Date/time:** `kotlinx-datetime` in commonMain. Use `Clock.System.todayIn(TimeZone.currentSystemDefault())` for today, `Clock.System.now().toLocalDateTime(tz)` for current local time. `DayOfWeek` and `Month` are enums with `.name` returning uppercase strings (take(3) for the design's three-letter abbreviations).

**Android dev networking:** The debug source set (`src/debug/`) contains a `network_security_config.xml` that permits cleartext to `localhost` and `10.0.2.2`. This is debug-only and cannot ship in release builds.

**iOS entry point flow:** `iosApp/ContentView.swift` → `MainViewControllerKt.MainViewController()` (Kotlin) → Compose UI.

**Key versions (see `gradle/libs.versions.toml`):**
- Kotlin: 2.3.0
- Compose Multiplatform: 1.10.0
- Koin: 4.2.0
- Ktor: 3.1.2
- kotlinx-datetime: 0.7.1
- Navigation Compose: 2.9.2 (JetBrains CMP port — pinned to a version matched to the CMP release in the CMP CHANGELOG; do not bump independently of `composeMultiplatform`)
- Android min/target SDK: 24/36
- Package: `org.arcana.mobile`

## Design system

The brand-aligned theming lives in `commonMain/kotlin/org/arcana/mobile/theme/` and the reusable UI primitives in `commonMain/kotlin/org/arcana/mobile/ui/`. **Screens should never hand-roll `TextStyle`s, hex colors, or raw icon paths** — compose them from these primitives. The brand color hexes and the typography hierarchy are sourced from the brand identity doc and the typography doc respectively (see the parent `arcana/CLAUDE.md` for live links).

**`theme/AppColors.kt`** — five primaries are the brand doc's source of truth: `Lime #B6C24F`, `Moss #3C5D1A`, `Stone #F5F2ED`, `Wood #3B2415`, `BurntNectar #F65713`. Derived variants (`LimeBright/Deep`, `MossDeep/Light`, `Stone2`, `Paper`) are HSL-style shifts of those primaries — recompute them, don't hand-edit, if a primary changes. `Ink/Graphite/Charcoal/Ash/Ash2/Mist/Mist2` are the warm neutrals. `StoneAlpha*` are translucent helpers for dark surfaces.

**`theme/Typography.kt`** — three font families, sourced from `composeResources/font/`:
- `LeagueSpartan` — display headlines, H2, CTAs (ALL CAPS)
- `DmSans` — body, overlines, captions, nav
- `CormorantGaramond` (italic) — sparing emotional accents, one line per screen max

**Do not add a monospace family.** The typography doc has exactly three; there is no `JetBrains Mono` / `MonoText`. For metadata stamps use `Overline` (DM Sans Bold caps with breathable tracking); for sentence-case microcopy use `Caption` (DM Sans Medium).

**`theme/ArcanaTheme.kt`** — single root wrapper. Resolves the font families once and provides them via `Arcana.fonts.{display,body,accent}`. Also installs the Material3 `lightColorScheme` keyed to the Stone palette and sets `LocalContentColor` to `Ink`. Wrap the app once in `App.kt`; downstream composables consume from `Arcana.fonts.*`.

**`theme/WordmarkLogo.kt`** — the dot-matrix "arcana" wordmark. The PNG (`composeResources/drawable/wordmark.png`) is cropped tight to the text bbox (aspect ratio `4.88:1`) so it renders flush left/right in any layout — no offsets or alignment hacks needed. Pass `tint = Moss` (or any color) to recolor every dot via `ColorFilter.tint`; leave null to use the asset's native stone + lime dots (the splash does this).

## Shared UI library — `ui/` package

The `ui/` package is the mobile design system. New screens compose from these; new shared affordances belong here.

- **`Text.kt`** — `Display`, `Heading2`, `Heading3`, `Overline`, `BodyText`, `AccentText`, `Caption`. Each takes a `size: Int` (sp) and `color: Color`. `Display`, `Heading2`, and `Overline` uppercase their input automatically.
- **`Icons.kt`** — `ArcanaIcons` exposes `DrawableResource` handles to the icon set in `composeResources/drawable/icon_*.xml`. `StrokeIcon(icon, size, tint)` renders one via Material3's `Icon` with `ColorFilter` tinting. **To add a new icon:** drop a 24×24 `<vector>` XML in `composeResources/drawable/` (stroke baked at 1.8, `fillColor="#00000000"`, `strokeLineCap/Join="round"`) and add a one-liner to `ArcanaIcons`. Works on Android and iOS unchanged.
- **`Dots.kt`** — `Pulse`, `DottedDivider`, `SectionRule`, `DotField`. The dot is the brand's repeating gesture; reach for these instead of inventing bespoke decoration.
- **`Buttons.kt`** — `PrimaryCta` (Moss pill with Lime arrow well), `TextLink` (underlined display-type link with trailing icon), `IconCircle` (the recurring round affordance — pass `background` for filled or `borderColor` for outlined).
- **`Inputs.kt`** — `ArcanaTextField` (hairline-underline input; Mist → Moss on focus). Built on `BasicTextField`, not Material's filled/outlined; resist the urge to swap.
- **`TabBar.kt`** — the bottom nav. `ArcanaTab` enum is the source of truth for the three primary destinations (`Home`, `Schedule`, `Profile`); the Profile tab renders as the member's avatar. `ArcanaTabBar` handles its own `safeBottomBarPadding()` internally — callers in `App.kt` do not pass a safe-area modifier.
- **`Insets.kt`** — `Modifier.safeContentPadding()` (top + horizontal), `Modifier.safeBottomBarPadding()` (bottom + horizontal), `Modifier.safeHorizontalPadding()` (horizontal only). **Always prefer these to `statusBarsPadding()` / `navigationBarsPadding()`** so display cutouts (camera punch-outs in landscape) are respected. They wrap `WindowInsets.safeDrawing.only(...)` and work the same on Android and iOS.

## Surface conventions

The app is **Stone-primary (light)** with two intentional dark counterweights: the splash sits on **Wood** (the only screen that does), and the Profile hero sits on **Ink**. Burnt Nectar appears only as the splash's ambient glow; do not introduce it elsewhere.

**Full-bleed dark hero pattern** — when a screen needs a dark hero at the top with a Stone body below (currently Profile), use this structure:

```kotlin
Box(modifier = Modifier.fillMaxSize().background(Stone)) {
    // Ink strip behind the hero — covers ~55% so iOS top-overscroll reads as ink.
    Box(Modifier.fillMaxWidth().fillMaxHeight(0.55f).background(Ink))
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { Hero() }            // its own Ink background, sits over the strip
        // post-hero items wear Stone backgrounds (see Profile's `StoneWrap` helper)
    }
}
```

This avoids the iOS overscroll-flash trap (transparent LazyColumn lets the Ink strip show through during top overscroll without leaving Ink visible below the last item). `ArcanaTabBar` separately fills its full slot with Stone, so nothing behind the Scaffold can bleed through under the visible tab row.

## Spacing & sizing

Pad in **4dp increments** (`4 / 8 / 12 / 16 / 20 / 24 / 28 / 32 / 40 / 48`). Text sizes in **2sp increments** (`10 / 12 / 14 / 16 / 18 / 20 / 22 / 26 / 28 / 36 / 44 / 52 / 56`). The few intentional exceptions — `1.dp` hairlines, the `116.dp` avatar diameter — should stay rare and obvious.

## Navigation

`App.kt` hosts a `NavHost` keyed off `navigation/ArcanaDestinations.kt` — a sealed `ArcanaDestination` with `@Serializable` data objects per destination (no string routes). The bottom bar's active tab is derived from `currentBackStackEntryAsState` via `hasRoute<T>()`; it hides on non-tab destinations (currently just `StudioSelection`) so a stray tab tap mid-flow can't silently pop the in-progress entry off the stack.

Tab navigation uses the standard `popUpTo(start) { saveState = true }` / `launchSingleTop` / `restoreState` block so each tab keeps its own back stack + scroll position.

Transitions are pinned at the `NavHost` level (150ms fade, all four slots) for cross-platform consistency — iOS's default slide reads wrong between sibling tabs. Override per-`composable` if a destination needs its own transition.

**Dependency pinning.** `org.jetbrains.androidx.navigation:navigation-compose` is the JetBrains CMP port, not Jetpack Navigation. Its version must match the `composeMultiplatform` release — find the matched version in the CMP CHANGELOG, not in Jetpack/Android docs. Bumping CMP almost always means bumping nav-compose; bumping nav-compose alone risks pulling a conflicting Compose runtime transitive.

## Open items

- **Live data wiring.** Home / Schedule / Profile currently render placeholder content (mock reservations, mock studios, hardcoded member name). `ArcanaApiClient` + `ClassesViewModel` are wired and ready; replace the screen-local mock data structures with real `StateFlow<UiState>` consumption when the corresponding endpoints land.
