# Scroll-performance A/B runbook (Schedule screen)

Measures dropped frames / frame-time percentiles while scripted-scrolling the Schedule
list on a heavy day, comparing two builds (e.g. before/after a Compose Multiplatform
upgrade). First used 2026-08-10 for the CMP 1.10.0 → 1.11.1 bump, then 2026-09-01 for
CMP 1.11.1 → 1.12.0. Re-run it for any change aimed at scroll cost.

Background & the full investigation this came from:
`../../../docs/2026-08-09-swift-migration-feasibility.md` (arcana repo root docs/).

## Results so far (for comparison)

Simulator = iPhone 17 Pro Max, iOS 26.3, 60Hz, Release builds, local server.

**2026-08-10 — CMP 1.10.0 → 1.11.1.** One protocol run per build (3 rounds ×
(8 flicks + 4 slow drags)), 1,006 sessions on the day's list.

| Metric | CMP 1.10.0 flick | CMP 1.11.1 flick | 1.10.0 slow | 1.11.1 slow |
|---|---|---|---|---|
| Hitch events (>1.5× budget) | 21 | 8 | 7 | 3 |
| Dropped frame slots | 1.91% | 1.14% | 1.09% | 0.65% |
| Total hitch time | 672ms | 401ms | 259ms | 167ms |
| p99 frame time | 25.1ms | 16.7ms (nominal) | 16.7ms | 16.7ms |

**2026-09-01 — CMP 1.11.1 → 1.12.0. No difference.** Three cold-launch protocol runs
per build pooled (vs one above), on a full 1,077-session day. Both arms re-measured the
same evening; the 1.11.1 numbers here are NOT comparable to the 2026-08-10 column above
(different day, list and round count) — compare only within a block.

| Metric | CMP 1.11.1 flick | CMP 1.12.0 flick | 1.11.1 slow | 1.12.0 slow |
|---|---|---|---|---|
| Hitch events (>1.5× budget) | 37 | 34 | 8 | 10 |
| Severe events (>2.5× budget) | 30 | 29 | 6 | 7 |
| Dropped frame slots | 1.93% | 1.90% | 0.65% | 0.82% |
| Total hitch time | 2038ms | 1980ms | 430ms | 552ms |
| p99 frame time | 16.7ms | 16.7ms | 16.7ms | 16.7ms |
| Worst frame | 120.3ms | 117.0ms | 110.2ms | 121.1ms |
| Frames sampled | 6,224 | 6,232 | 4,038 | 4,044 |

Both gaps are inside round-to-round spread (exact round-level permutation test:
flick p=0.90, slow p=0.70; 1.11.1 flick rounds ran 1.72/2.35/1.72%, 1.12.0 ran
1.87/1.57/2.25%). **Sample three rounds per arm minimum** — a single run of either build
lands anywhere in that band and would have "shown" a 20% win or loss at will.

1.12.0's iOS prefetch scheduler (PR #3149) is genuinely present — the linked binary
carries `PlatformPrefetchSchedulerImpl` / `PriorityPrefetchScheduler`, where 1.11.1 has
only the `rememberDefaultPrefetchScheduler` stub — so this is a real null result, not a
dependency that failed to take effect.

**2026-09-01 — the page-boundary stalls, fixed.** The residual severe stalls above were
NOT deserialization, which the CMP-1.12.0 write-up had assumed. Probing a Release build
measured, per 50-row page: JSON parse 0-3ms, row mapping 0ms, `publish()` 0-1ms — 8ms
total across every page load in a run, against 352ms of stall time at those same
boundaries. 8 of 9 stalls landed within 250ms of a page append, so the boundary was
implicated but nothing in the ViewModel was the cost.

It was `sessionTimeZone` in `ScheduleScreen`'s `byBand` bucketing. `TimeZone.of` costs
~0.16ms on Kotlin/Native and was called once per row, and `remember(sessionsForSelected)`
re-keys on every append, so each new page re-bucketed every accumulated row:

| rows | bucketing cost |
|---|---|
| 50 | 7-10ms |
| 150 | 27ms |
| 250 | 46ms |
| 350 | 55ms |

Every call resolved the same id (`zones=1` — the beta is NYC-only). Memoizing the
resolution in `ScheduleDisplayLogic` removed it:

| Metric | before | after |
|---|---|---|
| Dropped frame slots (flick) | 1.82% | **0.00%** |
| Dropped frame slots (slow) | 0.79% | **0.00%** |
| Hitch events | 44 | **0** |
| Severe events | 35 | **0** |
| Worst frame | 124.5ms | **16.7ms** |

