# Mobile Premium Polish — Design

**Date:** 2026-09-04
**Status:** Approved by Cole and Felicia (this doc is the written record of the decisions made 2026-09-02 to 2026-09-04)
**Repos touched:** arcana-mobile only (`:sharedUI`, plus one `:sharedLogic` change for the schedule pager)
**Reference material:** the proposal page with live prototypes (artifact `42c5fff7`), the frozen atmosphere studio (`8a705930`), Cole's path (`a5916ff1`) and Felicia's path (`aac6a80b`). Card **O** on Felicia's path, at Speed ×1 and Presence Quiet, is the atmosphere of record.

## Purpose

Raise the app from "feels good" to premium: surfaces with depth and life, one motion system, controls that answer when pressed, and a reservation that feels like the moment the product exists for. The search reveal set the bar; everything else catches up to it.

Brand constraints that bind every choice below: Moss and Stone are primary, Lime is the highlight, Burnt Nectar is reserved for errors and high-priority messages, Wood is rare. The app stays light-only. The background is the background: the brief for the atmosphere is "very subtle but noticeable, and never the main point".

## Decisions (settled, do not re-litigate)

1. **Atmosphere = "Deep centre" (Felicia's O) at Quiet presence and her ×1 speed.** Chosen by both founders on 2026-09-04. Cole's finalist J ("Moss shore") is the runner-up and is recorded on his path page. Exact values in §Atmosphere.
2. **No dark atmosphere.** The mesh treatments for dark surfaces (F, G, F2) were rejected. The You hero keeps its Ink block exactly as it is; the Stone body below it gets the atmosphere. The splash keeps MossDeep plus its existing vignette, unchanged.
3. **No grain.** Measured invisible at any premium opacity on Stone, and the site already retired it. Depth comes from the mesh's own light-and-shade and a whisper of vignette.
4. **Tab switch: instant.** Screens swap with no motion of their own. The Lime indicator dot travels the bar on a spring, the active icon bounces, and a selection haptic fires. (Lift-through, follow-the-dot and crossfade were compared and rejected.)
5. **Detail pushes present like a sheet that became a page:** rise from 12% of the screen height with a fade while the screen beneath recedes to 96% and dims; leave the same way, faster. Applies to every non-tab destination.
6. **Schedule days become a pager** the finger drags, with the day rail's selection pill gliding with the page offset, a tick haptic per boundary, skeleton rows for days not yet loaded, and a short row stagger on settle.
7. **Sheets** get an `ArcanaSheet` wrapper: Paper surface, 24 dp corners, a Mist handle, Ink scrim at 40%, and the parent screen receding behind them.
8. **Tactile controls:** a press response (scale 0.97 on a snappy spring, Moss fills darken to MossDeep, the Lime well kicks 2 dp) replaces the Material ripple on our own controls; two named shadows and one named shape set; every selection state animates.
9. **Floating Android tab bar** (Paper pill, hairline, shadow) so the atmosphere runs under it the way it runs under the iOS glass bar.
10. **Haptics** through one `Haptics` abstraction with eight verbs, used consistently.
11. **Slide-to-book and hold-to-cancel are a trial, not a commitment.** Build them as well as they can be built, behind a single switch, and try them on device. If they do not feel right, the switch turns them into plain taps with the press response and haptics, which still lands the confirm and reject moments.
12. **No full-screen celebration.** Decided 2026-09-04: the takeover "BOOKED." overlay from the proposal is out; a takeover is not premium. Confirmation happens in place: the slide control's well springs into a check, its label becomes BOOKED (or REQUESTED), the confirm haptic fires, the sheet closes, and the sticky CTA settles into its confirmed state with one soft Lime pulse. The dot-matrix stays the loading and splash gesture only.
13. **Presence and speed are baked into the spec values.** The studio's Presence and Speed controls do not exist in the app.

### One decision to confirm at implementation time

- **H. Surfaces on the atmosphere.** Cole noted the fully white Paper cards and pills read hard on top of the surface in the mocks. Planned: Paper at 72% alpha on cards, pills and sheets (a tint, no blur), as one token. The phase 4 report shows both treatments side by side and Cole decides then; no runtime switch is added to the code.

## Atmosphere (spec of record)

A 4×4 mesh gradient drawn by Compose's `MeshGradientPainter` (Jetpack Compose UI 1.12.0; supported in Compose Multiplatform 1.12.0 via `Modifier.paint`, Skia draws it through `drawVertices`). One composable, `Atmosphere()`, in `theme/`, replacing the unused `AmbientNectarGlow`.

**Base:** Stone `#F5F2ED`.

**Control-point colours** (row-major, top row first; these already include the Quiet presence, so use them verbatim):

| | col 0 | col 1 | col 2 | col 3 |
|---|---|---|---|---|
| row 0 | `#EEEDDC` | `#EBEBD4` | `#EBEBD4` | `#EEEDDC` |
| row 1 | `#EBEBD4` | `#C5CCA6` | `#D3D5AB` | `#EBEBD4` |
| row 2 | `#EBEBD4` | `#D3D5AB` | `#C5CCA6` | `#EBEBD4` |
| row 3 | `#EEEDDC` | `#EBEBD4` | `#EBEBD4` | `#EEEDDC` |

Derivation, so the values can be recomputed if a primary ever changes: `#EEEDDC` = Stone + Lime 10.8%; `#EBEBD4` = Stone + Lime 15.6%; `#D3D5AB` = Stone + LimeDeep 36%; `#C5CCA6` = Stone + Olive 39%, where Olive = MossLight + Lime 50% = `#7B9038`. No Paper, no Plate, no Mist, no Stone2 and no plain Moss tint anywhere: those are the whites, greys and browns both founders rejected.

**Positions:** base grid at `x = c/3`, `y = r/3`. The four interior points drift; the eight edge points slide only along their own edge at 60% of the interior amplitude; the four corners never move. For each point, with independent random seeds fixed once per composition:

```
x = xBase + A · sin(2π · t / Tx + φx)
y = yBase + A · cos(2π · t / Ty + φy)
A  = 0.15 of the screen's width (x) or height (y)
Tx = 6.0 s · s1,   Ty = 7.5 s · s2,   s1, s2 uniform in [0.8, 1.4],   φ uniform in [0, 2π)
```

No colour flow: each point keeps its colour. The green lives in the middle and breathes there, which is the "centred, random but symmetric" movement Felicia chose from C.

**Colour interpolation:** must not overshoot (overshoot draws visible ridges; that was D's "abrupt edges"). The prototype used a cubic B-spline. In Compose, start with `hasBicubicColor = false` (bilinear); if creases appear where points approach each other, switch to `true` and lower the centre contrast until the prototype screenshot is matched.

**Vignette:** a radial brush over the mesh, transparent inside 30% of the long side, `#C6CA91` at 6% alpha at the corners.

**Frame rate and gates:** `Modifier.preferredFrameRate(30f)` on the atmosphere layer (common API in CMP 1.12, wired to CADisplayLink on iOS). Drive positions from `rememberInfiniteTransition`, which honours Reduce Motion on iOS through `MotionDurationScale`. Pause when the screen is not resumed (`LifecycleResumeEffect`). Render the still composition (same colours, base positions) under Reduce Motion and Low Power Mode (iOS `ProcessInfo.isLowPowerModeEnabled`; Android `PowerManager.isPowerSaveMode`).

**Android below API 29:** `drawVertices` is only documented as hardware accelerated from 29. Until an API 26 emulator run proves the mesh, those devices get a static vertical brush (`#F5F2ED` → `#EBEBD4` → `#F5F2ED`) plus the vignette.

**Where it goes:** every Stone root. Home, Book, the You body below the Ink hero, Search, Class detail, My bookings, Studio selection, Edit profile, Concierge, and the signed-out screens (Auth, password reset, survey, claim). Developer settings stays plain. Sheets sit on top as Paper surfaces. On iOS it lives inside each tab's Compose root, under the native glass bar. Screens opt in by replacing `.background(Stone)` with the composable.

**Prototype parity check:** before wiring screens, render the composable on the iOS simulator and the Android emulator side by side with card O on Felicia's page at ×1 and Quiet, and match by eye and by a pixel sample (luminance band roughly 210 to 240, no pixel with red above green, no white).

## Motion system

`theme/Motion.kt` holds every value; nothing below is a literal at a call site.

| Token | Value | Used for |
|---|---|---|
| `Ease.Emphasized` | `CubicBezierEasing(0.2f, 0f, 0f, 1f)` | Everything entering. Already the search curve. |
| `Ease.Exit` | `CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)` | Everything leaving. |
| `Dur.Quick / Short / Medium / Long` | 120 / 200 / 340 / 480 ms | Exits / tab content / pushes and sheets / ceremonies. |
| `Springs.Snappy` | damping 0.85, stiffness Medium | Press scale, chip pops, tab indicator. |
| `Springs.Settle` | damping 0.90, stiffness MediumLow | Pager settle, sheet settle, slide thumb return. |
| `Springs.Kick` | damping 0.65, stiffness Medium | The Lime well and the confirmation check only. |

`theme/Shapes.kt`: Chip 12 dp, Card 16 dp, Hero 14 dp, Sheet 24 dp, Pill = circle. The eleven radii in use collapse to these.

Shadows (`ui/Depth.kt`): `Control` (Ink 14%, blur 18 dp, y 6 dp, plus a 1 dp inner top highlight of Stone at 10% on dark fills), `Card` (Ink 8%, blur 24 dp, y 10 dp), `Bar` (Ink 12%, blur 24 dp, y 8 dp), `Soft` (Ink 5%, blur 8 dp, y 2 dp, for Paper pills and chips).

### Navigation

- **Tabs.** Android: NavHost enter/exit for the three tab destinations are `None`. iOS: native `TabView`, unchanged. `ArcanaTabBar`: the Lime dot's horizontal position is animated with `Springs.Snappy`; the active icon scales to 1.06 and back; `Haptics.selection()` on change. The bar becomes a floating Paper pill (92% alpha, Mist hairline, `Shadow.Bar`, 12 dp side and 10 dp bottom insets, 58 dp tall); the three tab-root lists add the bottom inset through the existing `LocalFloatingBarInset`, which Android starts providing.
- **Details.** For every non-tab destination: enter = slide in from 12% of the height + fade over `Dur.Medium` on `Ease.Emphasized`; pop exit = the reverse over `Dur.Short` on `Ease.Exit`. The screen beneath: `scaleOut` to 0.96 on the same curve and back on pop; a 12% Ink dim drawn by the incoming screen behind its own surface during the transition. The Search reveal keeps its own transition.
- **Sheets.** `ArcanaSheet` wraps Material's `ModalBottomSheet`: Paper container, `Shapes.Sheet` corners, a 36×4 dp Mist handle, `scrimColor = Ink` at 40%, and the parent content receding to 0.94 with 26 dp corners while a sheet is open. If Material's spring feels wrong on device, the wrapper owns the drag with `AnchoredDraggable` and `Springs.Settle`; otherwise it stays pure styling.
- **Schedule.** `HorizontalPager` over the loaded day window; the ViewModel exposes the day list and selected index so the pager and the rail share one source of truth. The rail's Moss selection pill is one element positioned by the pager's fractional offset; the rail auto-scrolls the selected chip into view. `Haptics.tick()` per page boundary. Days not yet loaded render skeleton rows with the existing `ShimmerBox`. After a settle, the first eight rows rise 10 dp with a 16 ms stagger.

### Tactile

- `Modifier.pressable(pressed: darkenTo: Color? = null)`: scale 0.97 while pressed on `Springs.Snappy`; optional fill darken (Moss → MossDeep); `indication = null` on our own controls. Applied to `PrimaryCta`, `RetryButton`, filter pills, day chips, filter chips, `IconCircle` with `onClick`, the studio accordion, schedule rows (0.99), the `TextLink` (alpha 0.7, no scale).
- `PrimaryCta`: the Lime well translates 2 dp on release (`Springs.Kick`).
- Selection motion: `animateColorAsState` (180 ms) on every chip and pill; the day rail's selection becomes the single sliding pill; the accordion chevron rotates; filter panels use `animateContentSize`; capacity bars animate their width on first draw.
- Error snackbar and the favourites nudge get enter and exit transitions.

### Haptics

`Haptics` is an `expect object` in `:sharedUI` commonMain with UIKit feedback generators on iOS and `VibrationEffect.createPredefined` on Android 10+ (view feedback constants below that). Eight verbs:

| Verb | Fires on |
|---|---|
| `selection` | Tab change, day chip tap, spot pick |
| `tick` | Each page boundary while scrubbing days |
| `toggle(on)` | Filter chip and favourite on / off |
| `threshold` | Slide-to-book arms, pull-to-refresh triggers, sheet passes its dismiss point |
| `confirm` | Booking succeeds, profile saved |
| `reject` | Booking fails, hold-to-cancel completes |
| `boundary` | Overscroll at the end of the schedule and search results |
| `ramp` | Hold-to-cancel while holding |

### Ceremonies (trial)

Guarded by one constant, `BookingGestures.enabled`, in `:sharedUI`. On, the sticky CTA on Class detail is a slide-to-book control and cancellation is hold-to-cancel; off, both are the current taps with `pressable` and the confirm and reject haptics. Either way the booking ViewModel and every request it makes are untouched. When an accessibility service is active (TalkBack, VoiceOver) the control renders as the plain tap regardless of the switch.

- **Slide-to-book** replaces the booking sheet's CONFIRM (the sticky CTA still opens the sheet; the sheet's confirm is the commitment). The Lime well is an `AnchoredDraggable` thumb. A Lime fill follows it and the label fades. At 85% travel the control arms: `Haptics.threshold()`, the well turns Stone. Release completes the reservation; release earlier springs home on `Springs.Settle`. An idle shimmer crosses the track every 3.2 s to teach the gesture.
- **Hold-to-cancel** replaces the cancel sheet's CANCEL BOOKING. The Clay well fills a ring over 700 ms with `Haptics.ramp()`; releasing early cancels; completion fires `Haptics.reject()`.
- **Confirmation, in place.** On success the well morphs from arrow to check on `Springs.Kick`, the label reads BOOKED or REQUESTED, `Haptics.confirm()` fires, the sheet closes as it does today, and the sticky CTA shows its confirmed label with one 600 ms Lime pulse of its well. No overlay, no takeover.

## Non-goals

Dark atmosphere; grain; any tab-content motion; any full-screen celebration; shared-element row-to-hero morphs (a phase 6 candidate); headline arrival, odometer numbers, brand pull-to-refresh, overscroll boundary, search suggestions and the pill-to-field morph (phase 6 candidates, visuals first); the marketing site and the studio portal.

## Verification

- Compile both targets after every `commonMain` change; run both simulators for every visual change (`feedback_verify_on_both_platforms`).
- Atmosphere ships behind the `docs/perf` scroll protocol: flick and slow-drag recordings with it on and off on the iPhone 17 Pro simulator and on the oldest physical iPhone available; no measurable change in dropped frames is the bar. Plus an API 26 emulator run for the fallback branch.
- Every new all-caps label in a filled control uses `opticallyCentredCaps` and is checked with `tools/regression/measure_centering.py` (under 0.5 pt).
- `docs/regression/inventory.md` is updated in every phase; `tools/regression/self_audit.sh` prints `FINDINGS: 0`.
- Each phase ends with screenshots from both platforms in its report, and the tree left uncommitted until Cole says go.

## Phases (each its own branch, report and go)

0. Foundations: `Motion.kt`, `Shapes.kt`, `Depth.kt`, `Modifier.pressable`, `Haptics`; applied to `PrimaryCta` and the tab bar (dot spring, icon bounce, instant screens on Android).
1. Atmosphere: parity spike on both simulators, then the composable on every Stone root, with the perf A/B and the still fallback.
2. Navigation motion: detail push-and-recede, `ArcanaSheet`, floating Android bar, snackbar and nudge transitions.
3. Schedule pager: pager, scrubbing rail, skeletons, stagger, tick and boundary haptics.
4. Tactile pass: pressable and depth everywhere, animated selection, `animateContentSize`, capacity bars, translucent surfaces called out in the report (decision H).
5. Gestures: slide-to-book, hold-to-cancel, in-place confirmation, the switch, the accessibility fallback.
6. Candidates, each to be shown as a visual and thumbed up or down before any of them is planned: headline arrival on cold start, odometer numbers on the You hero, brand-native pull-to-refresh, overscroll boundary haptics, search suggestions under the reveal, the pill-to-field morph, shared-element row-to-hero.

The implementation plan (`docs/superpowers/plans/2026-09-04-mobile-premium-polish.md`) breaks these into tasks.
