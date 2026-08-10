# Scroll-performance A/B runbook (Schedule screen)

Measures dropped frames / frame-time percentiles while scripted-scrolling the Schedule
list on a heavy day, comparing two builds (e.g. before/after a Compose Multiplatform
upgrade). First used 2026-08-10 for the CMP 1.10.0 → 1.11.1 bump; designed to be
re-run for the **CMP 1.12.0 bump when it goes stable (~Sept–Oct 2026)** — 1.12.0
contains the iOS lazy-list prefetch scheduler (compose-multiplatform-core PR #3149),
the expected fix for compose-item-cost-during-scroll jank.

Background & the full investigation this came from:
`../../../docs/2026-08-09-swift-migration-feasibility.md` (arcana repo root docs/).

## Results so far (for comparison)

Aggregates over 3 rounds × (8 fast flicks + 4 slow drags), ~3,500 frames per build per
gesture class. Simulator = iPhone 17 Pro Max, iOS 26.3, 60Hz, Release builds, local
server, 2026-08-10 (1,006 sessions).

| Metric | CMP 1.10.0 flick | CMP 1.11.1 flick | 1.10.0 slow | 1.11.1 slow |
|---|---|---|---|---|
| Hitch events (>1.5× budget) | 21 | 8 | 7 | 3 |
| Dropped frame slots | 1.91% | 1.14% | 1.09% | 0.65% |
| Total hitch time | 672ms | 401ms | 259ms | 167ms |
| p99 frame time | 25.1ms | 16.7ms (nominal) | 16.7ms | 16.7ms |

Residual on 1.11.1: occasional ~100ms severe stalls consistent with pagination
page-appends deserializing on the Main dispatcher (ScheduleViewModel loads run in
viewModelScope with no `withContext(Dispatchers.Default)`). Watch whether 1.12.0
prefetch absorbs these; if not, moving parse off Main is the candidate fix.

## Prerequisites

1. **Local server with heavy data.** The dev Postgres (docker compose in arcana-server)
   holds real synced sessions — verify today's count first:
   `.venv/bin/python manage.py shell -c "from datetime import date; from integrations.models import ClassSession; print(ClassSession.objects.filter(start_at__date=date.today()).count())"`
   Aim for several hundred+. Run `nohup .venv/bin/python manage.py runserver 0.0.0.0:8000 &`.
2. **Test member** (dev DB only — never prod, no arcana.fit emails): user
   `perf-harness@example.com` should already exist with an active `founders`
   membership + comp Payment (99 credits). To (re)set its password, mirror the snippet
   in the git history of this file's introducing commit, or:
   `U.objects.get(email='perf-harness@example.com').set_password(NEW)` via manage.py shell.
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
- One protocol run per build was used above; ~3,500 frames/class is a fair sample but
  don't over-read single-window differences.
- The severe-stall count (>2.5×) tracks pagination/data-layer work; the small-hitch
  count (1.5–2.5×) tracks per-row render cost.
