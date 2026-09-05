# Mobile Premium Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the approved premium-polish design (`docs/superpowers/specs/2026-09-04-mobile-premium-polish-design.md`) as six independently reviewable branches: foundations, atmosphere, navigation motion, schedule pager, tactile pass, ceremonies.

**Architecture:** Every new value is a token in `theme/` (`Motion.kt`, `Shapes.kt`, `Atmosphere.kt`) or a modifier in `ui/` (`Depth.kt`, `Pressable.kt`, `Haptics.kt`); screens only compose them. The atmosphere is one composable drawn by `androidx.compose.ui.graphics.MeshGradientPainter`. Navigation motion is transition lambdas on the two existing NavHosts. The schedule pager is the only change that reaches `:sharedLogic`, and only to expose an index alongside the selected date. Booking logic is never touched: the ceremonies phase is a presentation layer over the existing `BookingViewModel`.

**Tech Stack:** Kotlin 2.3.10, Compose Multiplatform 1.12.0 (Jetpack Compose 1.12.0 underneath; `MeshGradientPainter`, `Modifier.preferredFrameRate`, `HorizontalPager`, `AnchoredDraggable` all present in the pinned artifacts), material3 1.12.0-alpha03, navigation-compose 2.10.0-alpha02, kotlin.test.

This index carries the global constraints and the phase order. Each phase has its own plan file with bite-sized tasks:

| Phase | Plan file | Branch | Depends on |
|---|---|---|---|
| 0 Foundations | `2026-09-04-mobile-premium-polish-phase0-foundations.md` | `feature/polish-0-foundations` | none |
| 1 Atmosphere | `2026-09-04-mobile-premium-polish-phase1-atmosphere.md` | `feature/polish-1-atmosphere` | 0 (frame-rate gate, tokens) |
| 2 Navigation motion | `2026-09-04-mobile-premium-polish-phase2-navigation.md` | `feature/polish-2-navigation` | 0 |
| 3 Schedule pager | `2026-09-04-mobile-premium-polish-phase3-schedule-pager.md` | `feature/polish-3-schedule-pager` | 0 |
| 4 Tactile pass | `2026-09-04-mobile-premium-polish-phase4-tactile.md` | `feature/polish-4-tactile` | 0, 1 (translucent surfaces need the atmosphere under them) |
| 5 Gestures | `2026-09-04-mobile-premium-polish-phase5-ceremonies.md` | `feature/polish-5-gestures` | 0, 2 (sheet wrapper), 4 (pressable CTA fallback) |

Phases 1, 2 and 3 are independent of each other and can be built in parallel worktrees once phase 0 is on `main`.

## Global Constraints

- **Brand colours only.** `theme/AppColors.kt` tokens, or a mix of two tokens computed in code with the mix named next to it. Never a fresh hex in a screen. Moss and Stone primary, Lime highlight, Burnt Nectar only for errors and high-priority messages, Wood rare. Light-only: no dark surfaces beyond the existing splash and the You hero.
- **Atmosphere of record = spec §Atmosphere, verbatim** (Felicia's card O at Quiet, ×1 on her page). Colours `#EEEDDC`, `#EBEBD4`, `#C5CCA6`, `#D3D5AB`, vignette `#C6CA91` at 6%, amplitude 0.15, periods 6.0 s and 7.5 s scaled by [0.8, 1.4], no colour flow, bilinear colours first. No grain. No dark mesh.
- **Motion tokens only.** Every duration, easing and spring comes from `theme/Motion.kt`; every radius from `theme/Shapes.kt`; every shadow from `ui/Depth.kt`. No literals at call sites.
- **Tabs are instant.** No screen motion on tab change on either platform. The dot, the icon bounce and the selection haptic carry it.
- **Slide-to-book and hold-to-cancel are a trial** behind `BookingGestures.enabled`. The tap path with `pressable` and haptics must be complete and correct with the switch off. Booking ViewModel and API calls are untouched.
- **Module split.** Compose-free logic and all ViewModels in `:sharedLogic`; all Compose UI in `:sharedUI`. Platform actuals under `sharedUI/src/androidMain` and `sharedUI/src/iosMain`. `:sharedLogic` must not gain a Compose dependency.
- **Compile BOTH targets after any `commonMain` change:**
  ```
  ./gradlew :sharedLogic:compileDebugKotlinAndroid :sharedUI:compileDebugKotlinAndroid
  ./gradlew :sharedLogic:compileKotlinIosSimulatorArm64 :sharedUI:compileKotlinIosSimulatorArm64
  ```
- **Tests:** `./gradlew :sharedUI:testDebugUnitTest` (UI-coupled pure helpers) and `./gradlew :sharedLogic:testDebugUnitTest` (main suite). The aggregate `test` tasks do NOT accept `--tests`. Backtick test names must not contain commas or colons.
- **No JVM-only APIs in `commonMain`:** no `String.format`, `java.*`, `Locale`. Use string templates and `padStart`.
- **Comments:** none by default; two or three lines on a non-obvious why only; no changelog narration; no annotation of imports or versions.
- **`AuthFlowRoot.kt` KEEP IN SYNC with `App.kt`.** Any change to App.kt's unauthenticated branches is mirrored in `sharedUI/src/iosMain/.../shell/AuthFlowRoot.kt` in the same task.
- **iOS shell is native.** Tab switching, the tab bar and its insets on iOS are SwiftUI (`iosApp/iosApp/ArcanaShell.swift`); Compose owns screen content. The floating bar work in phase 2 is Android only; iOS keeps `LocalFloatingBarInset` as provided by `TabRoots.kt`.
- **Accessibility.** Every new tappable icon control passes a `contentDescription`; every all-caps label in a filled box uses `opticallyCentredCaps` and is verified with `tools/regression/measure_centering.py` under 0.5 pt; gesture controls fall back to taps when an accessibility service is on.
- **Regression inventory is a hard PR requirement.** Any user-facing change updates `docs/regression/inventory.md` in the same branch; `tools/regression/self_audit.sh` prints `FINDINGS: 0`.
- **Verify on both simulators for every visual task** (`feedback_verify_on_both_platforms`): iPhone 17 Pro (iOS 26) and the Pixel 9 Pro emulator. Screenshots go in the phase report.
- **Leave every branch uncommitted.** No commit, no push, no PR until Cole says go for that branch. The superpowers template's "Commit" steps are replaced by "Verify" steps throughout these plans.

## The one open decision, as planned

Decision H (surfaces on the atmosphere): phase 4 ships cards, pills and sheets as Paper at 72% through one token, and its report shows translucent and opaque side by side so Cole decides before the PR goes up. No runtime switch. No full-screen celebration exists anywhere in these plans (decided 2026-09-04); confirmation is in place, in phase 5.

## Out of scope (decided, do not re-litigate)

- Dark mesh atmosphere (F, G, F2); grain; any full-screen celebration or overlay after a booking; lift-through, follow-the-dot or crossfade tab transitions; shared-element row-to-hero morphs; headline arrival, odometer numbers, brand-native pull-to-refresh, overscroll boundary haptics beyond the schedule and search lists, search suggestions, the pill-to-field morph (all phase 6 candidates, visuals first); the marketing site and the studio portal.
- Any change to booking, membership or fulfilment logic, the API client, or the server.

## Phase order and gates

Each phase: worktree from `main` → tasks → both-platform verification → report to Cole (what changed, files touched, how to test, production-regression read) → wait for go → PR → merge. The next phase's worktree is cut from `main` after the previous merge, except phases 1, 2 and 3, which may be cut in parallel after phase 0 merges.
