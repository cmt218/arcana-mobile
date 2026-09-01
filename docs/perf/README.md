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

Residual, unchanged across all three CMP versions: ~100ms severe stalls (30 → 29 on
1.12.0) consistent with pagination page-appends deserializing on the Main dispatcher
(ScheduleViewModel loads run in viewModelScope with no `withContext(Dispatchers.Default)`).
Prefetch did not absorb them. Moving parse off Main is the open candidate fix.

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
