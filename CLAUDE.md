# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

**Android:**
```bash
./gradlew :androidApp:assembleDebug
```

**iOS:** Open `/iosApp/iosApp.xcodeproj` in Xcode and run from there. The Kotlin framework is compiled as a static framework and linked by Xcode.

**Tests:**
```bash
./gradlew :sharedLogic:testDebugUnitTest       # the main suite (logic + ViewModels + session)
./gradlew :sharedUI:testDebugUnitTest   # the few UI-coupled tests (DotMatrixLoader, LateCancelWindow, OpensAtLabel)
```
(The aggregate `test` tasks do NOT accept `--tests`; filter on the `testDebugUnitTest` variants.)

**ALWAYS compile BOTH targets when touching shared code** — Android alone won't catch Kotlin/Native errors:
```bash
./gradlew :sharedLogic:compileDebugKotlinAndroid :sharedUI:compileDebugKotlinAndroid       # JVM/Android
./gradlew :sharedLogic:compileKotlinIosSimulatorArm64 :sharedUI:compileKotlinIosSimulatorArm64  # Kotlin/Native (iOS)
./gradlew :sharedLogic:compileTestKotlinIosSimulatorArm64  # Kotlin/Native test compile — commonTest only runs as JVM/Android otherwise
```
JVM-only APIs compile on Android but break the iOS build — notably `String.format` / `"%02d".format(x)` / `java.*` / `Locale`. Use string templates, `padStart`, and `kotlinx-datetime` instead. (`compileKotlinMetadata` is a no-op SKIP in this project — don't rely on it.)

Backtick test names (`` @Test fun `like this`() ``): Kotlin/Native rejects **commas and colons** in backtick identifiers ("Name contains illegal characters") — colons additionally break the Android test compile. Neither punctuation mark is safe as a swap-in for the other; rephrase the sentence so it needs no separator at all.

No linter is configured (no ktlint/detekt).

**Clearing the iOS Simulator Keychain** (useful when testing auth flows): Simulator app → Device → Erase All Content and Settings. Uninstalling the app alone does not clear Keychain items.

## Comments — keep them short

Production code in this repo is lightly commented on purpose. Verbose comments are
not free: they push the actual code off screen, go stale silently, and make a file
slower to read for someone who already has the context. **Long comments are treated
as a defect here, not as thoroughness.**

- **Default to none.** Well-named code needs no narration. Never restate what the
  line does.
- **Comment only the non-obvious *why*:** a constraint that will be violated by an
  innocent-looking edit, a value that came from measurement, a deliberate deviation.
- **Two or three lines.** If it needs more, it belongs in `docs/` or a Trello card,
  linked by id — not inlined.
- **No changelogs in code.** No "previously this did X", no incident narration, no
  "caught during QA on <date>", no restating what a test already proves.
- **Never annotate a dependency, import, or version bump.** `libs.versions.toml`
  has no comments; keep it that way.
- KDoc on a shared public API may be longer, but describe the contract, not its
  history.

Tests are the exception: comment them as freely as is useful, since a test's intent
is often less obvious than its mechanics.

If you find yourself explaining a subtlety at length, that is usually a signal the
code should be clearer — extract a named function instead of writing a paragraph.

## Android CLI — agent tooling (installed 2026-08-10)

Google's `android` CLI is installed at `~/.local/bin/android` (v1.0.15985488). Verify with `command -v android`; if missing:
`curl -fsSL https://dl.google.com/android/cli/latest/darwin_arm64/install.sh | bash`

**It is Android-only.** It does nothing for the iOS half of this app — no iOS build, no simulator, no Swift. For iOS use the Claude Code iOS Simulator MCP, or the idb fallback documented in `docs/perf/README.md`.

Four skills are installed under `.claude/skills/`: `android-cli`, `perfetto-trace-analysis`, `perfetto-sql`, `r8-analyzer`. **That set is deliberate and minimal — read "Skills that must NOT be used" below before adding any others.** Install more with `android skills add --agent=claude-code --project=. <skill-name>`.

### When to reach for what

| Task | Use |
|---|---|
| "Does this Android UI change look right?" / verify a screen without asking Cole to check | `android screen capture -a` + `android layout` (below) |
| Reproduce/inspect an Android-only bug on a real device | `android layout --diff` loop (below) |
| Android jank, frame drops, ANRs, slow startup, memory | `perfetto-trace-analysis` + `perfetto-sql` skills |
| The Android arm of the **CMP 1.12.0 A/B** (see `docs/perf/README.md`) | `perfetto-trace-analysis` — the existing runbook is iOS-only |
| APK/AAB size, release build optimization, minification | `r8-analyzer` skill (read the R8 note below first) |
| Looking up official Android/AGP/Jetpack behavior | `android docs search <query>` |
| Finding Android build outputs across the 3 modules | `android describe --project_dir=.` (verified: correctly picks `:androidApp` as the only APK producer) |

### Driving a real Android device — VERIFIED 2026-08-10

Verified end-to-end against a physical Pixel 9 Pro (Android 16, serial `49141FDAP0000L`) running a debug build. A `Pixel_9_Pro` AVD also exists (`android emulator list`).

**The CLI can inspect but NOT interact.** There is no `android tap`/`input` command. Input is always `adb shell input ...`. This is by design and is what the bundled skill instructs.

1. **Primary — the semantics dump.** Fast (~4s), small, cheap on context:
   ```
   android layout --pretty -o /tmp/layout.json
   ```
   Returns a **flat JSON list** (NOT a tree, despite the name) with `text`, `center`, `bounds`, `interactions`, `content-desc`, `off-screen`. There is no parent/child nesting — correlate label to control **by coordinate proximity**. The `key` field is identical for every node on a screen (it is a window key, not an element id) — do not use it to identify elements.

   **A clickable entry carrying no `text` is NOT necessarily an unlabeled control.** The dump emits a control's accessible label and its clickable node as *separate sibling entries at nearly identical coordinates* — e.g. the Profile settings button appears as `{"interactions":["clickable"],"center":"[866,239]"}` plus `{"content-desc":"Settings","center":"[865,238]"}`. Always coordinate-match before concluding something is unlabeled, and widen the match window for full-width rows (their center can sit 100+px from their text).

2. **`--diff` for loops.** After an interaction, `android layout --diff --pretty` returns `{"added": [...], "removed": [...]}` — an *object*, not a list. Use this instead of a full dump when stepping through a flow; it keeps context small.

3. **Coordinates are directly tappable.** They are in the same space `adb shell wm size` reports (this device: 960×2142), so `center` feeds straight into `adb shell input tap X Y`. No scaling needed.

4. **Fallback — the annotated screenshot.** When `layout` is missing something (icon-only controls, animations, Canvas-drawn UI):
   ```
   android screen capture -a -o /tmp/shot.png
   adb shell input $(android screen resolve --screenshot=/tmp/shot.png --string="tap #38")
   ```
   `--annotate` is **vision-based, not semantics-based, and finds substantially more than `layout` does** — on the Profile screen `layout` returned 33 nodes while the annotated PNG boxed ~66 regions, including every icon `layout` missed (settings gear, tab icons, studio monograms, the Manage chevron). **Always visually read the PNG before resolving a label.** The label numbers are drawn adjacent to (not inside) their boxes and overlap heavily on dense screens — reading them off the image by eye is genuinely error-prone, so confirm with `screen resolve` (it prints the coordinates) before tapping.

5. **Restore state when done.** You are driving Cole's real device with his real account. Put the app back where you found it (`adb shell dumpsys activity activities | grep topResumedActivity` to check) and never tap anything that books, cancels, pays, or deletes.

### Accessibility semantics — the labeling pass (done 2026-08-10)

Icon-only controls used to be genuinely unlabeled: `StrokeIcon`'s `contentDescription` existed as a parameter but was never passed at any call site, so a tappable well containing nothing but a glyph had no accessible name at all. Verified before/after on device: the Profile settings gear was the one unlabeled control on that screen, and is now labeled.

The pass followed the rule now documented on `StrokeIcon` and `IconCircle`: **label icons that ARE the control, keep decorative icons null.** Labeling every icon would make TalkBack announce redundant names ("Continue, arrow right") because Compose merges descendant semantics into the clickable ancestor. Decorative call sites carry a `// decorative` comment so the null reads as deliberate.

Two structural pieces worth knowing:
- `IconCircle` takes a `contentDescription` — **pass it whenever `onClick` is non-null.**
- `StudioAccordion`'s select-toggle is labeled via `Modifier.semantics` on the well rather than on the icon, because the check glyph is absent in the unselected and partial states.

Still open: tab-bar items have no `Role.Tab` / selected-state semantics (the visible label supplies the name, so they read acceptably but not ideally), and there are no `testTag`s anywhere.

### Doc-vs-binary discrepancies (the published docs are wrong; the binary is right)

- Skill name is a **positional arg**, not `--skill=`: `android skills add --agent=claude-code --project=. r8-analyzer`
- Agent id is **`claude-code`**, not `CLAUDE` (run `android skills add` with a bad value to see the full valid list)
- `screen resolve` takes **`--screenshot=`**, not `--screen=` as the bundled `android-cli` skill's `references/interact.md` claims
- The accessibility-description field in `layout` output is **`content-desc`** (hyphenated), not `contentDesc` as that same reference file claims — filtering on the wrong key silently reports zero labels
- `android screen capture --help` errors; use `android screen capture` with no args to print usage

### R8 / minification — Android-only, and currently OFF

`androidApp/build.gradle.kts` sets `isMinifyEnabled = false` with no proguard rules file anywhere in the repo. **Release AABs ship unshrunk and unobfuscated.** The `r8-analyzer` skill's first finding will be "there is no R8 configuration."

**R8 affects Android only** — it is a JVM-bytecode→DEX shrinker and has no iOS equivalent. iOS binary size is governed by Kotlin/Native release-mode DCE plus Xcode linker stripping; note that `export(project(":sharedLogic"))` in `sharedUI/build.gradle.kts` makes every exported public declaration a DCE **root**, so the Swift-visible surface is deliberately retained (correct tradeoff, not a leak). The only size cost genuinely shared by both platforms is the `composeResources` payload (8 TTFs, wordmark PNG, 22 vector drawables).

Enabling minification here is **real work, not a switch flip**: kotlinx-serialization, Koin, Ktor, PostHog and Sentry are all reflection- or annotation-sensitive and are the usual breakages. If attempting it, do it on a branch, keep `isMinifyEnabled` behind a full regression pass on a real device, and verify telemetry still initializes and events still land in PostHog before shipping.

### Skills that must NOT be used — READ THIS BEFORE INSTALLING ANY SKILL

This is a **Kotlin Compose Multiplatform** repo. Most of Google's skill catalogue assumes a Jetpack-Compose-and-Views Android app, and following it here produces wrong or destructive advice. Do not install or act on:

- **`version-lookup`** (an `android studio` subcommand) — **actively hazardous here.** It reports *latest* dependency versions. This repo's hard rule is that `composeMultiplatform`, `org.jetbrains.androidx.navigation`, `org.jetbrains.androidx.lifecycle` and `org.jetbrains.compose.material3` must be taken from the **CMP CHANGELOG's matched versions**, never "latest" (see "Key versions" in Architecture). Bumping nav-compose to latest pulls a conflicting Compose runtime transitive. Never take a version from this tool.
- **`navigation-3`** — Nav3 is Jetpack-side. Navigation here is the JetBrains CMP port (`org.jetbrains.androidx.navigation` 2.9.2), pinned to the CMP release. This skill's advice does not transfer and following it breaks the pinning rule.
- **`migrate-xml-views-to-jetpack-compose`** — there are no XML views to migrate. Irrelevant.
- **`styles`, `adaptive`** — written against Jetpack Compose and its Material3 artifact. This app uses `org.jetbrains.compose.material3` (alpha-only by design) plus a hand-rolled design system in `sharedUI/.../theme/` + `ui/`. High wrong-advice risk; compose from the existing primitives instead.
- **`edge-to-edge`** — already done correctly via `enableEdgeToEdge()` + `WindowInsets.safeDrawing.only(...)` in `ui/Insets.kt`, and targetSdk 36 forces it regardless. Nothing to gain.
- **`agp-9-upgrade`** — already on AGP 9.1.0. The real open item is the **AGP 10** horizon (`com.android.kotlin.multiplatform.library`), which this skill does not cover — see the "Known follow-up" note in Architecture.
- **`android studio` bridge commands generally** (`analyze-file`, `find-usages`, `render-compose-preview`) — require an Android Studio Quail 2 Canary install with Gemini signed in. Installed Studio is 2026.1 stable. Not worth a separate canary install.

**Journeys** (natural-language on-device E2E tests, `*.journey.xml` under `src/journeysTest/`) is legitimately interesting — this repo has **zero** on-device coverage (no `androidTest` source sets anywhere; Espresso is declared in `libs.versions.toml` and referenced by no build file). Good candidates would be the welcome-deep-link → survey → claim-your-name flow and login → schedule → filter → detail → book → cancel. But it is agent-driven (slow, nondeterministic, costs tokens per run), Android-only, and inherits the unlabeled-icon problem above. **Do not set this up unprompted** — raise it with Cole first.

## Architecture

This is a **Kotlin Compose Multiplatform** project targeting Android and iOS, split into three Gradle modules (2026-08-10, matching JetBrains' sharedLogic/sharedUI default naming exactly and AGP 9's app-plugin-out-of-KMP-modules requirement):

- **`:sharedLogic`** — the Compose-free Kotlin core: `networking/`, `data/` DTOs, `auth/` (token storage + diagnostics), `analytics/` (Telemetry facade + taxonomy), `di/AppModule.kt`, `navigation/` (destinations + DeepLinkHandler), `session/AppSessionController.kt`, ALL ViewModels, and the pure display-logic helpers (`ScheduleDisplayLogic`, `ClassDetailLogic`, `SpotLayout`, `ScheduleFilter`). Platform actuals live in its `androidMain`/`iosMain` (SecureStorage, PendingTokenSource, Platform; Android ones read the app context from `SharedAndroidContext`, set by `ArcanaApplication`). `commonTest/` here is the main test suite. **No Compose dependency may be added to this module** — it is what survives any UI-framework change.
- **`:sharedUI`** — all Compose UI (screens, `ui/` design system, `theme/`), plus the platform entry points: `MainActivity`/`ArcanaApplication` + Android telemetry impls (androidMain), `MainViewController` + the iOS framework (iosMain). An `androidLibrary` — it is NOT the installable app. The iOS framework `export(project(":sharedLogic"))`s so Swift sees real types; new logic in :sharedLogic that Swift must call needs no extra wiring (api + export cover it).
- **`:androidApp`** — the installable Android application shell: applicationId, versionCode/versionName, release signing, minimal manifest. No code. The manifest application element (activities, deep links, icons) merges in from :sharedUI's androidMain manifest.

New feature code: logic + ViewModel in `:sharedLogic`, screen in `:sharedUI`.

**Known follow-up (AGP 10 horizon):** KGP deprecates `kotlinMultiplatform` + `com.android.library` on AGP 9 (warning-only today) in favor of the single-variant `com.android.kotlin.multiplatform.library` plugin. Migrating will require redesigning two build-type-dependent pieces: the `sharedUI/src/debug/` networkSecurityConfig manifest overlay and the library BuildConfig analytics fields (candidates: move to `:androidApp`, or runtime FLAG_DEBUGGABLE checks). Do this deliberately when AGP forces it, not before.

**Platform abstraction pattern:** `Platform.kt` (:sharedLogic commonMain) declares a plain `interface Platform` plus top-level `expect` declarations (`getPlatform()`, `defaultBaseUrl()`, `logWarning()`, `logDebug()`, `isDebugBuild`, `appVersionName()`); the actuals live in :sharedLogic's `androidMain`/`iosMain` (`Platform.android.kt` / `Platform.ios.kt`). Follow this pattern for any platform-divergent behavior. `defaultBaseUrl()` is an existing example — it currently returns `https://api.arcana.fit` on **both** platforms (the prod cutover is already done; it is NOT a localhost default). To run against a local server, override the base URL in Developer Settings (see "Temporary debug treatment").

**Dependency injection:** Koin 4.x. `di/AppModule.kt` in :sharedLogic's commonMain defines all bindings. Koin is started at the platform entry point — `ArcanaApplication.onCreate()` on Android, `MainViewController()` on iOS — before any Compose code runs. Use `koinInject()` for values and `koinViewModel()` for ViewModels in composables.

**Auth architecture:**
- `ArcanaApiClient` owns a `StateFlow<Boolean>` (`isAuthenticated`) that is the single source of truth for session state. It is initialized from `TokenStorage.isLoggedIn` at startup and updated by `login()`, `completeSignup()`, `logout()`/`forceLogout(cause)`, and token refresh failure.
- `App.kt` collects `isAuthenticated` (via the Koin-injected `AppSessionController`, whose flow is ArcanaApiClient's) and gates the entire main scaffold behind it; all session rules (welcome-token machine, survey gate, first-launch recovery, teardown) live in :sharedLogic `session/AppSessionController.kt` — App.kt only collects state and forwards events. A session-scoped `ViewModelStore` (provided via `LocalViewModelStoreOwner`) wraps `MainScaffold` so all authenticated ViewModels are destroyed on logout and recreated fresh on next login.
- `auth/SecureStorage` is an `expect/actual` class: `EncryptedSharedPreferences` on Android, iOS Keychain via CoreFoundation/Security on iOS. `TokenStorage` wraps it with typed `accessToken`/`refreshToken` properties.
- The Ktor `Auth` plugin handles Bearer header injection and 401/token-refresh automatically for all authenticated requests. `sendWithoutRequest` excludes the login, register, and refresh endpoints from receiving auth headers.

**Networking:** Ktor 3.x with `kotlinx-serialization`. `networking/ArcanaApiClient.kt` contains the `HttpClient` and all endpoint methods. Data classes live in `data/` and are annotated with `@Serializable`. ViewModels live in feature packages (e.g. `classes/ClassesViewModel.kt`) and expose `StateFlow<UiState>` sealed interfaces with `Loading`, `Success`, and `Error` states — where `Error` carries an `ErrorType`, and read endpoints end in `bodyOrThrow()`. See "Error states" below before adding either.

**Date/time:** `kotlinx-datetime` in commonMain. Use `Clock.System.todayIn(TimeZone.currentSystemDefault())` for today, `Clock.System.now().toLocalDateTime(tz)` for current local time. `DayOfWeek` and `Month` are enums with `.name` returning uppercase strings (take(3) for the design's three-letter abbreviations).

**Android dev networking:** The debug source set (`sharedUI/src/debug/`) contains a `network_security_config.xml` that permits cleartext to `localhost` and `10.0.2.2`. This is debug-only and cannot ship in release builds.

**Android release fields** (`versionCode`/`versionName`, signing) live in `androidApp/build.gradle.kts`. Release bundle: `./gradlew :androidApp:bundleRelease` → `androidApp/build/outputs/bundle/release/androidApp-release.aab`. `keystore.properties` is honored from `androidApp/` (canonical) or `sharedUI/` (pre-split location); `analytics.properties` stays at `sharedUI/analytics.properties`.

**iOS entry point flow (Liquid Glass shell, 2026-08-10):** `iosApp/iOSApp.swift` → `ShellModel` (ArcanaShell.swift) → `IosShellBridge.start()` (Koin + telemetry) → native SwiftUI `TabView` hosting per-tab `ComposeUIViewController`s. See the shell section below. (`MainViewController()` in :sharedUI iosMain remains as a legacy single-VC entry point, currently unused.)

**Key versions (see `gradle/libs.versions.toml`):**
- Kotlin: 2.3.10
- Compose Multiplatform: 1.11.1 (concurrent/parallel rendering is ON by default on iOS since 1.11.0)
- Koin: 4.2.0
- Ktor: 3.1.2
- kotlinx-datetime: 0.7.1
- Navigation Compose: 2.9.2 (JetBrains CMP port — pinned to a version matched to the CMP release in the CMP CHANGELOG; do not bump independently of `composeMultiplatform`)
- Lifecycle (`org.jetbrains.androidx.lifecycle`): 2.11.0 — like nav-compose, bump in lockstep with the CMP release notes' listed version (prefer the stable if one exists at that minor).
- Material3 (`org.jetbrains.compose.material3`): 1.11.0-alpha07 — the multiplatform material3 port publishes **alpha-only** since it decoupled from CMP releases (no stable exists past 1.8.x); always use the exact version listed in the CMP release notes. We have shipped alpha material3 from day one; this is normal, not a red flag.
- Android min/target SDK: 24/36 (compileSdk 37 — required by lifecycle 2.11.0's Jetpack delegate; AGP 9.1 warns it was tested up to 36.1, warning-only)
- Package: `org.arcana.mobile`

**Planned upgrade — CMP 1.12.0 when stable (~Sept–Oct 2026):** it ships the iOS lazy-list prefetch scheduler (compose-multiplatform-core PR #3149), the expected fix for remaining schedule scroll hitches. Before/after measurement is repeatable via the runbook in `docs/perf/README.md` (scripted simulator scroll A/B; 1.10.0→1.11.1 already measured −40% dropped frames there).

## Regression inventory — keep it current

`docs/regression/inventory.md` is the checklist consumed by the agent-run full
regression suite (`.claude/skills/full-regression/`, execution runbook at
`docs/regression/runbook.md`). It is only useful if it tracks the app as it
actually is, so this is a hard rule, not a suggestion:

**Any PR that adds, changes, or removes user-facing functionality MUST update
`docs/regression/inventory.md` in the same PR.**
- New surface (screen, ViewModel-backed flow, nav destination) → a new entry.
- Changed behavior on an existing surface → an updated **Expected** on the
  entry that covers it.
- Removed surface → tombstone the entry (`### <ID> — RETIRED (<date>):
  <reason>`), do not delete the line. Entry IDs are stable and are never
  reused.

This isn't just a paperwork requirement: the full regression suite's Phase 1
self-audit diffs the inventory against the current source tree on every run
(every `*Screen.kt`, every ViewModel, every nav destination should trace back
to an inventory entry's **Source:** line, and every entry's **Source:** line
should resolve to a real file) and reports drift as findings. Skipping this
update doesn't skip the check — it just means the next regression run surfaces
your PR as a "stale entry" or "uncovered surface" finding instead of the
inventory being right the first time.

**If you're implementing a change that adds, changes, or removes user-facing
functionality, use the `regression-inventory` skill**
(`.claude/skills/regression-inventory/SKILL.md`) — it teaches the entry
format, ID scheme, area-vs-new-area call, Source-citation rules, the
tombstone rule for removals, and the iOS/Android KEEP-IN-SYNC trap, with a
worked "adding a new tab" example. Before finishing, verify your edit with
`tools/regression/self_audit.sh` (a mechanical, always-exits-0 implementation
of the runbook's Phase 1 checks — it prints `FINDINGS: N`) and require `N=0`
before handing the work back.

## iOS Liquid Glass shell (2026-08-10)

On iOS the top-level chrome is **native SwiftUI**; Compose renders screen content. This is JetBrains' recommended architecture for system Liquid Glass (their Liquid Glass tutorial / the KotlinConf app). **Android is untouched** — App.kt's MainScaffold remains the Android composition.

- **Swift** (`iosApp/iosApp/`): `ArcanaShell.swift` — `ShellModel` (session-driven controller cache) + `ArcanaShellView` (ZStack of `TabView` | AuthFlow | splash overlay). The native tab bar gets system Liquid Glass on iOS 26; on iOS 18.x (there are NO versions between 18 and 26 — Apple jumped to year-based numbering) `ShellModel.init` pins `standardAppearance`+`scrollEdgeAppearance` to the system default background under `#unavailable(iOS 26.0)` — without this, UIKit permanently applies the transparent scroll-edge appearance because Compose content is not a UIScrollView it can observe. The You tab renders the member-initials Moss chip (`AvatarChip` — UIImage MUST bake `.withRenderingMode(.alwaysOriginal)` or the bar template-tints it into a silhouette; initials come from `IosShellBridge.observeMemberInitials`, one /me fetch per session; falls back to the person symbol). `iOSApp.swift` is a thin entry (deep-link handlers unchanged).
- **Kotlin** (`sharedUI/src/iosMain/.../shell/`): `IosShellBridge` (Koin bootstrap, auth observation for Swift, the App.kt-equivalent session side-effects on auth flips, tab telemetry, splash duration); `TabRoots.kt` (three per-tab `ComposeUIViewController`s, each with its own NavHost — per-tab back stacks live naturally in per-VC state; 150ms fades; $screen telemetry via the shared `currentScreenName`); `AuthFlowRoot.kt` (the signed-out flow); `SplashHost.kt`.
- **Behavior parity rules:** the native tab bar hides on pushed (non-tab) destinations via per-tab `onRootChanged` callbacks (matches the old Compose bar; deliberately diverges from the iOS 26 minimize-don't-remove HIG direction — revisit if wanted). Content flows edge-to-edge UNDER the floating glass bar; the three tab-root scrollables add `LocalFloatingBarInset` (ui/FloatingBarInset.kt, 0 on Android) to their bottom contentPadding so last items scroll clear. Session boundaries: `IosShellBridge.clearSessionViewModelStores()` deterministically clears every shell controller's explicit ViewModelStore (`ShellSessionStores` in TabRoots.kt — stores are created OUTSIDE composition so retention never depends on CMP scene internals; empirically, tab compositions PERSIST across TabView switches), then Swift rebuilds fresh controllers; the bridge watcher runs `AppSessionController.onSessionEnded()`. AuthFlowRoot renders inert Stone once `isAuthenticated` flips (kills a one-frame Auth flash + spurious $screen during the signup→shell swap). HomeTabRoot warm-loads ProfileViewModel so PostHog identify fires at session start (pre-shell MainScaffold parity). **$screen for tab ROOTS is bridge-driven on iOS** (empirically verified: tab compositions persist across switches, so LaunchedEffects don't re-run): `IosShellBridge.tabRootShown` emits on every real tab switch, Home emits its own initial root at cold start (`emitInitialRootScreen`), Schedule/Profile skip their initial composition emission to avoid doubles, pushed-destination screens still emit from the per-tab NavHost effect, and same-tab re-taps emit `tab_tapped` only — all matching the pre-shell event stream exactly (verified via Debug-build telemetry echo). The app locks `UIUserInterfaceStyle=Light` (light-only design; Moss tint on a dark glass bar would fail contrast) and the launch screen uses the `LaunchBackground` colorset (MossDeep) so cold start has no white flash before the splash.
- **KEEP IN SYNC:** `AuthFlowRoot.kt` mirrors App.kt's unauthenticated branches (auth / password reset / survey / claim, incl. the synchronous welcome-token seeding). Any change to that flow in App.kt must be mirrored there, and vice versa.
- **App icon:** `iosApp/iosApp/AppIcon.icon` is a hand-authored Icon Composer bundle (solid Moss fill + the vectorized dot-matrix `a` as a glass layer; exact SVG generated from the Drive source art). iOS 26 renders the layered Liquid Glass icon; iOS 18.x uses the legacy `AppIcon.appiconset` PNGs, whose dark/tinted appearance slots are now filled (`app-icon-1024-dark/tinted.png`).

## Design system

The brand-aligned theming lives in :sharedUI at `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/` and the reusable UI primitives in `.../ui/`. (Note :sharedLogic also has an `org.arcana.mobile.ui` package for the pure `studioLocationLabel` helper.) **Screens should never hand-roll `TextStyle`s, hex colors, or raw icon paths** — compose them from these primitives. The brand color hexes and the typography hierarchy are sourced from the brand identity doc and the typography doc respectively (see the parent `arcana/CLAUDE.md` for live links).

**`theme/AppColors.kt`** — five primaries are the brand doc's source of truth: `Lime #B6C24F`, `Moss #283B15`, `Stone #F5F2ED`, `Wood #2E1B0F`, `BurntNectar #F65713`. Derived variants (`LimeBright/Deep`, `MossDeep/Light`, `Stone2`, `Paper`) are HSL-style shifts of those primaries — recompute them, don't hand-edit, if a primary changes. `Ink/Graphite/Charcoal/Ash/Ash2/Mist/Mist2` are the warm neutrals. `StoneAlpha*` are translucent helpers for dark surfaces.

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
- **`ErrorState.kt`** — `FullScreenError`, `InlineError`, `ErrorSnackbar`, and the `ErrorCopy` strings behind them, all keyed off `ErrorType`. **Never hand-roll an error message or a retry control** — see "Error states" below.

## Error states — use the shared system

Every member-facing failure resolves to one of two categories, and the app must
say which. Conflating them is the production bug this system exists to prevent:
a member was told the server had failed while the server was healthy and their
connection had blipped.

- **`ErrorType.CONNECTION`** — no valid answer from the server (offline, flaky,
  timeout, DNS). Copy must **never** say "server error".
- **`ErrorType.SERVER`** — the server answered badly. Copy owns the fault.

**Three rules, all cheap to follow and all expensive to miss:**

1. **A ViewModel's `Error` state carries an `ErrorType`**, not a pre-baked
   message. Derive it with `Throwable.toErrorType()` (:sharedLogic
   `networking/ErrorType.kt`). Screens pass that type to the shared component and
   never build their own copy.
2. **Pick the component by how much of the screen the failure invalidates:**
   `FullScreenError` when there is nothing to show, `InlineError` when a section
   failed but the surrounding screen is still good, `ErrorSnackbar` when cached
   content is still on screen and must survive. A failed refresh must never wipe
   good content.
3. **Every new *read* endpoint on `ArcanaApiClient` ends in `bodyOrThrow()`, not
   `body()`.** This one is not optional and its failure is silent: the client runs
   `expectSuccess = false`, so a non-2xx never throws, and because most DTO fields
   are defaulted a 5xx body deserializes into a valid empty object that the app
   then reports as **success**. Write/auth endpoints are the exception — they
   inspect `response.status` themselves and may call `body()` inside a verified
   2xx branch.

For a submit flow, map the caught failure with `Throwable.transportFailureCode()`
and render `transportErrorCopy(code)`, falling back to your own copy only for
reason codes the server named. A typed server reason always wins over both.

Copy lives only in `ErrorCopy` / `TransportErrorCopy.kt`. No em or en dashes in
anything a member reads.

**Verify a change here on a device, not just in tests.** Two real bugs in this
area (every non-2xx collapsing into one generic booking/concierge code) passed
unit tests and code review and were only caught by driving a real 5xx.
`docs/regression/error-states-qa.md` lists every state with the exact command
that forces it, and `tools/regression/error-state-harness.sh` provides the levers
(`kill-server`, `db-down`, `stall`, `wifi-off`). Run `preflight` first — the app
defaults to **prod**, and a storage clear silently resets it there.

## Surface conventions

The app is **Stone-primary (light)** with two intentional dark counterweights: the splash sits on **MossDeep `#1F2D10`** (the only screen that does), and the Profile hero sits on **Ink**. Burnt Nectar is reserved as a sparing accent — do not introduce it as a primary surface.

**Splash** (`SplashScreen.kt` + `ui/DancingWordmark.kt`): full-screen Compose Canvas rendering a grid of stone-colored dots that flicker, settle into the Arcana wordmark from `ui/WordmarkGridData.kt` (embedded const, no I/O), then breath-pulse. Sits on MossDeep with a radial vignette fading to Ink at the corners. Timing is derived from `DANCE_DURATION_MS` + `DANCE_SETTLE_STAGGER_MS`; the splash min-display constant in `SplashScreen.kt` adds a 200 ms tail so the breath pulse lands before the 300 ms exit fade in `App.kt`. The canonical reference grid JSON is in `composeResources/files/wordmark-grid.json`; if it ever changes, regenerate the const in `WordmarkGridData.kt` from the file.

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

## Vertical centring — two traps that keep shipping

Both of these produce UI that is arithmetically centred and visibly wrong. They
have each been caught in review more than once, so treat them as defaults, not
edge cases.

**1. All-caps labels in filled controls sit high and left.** Compose centres a
Text's *layout* box (ascent..descent), but all-caps ink only occupies
cap-height..baseline, so the empty descent space pushes the glyphs up. Letter
spacing is also applied after the final glyph, pushing them left.

> Every centred ALL-CAPS label inside a pill/button gets
> `Modifier.opticallyCentredCaps(fontSize, letterSpacingEm)` from
> `ui/OpticalCentering.kt`. Without it the label lands about a point high and
> left. Do NOT apply it to sentence-case body text.

Verify rather than eyeball, on a screenshot of the control:
`tools/regression/measure_centering.py <shot.png> <fill_hex> 3 <x0 y0 x1 y1>` —
under 0.5pt on both axes passes. The crop box is required; the brand fill also
appears in the tab bar and wordmark, and an unconstrained search unions them and
reports a meaningless 0.0px.

**2. Stacking a TopBar above centred content shifts it down.** A
`Column { TopBar(); Thing(Modifier.weight(1f)) }` centres `Thing` in the space
*below* the bar, so it sits lower than the same component on a screen without
one. Consuming the top inset before the content measures does the same thing
(`windowInsetsPadding` consumes, so a parent's `safeContentPadding()` moves the
centre down by half the status bar).

> Overlay the bar in a `Box` instead, and apply safe-area padding to the BAR,
> not to the container that wraps the centred content. `ClassDetailScreen`'s
> `ErrorBlock` and its loading state are the worked examples; both were wrong in
> exactly this way and are what the rule came from.

## Spacing & sizing

Pad in **4dp increments** (`4 / 8 / 12 / 16 / 20 / 24 / 28 / 32 / 40 / 48`). Text sizes in **2sp increments** (`10 / 12 / 14 / 16 / 18 / 20 / 22 / 26 / 28 / 36 / 44 / 52 / 56`). The few intentional exceptions — `1.dp` hairlines, the `116.dp` avatar diameter — should stay rare and obvious.

## Navigation

`App.kt` hosts a `NavHost` keyed off `navigation/ArcanaDestinations.kt` — a sealed `ArcanaDestination` with `@Serializable` data objects per destination (no string routes). The bottom bar's active tab is derived from `currentBackStackEntryAsState` via `hasRoute<T>()`; it hides on every non-tab destination (StudioSelection, MyBookings, EditProfile, ConciergeRequest, ClassDetail) so a stray tab tap mid-flow can't silently pop the in-progress entry off the stack.

Tab navigation uses the standard `popUpTo(start) { saveState = true }` / `launchSingleTop` / `restoreState` block so each tab keeps its own back stack + scroll position.

Transitions are pinned at the `NavHost` level (150ms fade, all four slots) for cross-platform consistency — iOS's default slide reads wrong between sibling tabs. Override per-`composable` if a destination needs its own transition.

**Dependency pinning.** `org.jetbrains.androidx.navigation:navigation-compose` is the JetBrains CMP port, not Jetpack Navigation. Its version must match the `composeMultiplatform` release — find the matched version in the CMP CHANGELOG, not in Jetpack/Android docs. Bumping CMP almost always means bumping nav-compose; bumping nav-compose alone risks pulling a conflicting Compose runtime transitive.

## Temporary debug treatment

These are pre-launch dev affordances that must be removed (or hardened) before public release. Each is tagged inline with a comment pointing back here for findability.

**1. Runtime API base URL override + Developer Settings screen.**
- Files (straddling the split): `networking/BaseUrlProvider.kt` + `settings/DeveloperSettingsViewModel.kt` in :sharedLogic; `settings/DeveloperSettingsScreen.kt` + the hidden entry gesture in `auth/AuthScreen.kt` (10 taps on the wordmark) in :sharedUI.
- Why it exists: pre-launch we run the server on Cole's Mac and expose it to physical devices via a Cloudflare *quick* tunnel. Quick-tunnel URLs change on every `cloudflared` restart, so rebuilding the app each time would be miserable. The override is editable at runtime via the Developer Settings overlay (reachable from the auth screen footer — so testers locked out of login because the default doesn't reach the server can fix it without first authenticating). The override persists in `SecureStorage` (Keychain on iOS, EncryptedSharedPreferences on Android).
- Default fallback: **`https://api.arcana.fit` on both platforms** — the prod cutover is already done (see `defaultBaseUrl()` in `Platform.android.kt` / `Platform.ios.kt`), so a fresh install (debug included) reaches prod with no setup. It is NOT a localhost default. To run any build against a local server you MUST set an override in Developer Settings: `http://localhost:8000` (iOS simulator), `http://10.0.2.2:8000` (Android emulator host-loopback alias), or a Cloudflare quick-tunnel URL (physical device). Debug builds permit cleartext to `localhost` / `10.0.2.2` for exactly that.

**2. Server-side `*.trycloudflare.com` allowlist.**
- File: `arcana-server/arcana/settings/dev.py` — `ALLOWED_HOSTS` includes `.trycloudflare.com` (wildcard) and a placeholder `api.arcana.fit`. Also sets `SECURE_PROXY_SSL_HEADER` because Cloudflare terminates TLS at the edge.
- Why it exists: paired with #1. Without the allowlist Django 400s every request through the tunnel (unknown Host header).

**Cutover checklist when prod ships (probably alongside Phase 4 / Stripe or Phase 5 / booking):**

- [x] Flip the platform `defaultBaseUrl()` actuals to point at the prod hostname. **DONE** — both `Platform.android.kt` and `Platform.ios.kt` return `https://api.arcana.fit`. A fresh install reaches prod with no override; local dev now requires an explicit Developer Settings override.
- [ ] Decide on the future of the Developer Settings screen: either remove the auth-screen link entirely, gate the entire feature on a debug build flavor, or keep it as a "support" affordance for troubleshooting. The screen itself is self-contained — removing the AuthScreen entry point is sufficient to hide it from users.
- [ ] Remove the `BaseUrlProvider` if the runtime override is no longer needed (also drop the `defaultUrl` constructor param and revert `ArcanaApiClient` to a static base URL).
- [ ] In `arcana-server`, tighten `dev.py`'s `ALLOWED_HOSTS` (remove the `.trycloudflare.com` wildcard) once prod uses `prod.py` and tunnels aren't used for active dev.
- [ ] Make sure any in-flight Developer Settings overrides on testers' devices are reset, or accept that they're sticky and document the reset path.

## Open items

- **Live data wiring.** Home + Profile are now real-data-backed (Phase 5 — `HomeViewModel`/`ProfileViewModel` read `/memberships/me` + `/bookings/me/`). The Profile **"Your favorites"** section is real-data-backed via `FavoritesRepository` (whole-studio favorites first, then location-grain rows) with a Manage link into StudioSelection. Schedule + ClassDetail have been real-data-backed since Phase 3.
- **Time-based background refetch on Schedule.** Pull-to-refresh and resume-refresh both shipped (`PullToRefreshBox` → `ScheduleViewModel.refresh()`; `LifecycleResumeEffect` refreshes booked-pills on every return). Remaining: an optional full refetch when returning from background after N minutes (only bookings refresh on resume today).
- **Network image loader.** `ClassDetailScreen` currently renders a studio-color-tinted Box as the class hero placeholder because no Compose Multiplatform network-image library is configured. When adopting Coil-MP or Kamel, swap the placeholder for `AsyncImage` reading `session.template.heroImageUrl`.

## Schedule + Detail screens

`ScheduleScreen` is real-data-backed since Phase 3 and **cursor-paged since Phase 2 of schedule-loading**. `ScheduleViewModel.init` loads favorites (defaulting the scope toggle to Favorites when the member has any), then fetches the chip-rail overview (`ScheduleApi.fetchOverview`) plus page 1 of the selected day (`fetchSessionsPage`) in parallel. Day-chip taps fetch that day's first page from the server (per-day `DayState` caches are kept only for days visited under the current filter set); infinite scroll paginates via the keyset cursor (`DayState.nextCursor` / `loadMore`, with generation counters discarding stale in-flight pages). Since Phase 2 EVERY filter (scope, studio/location picks, time-of-day, modality categories) narrows **server-side** through a debounced (250ms) refetch pipeline — nothing re-buckets or refilters client-side. `WINDOW_DAYS = 15` now only defines the day-chip range. Pull-to-refresh re-fetches the overview + selected day's page 1; booked-status pills refresh independently on every resume. The capacity overline on each row shows a four-tier coarse label (`AVAILABLE / FILLING UP / ALMOST FULL / FULL`) instead of a precise number — this hides cross-screen capacity inconsistencies that would otherwise appear because the Schedule list and the Detail screen update independently.

`ClassDetailScreen` (Phase 3.5) is reached by tapping a class row on Schedule. `ClassDetailViewModel` takes the session id as a Koin parameter (`koinViewModel { parametersOf(id) }`) and fetches `GET /api/v1/classes/<id>/`. The server refreshes from the upstream platform if its cached row is > 30s old, so capacity numbers on the detail screen are always near-real-time — hence we DO show precise spot counts here. Cancelled sessions render a cancellation notice in place of the capacity block.

The DTOs in `data/ScheduleDto.kt` are shared between list and detail responses; detail-only fields (`template.description`, `template.layout_metadata`, `location.address`/`latitude`/`longitude`) have default values so list responses still deserialize cleanly.

## Booking flow (Phase 5)

Member booking UI built against the locked `arcana-server` `/api/v1/bookings/` + `/memberships/me` contract. Spec: `docs/superpowers/specs/2026-06-02-phase-5-booking-fulfillment-design.md` §11; plan: `docs/superpowers/plans/2026-06-02-phase-5-booking-mobile.md`.

**Data + API.** DTOs in `data/BookingDto.kt` (`BookingDto`, `SpotDto`, `SessionBriefDto` incl. `instructor`, `MyBookingsDto`, `CreateBooking*`, `CancelBookingResponse`) and `data/MembershipDto.kt` (`MembershipMeDto`). `ScheduleSessionDto` gained `spots: List<SpotDto>` (detail-only). `ArcanaApiClient` implements two **narrow interfaces** — `networking/BookingApi.kt` (`createBooking`/`myBookings`/`cancelBooking`) + `MembershipApi` (`membershipMe`) — so ViewModels depend on the interfaces and are faked in `commonTest`. `createBooking` inspects `response.status` manually (the client has `expectSuccess=false`, so 4xx does NOT throw) and surfaces the server `{error: code}` as `BookingError(code)`.

**Booking from class detail.** `schedule/ClassDetailScreen.kt`'s `StickyReserveCta` is driven by `booking/BookingViewModel.kt`: it reads `/me` (credits) + `/bookings/me/` (to detect an existing booking for this session), derives the CTA via the pure `bookCtaState(...)` → `BookCta` (`Bookable`/`Full`/`OutOfCredits`/`AlreadyBooked`/`NotBookable`), opens the `booking/BookingSheet.kt` confirmation sheet (Material3 `ModalBottomSheet`; `booking/SpotPicker.kt` for spot studios), submits, and maps errors via `bookingErrorCopy(code)`. The CTA reflects real status: `REQUESTED ✓` (just booked) / `REQUESTED` / `CONFIRMED ✓` on return, and `CLASS ENDED` (no availability, no-op) for past classes.

**My Bookings + Home + Profile.** `booking/MyBookingsScreen.kt` (`MyBookingsViewModel`) — a `MyBookings` non-tab destination reached from Home's "See all"; rows show date/time + instructor + `ui/StatusPill`, tap opens the class detail, X closes; cancel uses `cancel_policy.will_forfeit_credit` to warn before a forfeiting cancel. `home/HomeViewModel` + `profile/ProfileViewModel` read `/me` (+ `/bookings/me/`) and render real greeting/credits/streak/upcoming and member card; both shimmer while loading via `ui/Shimmer.kt`. `ui/StatusPill` mirrors the server ops-console pill tones (good/warn/bad).

**Auth: sign-in only.** There is no in-app sign-up — `auth/AuthScreen.kt` is login-only; members onboard via the invite welcome flow (welcome deep link → onboarding survey → `SignupCompletionScreen`). `ArcanaApiClient.login/logout/completeSignup` call `clearBearerTokenCache()` (clears the Ktor `Auth` plugin's in-memory bearer token via `client.authProviders…clearToken()`) so requests use the current user's token after a re-login — without it, the plugin keeps sending the previous user's cached token.

**Onboarding survey (August cohort+).** New members answer a 13-question survey between the welcome deep link and claim-your-name: `signup/SignupSurveyScreen.kt` + `SignupSurveyViewModel.kt`, questions in `signup/SignupSurveyQuestions.kt` — a 1:1 port of arcana-web `app/beta/survey.ts` (ids/options/order must stay in lockstep; the server's Google-Sheet mirror depends on it). Submits to `POST /api/v1/beta/signup-survey` (token-gated, unauthenticated — token is validated, never consumed) via the `SignupSurveyCallable` seam. Completion (or the post-failure "Continue anyway" skip — the survey must never block a paid member's signup) is persisted in `SecureStorage` keyed `signup_survey_done:<token>` via `AppSessionController.markSurveyDone/isSurveyDone` (App.kt collects the gate and advances), so re-tapping the email link goes straight to claim-your-name. Telemetry: `signup_survey_started/submitted/failed/skipped` + `$screen` `SignupSurvey`; taxonomy locked by `SignupSurveyTelemetryTest`.

## Telemetry (PostHog analytics + Sentry observability)

Product analytics (PostHog) and crash/nonfatal reporting (Sentry) for both platforms. **All instrumentation goes through one shared, type-safe layer — never call PostHog/Sentry SDKs directly from feature code.**

**Architecture.** `analytics/` in :sharedLogic's commonMain defines two interfaces — `Analytics` (PostHog) and `CrashReporter` (Sentry) — and the **`Telemetry` facade** that owns the *entire* event taxonomy (one typed method per event; event-name strings live only in `Telemetry.Events`). Feature code injects `Telemetry` (Koin `single`) and calls `telemetry.bookingSucceeded(...)`, `telemetry.screen(...)`, etc. Per-platform impls supply the interfaces:
- **Android** (:sharedUI's `androidMain/analytics/`): `PostHogAnalytics` + `SentryCrashReporter` over `posthog-android` + `sentry-android`; initialized in `ArcanaApplication.onCreate()` via `androidTelemetryModule(context)` (returns a Koin module binding the interfaces).
- **iOS** (`iosApp/iosApp/Analytics/` — Swift): `SwiftAnalytics` + `SwiftCrashReporter` over the PostHog + Sentry **Swift SDKs (SPM)**; `TelemetryBootstrap.start()` inits them in `iOSApp.swift` and the instances are passed into Kotlin via `MainViewController(analytics:crashReporter:)`, then registered into Koin. Mirrors the existing deep-link bridge.
- If a platform supplies no impl (blank key, or the analytics gate below says no), the binding falls back to `NoopAnalytics`/`NoopCrashReporter` and the app runs with telemetry disabled.

**Adding/changing instrumentation (the rule):** add a typed method to `Telemetry` (+ its `Events`/`Screens` constant) and call it from the relevant ViewModel/screen — do **not** scatter raw `capture("...")` strings. ViewModels take `telemetry: Telemetry = Telemetry.Noop` (the default keeps tests/previews and the existing `commonTest` fakes compiling). Screen views fire from one `LaunchedEffect` keyed on the resolved screen name in `App.kt`'s `MainScaffold` (+ `Auth`/`Signup` in `App`). `identify` is called from `ProfileViewModel` on the first `/me` and is **deduped per session inside `Telemetry`** (cleared on `reset()`); don't re-add per-VM identify guards. Forced-vs-manual logout is distinguished in `ArcanaApiClient` (`forceLogout(cause)` vs `logout()`). Every event also carries a `platform` super property (`android`/`ios`).

**Keys/config (client-safe, gitignored — never commit).** Android: `sharedUI/analytics.properties` → `BuildConfig` (`build.gradle.kts` `analyticsProp(...)`). iOS: `iosApp/Configuration/Secrets.xcconfig` (optionally `#include?`'d by `Config.xcconfig`, referenced from `Info.plist` via `$(VAR)`). **xcconfig URL gotcha:** `//` starts a comment, so URLs/DSNs use a `SLASH = /` var (`https:${SLASH}${SLASH}host`) — never the `$()` trick (it silently truncates the host). A blank key/DSN disables that SDK. CI supplies values via `-P`/env vars instead.

**Dev behavior (debug builds only).** Every `Telemetry` call echoes to logcat / Xcode console via `logDebug` under a `▶ Telemetry` tag (gated on `isDebugBuild` in `Platform.kt`) — watch with `adb logcat -s Telemetry:D` or the Xcode console filtered on `D/Telemetry`. **PostHog itself is not initialized in a debug build** (see the gate below), so the console echo is the only way to watch events while developing, and the regression suite depends on it. Session replay is **on, fully masked** (text inputs + images) wherever PostHog does run. The PostHog dashboard is "Beta — App Health & Usage (Mobile)" (project 439926, US).

**Sentry specifics.** Sentry's **auto-init is disabled** in `AndroidManifest.xml` (`io.sentry.auto-init=false`) because its ContentProvider crashes on startup without a manifest DSN — we init manually, which is the single source. iOS dSYM upload is a Run Script phase (`iosApp/scripts/sentry-upload-dsyms.sh` + gitignored `.sentryclirc`). **iOS crash caveats:** crashes only capture when run **without the Xcode debugger attached** and upload on the next **cold launch**; native/Swift crashes symbolicate via dSYMs, but **uncaught Kotlin/Native exceptions surface as SIGABRT with imperfect stacks** — breadcrumbs (one per telemetry event) carry the action trail. Nonfatals and crashes are distinguished in Sentry by the `handled` flag, not by issue grouping.

**Why not `sentry-kotlin-multiplatform`:** deliberately deferred. It would only improve iOS *Kotlin-uncaught-crash* stacks (a narrow gain — Android JVM stacks and iOS native stacks are already good), and it's still 0.x, unverified on Kotlin 2.3.0, and needs finicky SPM↔Kotlin/Native linking against our static-framework setup. The `CrashReporter` interface is the seam, so swapping later is localized — revisit when the SDK hits 1.0 / confirms our toolchain, or when an iOS Kotlin crash is genuinely hard to debug.

**Verifying.** `commonTest/.../analytics/` has `FakeAnalytics`-backed regression tests that lock the taxonomy — add one when you add an event. Manual QA walkthrough: `docs/analytics-qa-checklist.md`. As always, compile **both** targets after touching `commonMain`.

### Performance & latency instrumentation

A perf-observability layer sits on top of the telemetry facade, feeding the **"Mobile Performance & Latency"** PostHog dashboard (project 439926, id 1849473). Spec/plan: `docs/superpowers/specs|plans/2026-07-14-mobile-performance-observability*`.

**Events:**
- **`api_request`** — emitted for EVERY HTTP call by `networking/PerfTimingPlugin.kt` (a Ktor client plugin on `ArcanaApiClient`), no per-call-site code. Carries `total_ms` (client round-trip), `server_ms` (from the `X-Arcana-Server-Ms` response header that arcana-server's `ServerTimingMiddleware` stamps), derived `network_ms = total − server`, `endpoint`, `outcome`, `status_code`, `response_bytes`.
- **`app_start_completed`** — cold-start → first Home frame, via `analytics/AppStartTracker.kt` (`markStart()` at the platform entry points, `onFirstContent()` in `App.kt`). Fires once per process (`start_type=cold`; warm deferred).
- **`screen_load_completed`** (`source` = cold_start/tab_switch/refresh/day_switch/filter) + **`schedule_page_loaded`** — fired from `ScheduleViewModel`. Reuses existing `class_viewed.load_ms` for class-detail latency.

**Maintenance rules (so this stays sustainable):**
- **New API endpoint** → add a `when` case to `analytics/ApiRequestMetrics.kt` `normalizeEndpoint(method, path)`, else it buckets as `other` on the dashboard (safe, but not individually visible). The FULL map is locked by `ApiRequestMetricsTest` — a rename fails the build. `other` is an intentional bounded fallback (auto-derived names would blow up cardinality on slug/uuid paths).
- **New journey/timing event** → typed method on `Telemetry` + `Events` constant + a `PerformanceTelemetryTest`/`ScheduleTelemetryTest` assertion (same rule as all telemetry).
- **New PostHog super-property** → register it AND **re-register it after `reset()`** in BOTH `PostHogAnalytics.kt` and `SwiftAnalytics.swift` — `reset()` (fired on logout) clears super-properties, so anything not re-registered is lost until the next cold start. `platform` and `environment` already do this.

**`environment` super-property** (`analytics/AppEnvironment.kt` `classifyEnvironment`): `prod` (`api.arcana.fit`) / `local` (localhost/127.0.0.1/10.0.2.2) / `tunnel` (`*.trycloudflare.com`) / `other`, derived from the resolved base-URL host. Owned by `BaseUrlProvider` (set at init + on every override change). **It is a tripwire, not a control:** dev traffic is not sent at all (see the analytics gate below), so anything arriving tagged non-prod means the gate has a hole.

**Analytics gate (`analytics/TelemetryGate.kt`).** PostHog initializes **only when `!isDebugBuild && environment == prod`** — everything in the project is production data, and dev traffic is never sent rather than sent and filtered later. The environment is resolved from the persisted Developer Settings override *before* Koin starts (telemetry bootstraps first on both platforms), so a release build pointed at localhost stays silent too. No dev toggle: verify end to end with a TestFlight/release build against prod. **Sentry is deliberately exempt** and reports from every build and base URL (dev and regression crashes are wanted); it only carries the classification as its native `environment` option (`prod`, `local-debug`, …), which is what new-issue alert rules scope on. Before this gate a 2026-08-11 regression run put 3,030 local events and 59 recordings into the prod project.

**iOS boolean gotcha:** a Kotlin `Boolean` in an event-property map boxes to `KotlinBoolean` crossing into Swift and is silently dropped by PostHog's serializer, so `SwiftAnalytics.bridge()` coerces it to a native `Bool`. Any new impl of `Analytics` on iOS must keep that coercion or boolean properties vanish on iOS only.