Three cold-launch rounds per arm, ~6,200 flick + ~4,000 slow frames each; not one frame
exceeded a single vsync interval afterwards. Pagination was confirmed still running
(107 cursor fetches server-side). The lesson worth keeping: a per-row call into
kotlinx-datetime is not free on Kotlin/Native, and a `remember` keyed on a growing list
turns per-row cost into O(total) work on every append.

## Prerequisites

1. **Local server with heavy data.** The dev Postgres (docker compose in arcana-server)
   holds real synced sessions — verify today's count first:
   `.venv/bin/python manage.py shell -c "from datetime import date; from integrations.models import ClassSession; print(ClassSession.objects.filter(start_at__date=date.today()).count())"`
   Aim for several hundred+. Run `nohup .venv/bin/python manage.py runserver 0.0.0.0:8000 &`.
2. **Test member** (dev DB only — never prod, no arcana.fit emails): user
   `perf-harness@example.com` should already exist with an active `founders`
   membership + comp Payment (99 credits). To (re)set its password, mirror the snippet
   via manage.py shell:
   `U.objects.get(email='perf-harness@example.com').set_password(NEW)` (dev DB only; the
   introducing commit referenced a snippet it did not actually contain).
   If the Payment's `window_end` has passed, create a fresh comp Payment
   (`tier_at_payment` is NOT NULL — set it to the membership tier).
3. **Simulator input driver.** The Claude Code iOS Simulator MCP may refuse with a
   stale "Xcode not selected" check even when `xcode-select -p` is correct; the
   headless fallback that works: `brew install facebook/fb/idb-companion` (may need
   `brew trust facebook/fb`) + `pip install fb-idb` in a venv. On Python ≥3.12 patch
   `idb/cli/main.py`: replace `asyncio.get_event_loop()` with
   `new_event_loop()` + `set_event_loop(loop)`.
   - Coordinates are in **points** (440×956 on iPhone 17 Pro Max).
   - `idb ui describe-all --udid <UDID>` dumps the accessibility tree with frames —
     use it to locate elements instead of guessing from screenshots.
   - `idb ui text` races a subsequent tap: sleep ≥1.5s and re-verify field AXValue
     via describe-all before proceeding (a dropped ".com" cost one login attempt).

## Procedure (identical for BOTH builds — only the dependency versions differ)

1. Copy `FrameTimeRecorder.swift` (in this directory) to `iosApp/iosApp/Perf/` and add
   to `iOSApp.swift`: `init() { FrameTimeRecorder.shared.start() }`.
   The Xcode project uses filesystem-synchronized groups — no pbxproj edit needed.
   **This harness must be identical in both builds and REMOVED afterward (never ship).**
2. Build Release for the simulator (Release = Kotlin/Native release link; debug builds
   are drastically slower and useless for perf):
   `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release -destination 'platform=iOS Simulator,id=<UDID>' -derivedDataPath <separate-dir-per-build> build`
3. `xcrun simctl install <UDID> <dd>/Build/Products/Release-iphonesimulator/Arcana.app`
   and `xcrun simctl launch <UDID> org.arcana.mobile`.
4. First build only — point the app at the local server and log in:
   Developer Settings = 10 taps on the auth-screen wordmark (center ≈ point 86,122);
   the base-URL override (`http://localhost:8000`) persists in the sim Keychain, as
   does the session, so the second build boots straight in.
5. Schedule tab (≈ point 220,882), wait for today's list.
6. Run `./scroll_protocol.sh <UDID> windows-<label>.txt` (in this directory; set
   `IDB=/path/to/idb` env var if idb isn't on PATH). It does a 4-flick warm-up
   (excluded — shader caches / first pagination), then 3 rounds × (8 flicks + 4 slow
   drags), logging wall-clock windows per gesture.
7. Pull the frame log:
   `xcrun simctl get_app_container <UDID> org.arcana.mobile data` →
   `Documents/frame_log_<epoch>.csv` (newest file).
8. Analyze each window (+1.2s fling tail is added inside the aggregator we used; if
   calling `analyze_frames.py` directly, pass end-times with +1.2s):
   `python3 analyze_frames.py frames.csv <label> <t0> <t1> [...]` — or aggregate all
   flick/slow windows into the summary-table metrics.

## Interpretation rules

- **Compare relative aggregates between builds, not absolutes** — the simulator runs
  60Hz on the Mac GPU; a 120Hz ProMotion iPhone has half the frame budget, so
  simulator numbers understate device impact. Confirm feel on a physical device
  (TestFlight) before/after regardless.
- Exclude the warm-up. Expect later rounds to be cleaner (warm caches).
- **Three protocol runs per build, each from a cold launch**, pooled. The 2026-09-01
  A/B showed single runs of one build spanning 1.57–2.35% dropped frames, so a one-run
  comparison can manufacture a result in either direction. Don't over-read single windows.
- The severe-stall count (>2.5×) tracks pagination/data-layer work; the small-hitch
  count (1.5–2.5×) tracks per-row render cost.

## First keyboard focus (iOS), measured 2026-09-19

**Symptom.** The first tap on ANY text field in a process (a review's comment,
Concierge, login) hung for up to a second before the field even showed focus;
every later tap, on any screen, was instant.

**Cause.** Not Compose and not our fields. UIKit builds its whole text-input
system lazily, the first time anything becomes first responder, synchronously
on the main thread inside that tap: `UIKeyboardImpl` init, the input view set,
and a run of soft-linked framework loads (`_sl_dlopen`). Compose cannot draw
the cursor or the focused rule until `becomeFirstResponder` returns.

**Numbers.** `sample <pid> 8 1` around the tap, iPhone 17 Pro simulator, Debug
build launched without a debugger, software keyboard visible, main-thread time
under `-[UIApplication sendEvent:]` for the first field tap in a process:

| Build | First tap | Later taps |
|---|---|---|
| No prewarm | 244ms (249 and 263ms with the software keyboard suppressed) | 3ms |
| Prewarm with a plain `UITextField` | 16ms | 3ms |
| Prewarm with an empty `inputView` (what ships) | 26 to 30ms | 3ms |

Of the ~250ms: ~90ms `-[UIKeyboardImpl initWithFrame:forCustomInputView:]`,
~100ms `_sl_dlopen`. On a physical phone attached to Xcode it is several times
slower (every dylib load notifies the debugger), which is the "entire second"
seen on device; launched from the home screen it is far less, which is why
most native apps never show it.

**Fix.** `KeyboardPrewarm.run()` in `iosApp/iosApp/ArcanaShell.swift`, called
from `ShellModel.splashDidAppear()` before the splash timer starts: an
invisible `UITextField` becomes first responder and resigns in the same
run-loop turn, so the cost lands under the splash, which still runs its full
minimum. The field carries an EMPTY `inputView` (and an empty input assistant,
for iPad): UIKit still builds everything that was slow, but is never asked to
present the system keyboard, so there is nothing that could flash at launch
even in principle, and no keyboard-height notification reaches Compose. That
costs ~10ms of the first real tap against the plain form. Checked anyway: a
60fps capture of the launch, with the software keyboard enabled, shows the
keyboard region at 27 to 54 brightness for the whole splash (a keyboard reads
~220). Android has no equivalent stall.

**To re-measure.** Cold launch from the home screen (not from Xcode), open
You → Concierge, start `sample`, tap the field, read the `sendEvent` line.
`xctrace` cannot attach to a simulator process, and launching under it stalls
the app; `sample` on the host pid works. A simulator only shows its software
keyboard (needed to see a flash at all) after
`xcrun simctl spawn <udid> defaults write com.apple.keyboard.preferences AutomaticMinimizationEnabled -bool NO`
and a reboot; on iOS 26 the older `com.apple.Preferences` domain does nothing.

## Discover map (iOS), measured 2026-09-20

Debug build on the simulator (iPhone 17 Pro, iOS 26.4.1, and iPhone 16 Pro, iOS 18.5), local server, 80 locations. Absolute numbers are a Mac's; the comparisons are what carry to a phone. Tools: `ps -o cputime` over a window for CPU, `footprint -p <pid>` for memory, `heap <pid>` for live `VKMapView` engines (the 18.5 runtime's process cannot be read by `heap`), `sample <pid>` for stacks.

| Question | Result |
|---|---|
| Compose work while the map is on screen, before | 12.6% of a core at idle: the atmosphere steps 30 times a second and every step redraws the whole Compose scene over the native map, on the thread the map's gestures run on |
| Same, with `Atmosphere(drifting = false)` on the Map lens | 0.8% idle, 0.8% during a nine second drag |
| What the map costs in memory | about +50 MB while it exists (62 → 113 MB on 26.4) |
| Is it given back | yes: 68 MB within three seconds of leaving the Map lens, engine count 0 |
| Does create and destroy leak | no: three rounds of 15 Studios ↔ Map round trips in three seconds each settle at 157, 154, 151 MB |
| The catch | each round spikes to about 315 MB and takes up to 45 s to drain (malloc returns the pages lazily). Fifteen maps in three seconds is not a member, but it shows a new `MKMapView` per visit is the design's cost |
| A map left open in a hidden tab (iOS keeps tab compositions) | zero work: no MapKit or VectorKit frame on any thread in a three second sample; it holds its ~50 MB |
| Releasing it when its tab hides, via the screen's lifecycle | does NOT work: the engine was still alive 40 s after switching tabs, so that gate was removed rather than left as dead code |

**Decisions.** The atmosphere holds still on the Map lens (both platforms). The map lives only while the Map lens is on screen, plus the iOS hidden-tab case; MapKit answers memory warnings itself. Android forwards `onLowMemory` / `onTrimMemory` to Google's `MapView` and skips a marker redraw when a zoom leaves every cluster unchanged. Not built, and the next step if the rebuild after "back from a studio page" feels slow on a device: keep ONE `MKMapView` per session and re-attach it, which removes both the rebuild and the spike at the price of holding ~50 MB until the session ends or a memory warning arrives.

**Scale limits to revisit.** Android redraws every cluster mark (on and off screen) when the clusters change: fine for hundreds of locations, wasteful near a thousand, where marks should be limited to the visible region. The directory ships every location in one response (3.8 KB gzipped for 80): past a few thousand it wants a viewport query.

**A laggy pan under Xcode is mostly Xcode.** A Run from Xcode is a debug Kotlin/Native build with the debugger attached and Metal API Validation on (the scheme default; the project has no shared scheme), and MapKit draws with Metal. Judge map smoothness from the home screen (stop the Xcode session, tap the icon), with Edit Scheme → Run → Diagnostics → Metal API Validation off, or from a Release / TestFlight build.

## Splash length, measured 2026-09-21

The splash is a fixed timer: it does not wait for anything. Home composes under it
from the first frame and fetches `/memberships/me`, then `/bookings/me?scope=upcoming`,
so the splash's only job is to cover that fetch.

**How long Home's data takes after a cold start** (PostHog, 60 days of authenticated
cold starts; time from `AppStartTracker.markStart()` to the later of the first
successful `membership_me` and `my_bookings` `api_request`):

| | starts | p50 | p75 | p90 | p95 | ready when Home now appears | ready by 3.9s |
|---|---|---|---|---|---|---|---|
| iOS | 1153 | 0.82s | 1.11s | 1.90s | 3.33s | ~87% (reveal ≈ 1.7s) | 96% |
| Android | 50 | 1.89s | 2.87s | 3.26s | 3.79s | ~68% (reveal ≈ 2.5s) | 96% |

Android's clock starts at `Application.onCreate`, before the first frame (`app_start_completed`
p50 0.92s there vs 0.09s on iOS), so its numbers include process start and its splash starts
about a second later on that clock. Only 50 Android starts: treat its row as a rough guide. 91% of iOS cold
starts refresh the access token first (it lives 5 minutes), which is one of the round trips
in that wait.

**Timing.** Was a 1.3s stagger + 2.4s dance + 0.2s tail = 3.9s, plus the 0.3s fade: the
last ~1.1s of the dance is ease-out settling nobody sees, and 96% of iOS launches had
Home's data in hand long before the fade. Squeezing the dance into 1.8s read as panic,
so the lead-in changed: the wordmark now redraws row by row (`SplashWordmark`, 0.82s)
and holds 0.45s, **1.27s**, then a 0.55s eased fade, the mark holding still. iOS 18.5 simulator,
launch screen to Home: **4.35s → 1.95s** (mostly visible; fully in at ~2.1s). The old 0.3s
fade never actually played on iOS: removed from the shell's `ZStack` without a `zIndex`, the
splash animated out behind the tabs and showed as a one-frame cut. The animation's first frame lands 120 to 190 ms after the splash
timer starts (Compose scene setup after `splashDidAppear`), so the hold plays as ~0.3s.
A launch whose fetch is slower than the splash shows Home's own shimmer for the
difference (about 1 in 8 on iOS).

**Deliberately not done: refreshing an expired token up front.** Most cold starts send
their first request with an expired access token, get a 401, refresh, and replay (inside
the `api_request` timing, which is why 401s barely appear in it). Refreshing first would
save that one round trip, ~100 to 150ms at the median, but it puts a write (the refresh
POST, 30s timeout) in front of every launch's reads: with no signal, Home's connection
error would take 30s+ again instead of 10s. It also touches the refresh path that decides
forced logouts. Revisit only with a short timeout on that refresh.
