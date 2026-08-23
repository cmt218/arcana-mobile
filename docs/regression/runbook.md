# Arcana Mobile — Full Regression Runbook

Execution guide for the agent-run full regression suite: an orchestrated set
of agent shifts that drive the real app — real network calls, real
simulators/emulator, real taps — through every entry in
`docs/regression/inventory.md` (225 entries at last count) on all three
target devices, then triage what they found, fold the lessons back into these
docs, and file the surviving issues to a tracker. Invoked by the
`/full-regression` skill; this doc is what that skill follows phase by phase.
It assumes the techniques and exact binaries documented in
`docs/regression/driver-playbook.md` (the dump→act→verify loop, the
driver-bug protocol, the idb venv path) — read that doc first if you haven't.

**A run is seven phases, 0 through 6, and it is not finished until Phase 6.**
Phase 0 Preflight · Phase 1 Self-audit · Phase 2 Environment · Phase 3
Execution · Phase 4 Report · Phase 5 Triage & fold-back · Phase 6 Tracker
filing. Phases 5 and 6 are mandatory, not a nice-to-have follow-up: they are
what makes the suite self-improving (every run leaves the docs better than it
found them) and what turns a run into something a human can act on.

**Orchestration.** The checked-in reference orchestration script is
`tools/regression/full-regression.workflow.js` — it takes
`{ runDate, mode: 'sequential' | 'parallel' }` and dispatches the phase agents
described below. `tools/regression/self_audit.sh` is the mechanical
implementation of Phase 1. Both are checked in; this doc remains the contract
they implement, so on any divergence fix the script, not the procedure —
unless the run's own experience says the procedure was wrong, in which case
Phase 5.2 is where that gets written down.

**One sanctioned regrouping.** The script dispatches Phase 0 and Phase 2
together as a single Setup agent (preflight and environment are the same
machine's work and share a context), and runs Phase 1's self-audit *after*
that Setup agent rather than between them. Phase 1 touches only the mobile
repo's source tree, needs no device or server, and never halts anything, so
its position relative to the environment work is immaterial. This grouping is
deliberate and is not drift — the phase *numbering* below is the contract, not
the dispatch order of two phases that cannot affect each other.

---

## The three iron rules

1. **Never halt.** A failed check, a missing tool, a FAILed entry, an
   inventory/code drift finding — none of these stop the run. Report it,
   classify it (SKIP / BLOCKED / FAIL / a Phase 1 finding), and keep going.
   The only acceptable reason to stop before Phase 6 is being unable to
   proceed on *any* device at all (e.g. Xcode itself is missing AND adb has
   no device AND no emulator can boot) — and even then, write the partial
   report before stopping, and still run Phases 5 and 6 over whatever the run
   did produce.
2. **Outputs never enter git.** The report, screenshots, per-device results
   logs and captured manifest all live under
   `~/arcana-regression-runs/YYYY-MM-DD/`, entirely outside both
   `arcana-mobile` and `arcana-server` checkouts. Never `git add`, commit, or
   push anything as a side effect of running this suite. **Phase 5.2's
   fold-back edits are the one thing a run writes inside the repo** — and
   even those are only ever left as an *uncommitted* working-tree diff on a
   branch for Cole to review. A run never commits, never pushes, never opens
   a PR.
3. **Everything runs on all three devices.** iOS 26, iOS 18, and Android each
   get the complete inventory (minus entries their `Platforms` field
   excludes) — never a subset "for time," never skip a device because an
   earlier one had problems.

---

## Status vocabulary

Every inventory entry, on every device it applies to, ends in exactly one of:

| Status | Meaning |
|---|---|
| **PASS** | Driven end to end; observed behavior matched the entry's Expected. |
| **FAIL** | Driven end to end; observed behavior did not match Expected, and the driver-bug protocol (see driver-playbook.md) could not explain it away as a driving mistake. Reproduced via a second interaction path before being recorded. |
| **BLOCKED** | Not driven because an upstream entry it depends on is FAIL or BLOCKED (e.g. a booking-flow entry when login itself failed). Record the specific blocking entry ID. |
| **SKIP** | Not applicable on this device (`Platforms` excludes it) — expected and silent in the summary, not a finding. |

A fifth label, **suspect-driver**, is not a status but an annotation the
driver-bug protocol can attach instead of FAIL when a failure only
reproduces via one interaction path. Every suspect-driver annotation is
adjudicated in Phase 5.1 — none may survive into the finished report
unresolved.

A sixth label, **DEFERRED**, is a transient marker meaning "this entry needs
an exclusive, environment-wide fault (the shared dev Postgres, the whole DB
layer, a `manage.py shell` mutation of shared rows) and was handed to the
serialized tail phase" — never a final status. It applies in **both modes**:
in parallel mode because such a fault would land on the two lanes still
driving, and in sequential mode because batching every DB-down entry into one
tail agent gives the run a single stop/restore cycle with one verified restore
instead of several scattered through the pass.

Because the results log is append-only, the tail phase does **not** rewrite a
DEFERRED line. It **appends** a new line for the same (device, entry) carrying
the real outcome, and **the last line for a (device, entry) pair is
authoritative**. No DEFERRED status may survive into the report Phase 4
assembles.

**Cross-reference entries are a sanctioned shape, not a gap.** A few entries
exist only to point at another entry that already covers the same row, gesture
and file (today: PROFILE-12 → AUTH-11, DEVSET-01 → AUTH-13). Record such an
entry with the **referenced entry's status** and the notation `see <ID>` (e.g.
`PROFILE-12: PASS (see AUTH-11)`) — never drive it separately, and never record
it as SKIP.

---

## Run modes — sequential (default) and parallel

Pick the mode **before Phase 2**, because it changes how many servers start
and what each device's base URL points at. The orchestration script
`tools/regression/full-regression.workflow.js` takes it as an argument:
`{ runDate, mode: 'sequential' | 'parallel' }`.

**Sequential is the default and stays the default.** iOS 26 → iOS 18 →
Android, one device at a time, one server on port 8000.
Everything else in this doc is written for it. Measured wall-clock on the
2026-08-11 run: **~6.8h**. Choose it whenever the run is unattended/overnight
(the wall clock doesn't matter), whenever anything about the environment is
already shaky, or whenever you want the simplest possible failure story.

**Parallel is an explicit option, never the silent default.** The three
devices are driven as three concurrent lanes. Wall-clock is roughly the
**slowest lane, ~2.5–3h**. Choose it when a human is waiting on the result.

### Why parallel is safe here

The fixture design already isolates the devices: `seed_regression` mints a
**separate account set per device** (`accounts.ios26.*`, `accounts.ios18.*`,
`accounts.android.*`) plus one dedicated reserved session each, and Phase 3
already forbids cross-using them. Lanes therefore never contend on
account-level state (claim-once tokens, per-device booked session, credit
balance, favorites). What they *do* share — the two `regression-*` studios and
most bookable sessions — they already shared in sequential mode; the runbook's
existing rule that a later device legitimately sees an earlier device's
state-mutating effects on shared fixtures just becomes "a lane sees another
lane's effects," which is the same class of fact and is handled the same way
(note the effect on the entry's result line, per "State-mutating entries").

### Per-lane setup

Three server instances against the **same dev DB**, one per lane:

```
cd ../arcana-server && source .venv/bin/activate
python manage.py runserver 0.0.0.0:8000 &   # lane: ios26
python manage.py runserver 0.0.0.0:8001 &   # lane: ios18
python manage.py runserver 0.0.0.0:8002 &   # lane: android
```
Capture all three PIDs, individually labelled by lane. Phase 0.7's port check
applies to **each** of 8000/8001/8002.

Each device's Developer Settings override (Phase 2.5) points at **its own
lane's port**:

| Lane | Device | Base URL override |
|---|---|---|
| ios26 | iPhone 17 Pro Max (iOS 26.x) | `http://localhost:8000` |
| ios18 | iPhone 16 Pro (iOS 18.5) | `http://localhost:8001` |
| android | `Pixel_9_Pro` AVD | `http://10.0.2.2:8002` |

This one-server-per-lane split is the whole point: it gives each lane its own
kill switch. **For CONNECTION-failure entries (the ERR-0x family), a lane
kills and restarts ONLY its own `manage.py` PIDs** — the ones it captured
above, by PID, never by a port pattern and never a blanket `pkill manage.py`.
Killing another lane's server injects a fault into a run that isn't expecting
one and manufactures phantom FAILs there. Re-read the driver-playbook Safety
rule before touching any of this: a port-based `lsof -ti` kill is banned
outright (it killed the emulator once, and in the 2026-08-11 run it killed the
app under test and produced ten phantom "iOS 26 crash" FAILs).

### Coordination rules

1. **Seed exactly once, before any lane starts, and never mid-run.**
   `seed_regression` purges and rebuilds the whole `regression-*` namespace;
   a mid-run re-seed wipes every lane's bookings and invalidates every
   lane's manifest ids at once. This is Phase 2.2's existing rule with a
   sharper edge — in parallel mode a re-seed doesn't corrupt "the rest of the
   pass," it corrupts two other lanes *in flight*.
2. **Exclusive faults are serialized, not parallelized.** Anything that
   affects the whole environment rather than one lane must not run while
   other lanes are driving:
   - `docker stop arcana_postgres` (and `docker start`) — the 5xx-injection
     recipe. One DB, all three lanes.
   - Any DB-level mutation used by a fault entry: blanking the member's
     first/last name for HOME-03, the `late_cancel_active` fulfilment
     one-liner in Phase 3, and anything else driven from `manage.py shell`
     against shared rows.
   When a lane reaches such an entry it records **DEFERRED** on that entry's
   line (with the reason) and moves on.
3. **A short single-lane tail phase drives the DEFERRED set** after all
   parallel lanes have finished. It runs one device at a time, in the normal
   sequential way, and appends a fresh line carrying the real status for each
   DEFERRED entry (append-only log; last line for a (device, entry) pair
   wins). Only then is Phase 3 complete. The tail is short by construction —
   the exclusive-fault entries are a small minority of the inventory.

### Results logs

Each lane appends to its **own** log, `results-<device>.log`
(`results-ios26.log`, `results-ios18.log`, `results-android.log`) in the same
run folder, in the same one-line-per-entry format. Concurrent appends to a
single shared `results.log` interleave and corrupt each other, so never share
one. **Phase 4 merges the three logs** (plus the tail phase's appended lines)
into the report's per-item table. Resume-after-interruption works per lane
exactly as documented in Phase 3, reading that lane's own log.

**Sequential mode writes the same per-device logs** — this is not a
parallel-only convention. Phase 3's resume is a per-device ID diff, Phase 4's
merge is written once for both modes, and the deferred tail appends to the log
of the device an entry was deferred from; a single shared file would special-
case all three for no gain. What parallel mode adds is only that sharing one
file becomes actively corrupting rather than merely awkward. **`results.log`
is produced by Phase 4's merge in both modes and is never written during
Phase 3.**

---

## Phase 0 — Preflight

Read-only checks. Every check either passes or is recorded as a **SKIP with
reason** — nothing here aborts the run. If a whole device's toolchain is
unusable (e.g. no iOS 18.5 runtime at all), that device's entire Phase 3
pass becomes SKIP-with-reason in the Phase 4 summary table, and the other
two devices proceed normally.

Run each of these once, at the start of the run, and pin the real values
into the run's working notes (not into this file — this file documents the
check, not one run's results).

### 0.1 — Xcode / iOS toolchain

```
xcodebuild -version
```
Expect a version line + build number. **Verified on this Mac (2026-08-11):
Xcode 26.6, build 17F113.** If missing or `xcode-select` points at the CLT
only, SKIP all iOS work with reason "Xcode not installed/selected."

### 0.2 — iOS simulator runtimes (need both an iOS 26.x AND an iOS 18.5 runtime)

```
xcrun simctl list runtimes
```
**Verified on this Mac (2026-08-11):**
```
iOS 18.5 (18.5 - 22F77) - com.apple.CoreSimulator.SimRuntime.iOS-18-5
iOS 26.3 (26.3.1 - 23D8133) - com.apple.CoreSimulator.SimRuntime.iOS-26-3
iOS 26.4 (26.4.1 - 23E254a) - com.apple.CoreSimulator.SimRuntime.iOS-26-4
iOS 26.5 (26.5 - 23F77) - com.apple.CoreSimulator.SimRuntime.iOS-26-5
```
Both required runtimes are present. If a run ever finds no iOS 26.x runtime
or no iOS 18.5 runtime, SKIP that device's entire pass with reason "runtime
not installed" — do not attempt to install a runtime mid-run (that's a
human/CI-image decision, not something to do inside a regression pass).

### 0.3 — Pick one device per required OS line

```
xcrun simctl list devices available | head -30
```
Pick **one iPhone** on an iOS 26.x runtime and **one iPhone** on the iOS
18.5 runtime specifically (iPads are visually and interaction-wise different
enough — no bottom tab-bar safe-area geometry, different keyboard behavior —
that they are not a substitute; if the only iOS 18.5 device available is an
iPad, create an iPhone with `xcrun simctl create "iPhone 16 Pro" "iPhone 16
Pro" <iOS-18-5-runtime-id>` rather than running on the iPad).

**Verified/pinned on this Mac (2026-08-11) — both already exist as iPhones,
no `simctl create` needed:**

| Device role | Name | UDID | Runtime | State at check time |
|---|---|---|---|---|
| iOS 26 target | iPhone 17 Pro Max | `4B113C10-9463-4128-AC7B-A89759328047` | iOS 26.3 | Booted |
| iOS 18 target | iPhone 16 Pro (18.5) | `96B93E6F-2F4A-40BA-A0C5-BB6C63E00C99` | iOS 18.5 | Booted |

(These match the pair the driver-playbook's own verification pass used —
iPhone 17 Pro Max/26.3 and iPhone 16 Pro/18.5 — so the driving techniques
documented there are known-good against these exact UDIDs.) A future run
should re-run this check and update the pinned UDIDs if these simulators
have been deleted; do not hardcode UDIDs into automation, only reference
them for one run's duration.

### 0.4 — idb functional

```
~/.arcana-tools/idb-venv/bin/idb list-targets
```
Expect a non-empty target list (companion auto-spawns). **Verified
(2026-08-11): returns the full simulator list, exit 0.** `idb_companion`
resolves on `PATH` at `/opt/homebrew/bin/idb_companion`. Bare `idb` (no venv
path) is confirmed NOT on PATH on this Mac — always use the full venv path,
per driver-playbook.md. If the venv is missing entirely, SKIP iOS driving
with reason "idb venv missing" and point at driver-playbook.md's
from-scratch install recipe rather than attempting to reinstall inline.

### 0.5 — Android emulator + AVD

**This suite's Android target is the `Pixel_9_Pro` AVD — an emulator, always.**

```
emulator -list-avds
```
`emulator` is **not on PATH** on this Mac — invoke the absolute path,
`~/Library/Android/sdk/emulator/emulator -list-avds`, or use `android
emulator list` (the Google Android CLI's equivalent, per this repo's
CLAUDE.md "Android CLI" section) as a fallback if the emulator binary can't
be located. **Verified (2026-08-11): one AVD exists, `Pixel_9_Pro`.** Boot it
for the run:
```
~/Library/Android/sdk/emulator/emulator -avd Pixel_9_Pro &
adb wait-for-device
```
If the AVD cannot be listed or cannot boot, SKIP Android with reason "no
emulator available" — do **not** substitute a physical device (below).

**The already-connected physical Pixel 9 Pro is NOT a target for this suite.**
`adb devices` will show one on this Mac (**verified 2026-08-11:**
`49141FDAP0000L	device`). Leave it alone. Two independent reasons:

1. **The spec says so.** The design spec
   (`docs/superpowers/specs/2026-08-11-agent-regression-suite-design.md`)
   lists as an explicit non-goal: *"No physical-device automation
   (simulator/emulator only)."* This suite is unattended and overnight;
   nothing in it is allowed to drive Cole's hardware.
2. **A concrete install hazard.** `androidApp/build.gradle.kts` declares **no
   debug `applicationIdSuffix`**, so the debug build carries the *same*
   `applicationId` as the release build already installed on that phone. A
   `:androidApp:installDebug` onto it therefore either fails outright with
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (different signing key) or, if forced,
   requires uninstalling Cole's real Arcana app — destroying his live session,
   Keychain/EncryptedSharedPreferences tokens and any Developer Settings
   override. Neither outcome is acceptable from an unattended run.

If more than one Android target is attached anyway, do not guess — pin every
`adb` call to the emulator's serial (`adb -s emulator-5554 ...`) and see Phase
2.4 for the install-side pinning.

### 0.6 — arcana-server checkout, venv, dev Postgres

```
ls ../arcana-server/manage.py
```
**Verified (2026-08-11): present** at
`/Users/coletomlinson/Desktop/arcana/arcana-server/manage.py`.

**Venv path correction:** treat any reference to a `venv/` directory as
approximate — **the actual virtualenv on this checkout is `.venv/`**
(`../arcana-server/.venv/bin/activate`, verified present 2026-08-11; there
is no bare `venv/` directory in the repo). Use `.venv` in every command
below. If a future checkout genuinely uses `venv/` instead, adjust — the
working directory is authoritative over this doc.

`manage.py` itself defaults `DJANGO_SETTINGS_MODULE` to `arcana.settings.dev`
in its `main()` (`os.environ.setdefault(...)`), so **no explicit settings
env var is needed** for local dev runs — `python manage.py runserver` alone
already targets dev settings. Do not export `DJANGO_SETTINGS_MODULE=prod` or
anything else that would override this default.

Dev Postgres + Redis run via `docker-compose.yml` in the arcana-server repo
root. **Verified (2026-08-11): both containers already up** —
`arcana_postgres` (port 5432→5432) and `arcana_redis` (port 6379→6379), each
running for weeks, i.e. this is a persistent local dev stack, not something
this suite needs to bring up itself. If `docker ps` shows them absent, start
with `cd ../arcana-server && docker compose up -d` before Phase 2's server
start; if Docker itself isn't running, SKIP the entire environment/execution
phases with reason "dev Postgres unavailable" — there is no fallback DB.

### 0.7 — Port 8000 free

```
lsof -i :8000
```
**Verified (2026-08-11): port 8000 was already occupied** — by
`.venv/bin/python manage.py runserver 0.0.0.0:8000`, i.e. arcana-server's
own dev server, already running from an earlier session. **This is the
good case, not a conflict**: if `lsof -i :8000` shows a `manage.py
runserver` process, Phase 2 can skip starting a new one and just verify it
answers (`curl -sf http://localhost:8000/api/v1/health/` or equivalent — see
Phase 2).

**With one hard caveat: a pre-existing server inherited this checkout's
`.env`, which wires the REAL ops notifier** (see Phase 2.1). Reusing it means
every booking, cancel and concierge submit in Phase 3 pages the founders for
real. So reuse it only after confirming its resolved `OPS_NOTIFIER_CLASS` is
`notifications.telegram.NullOpsNotifier`; if it is not, stop **only that
specific `manage.py` PID** (never a port-based kill) and start this run's own
process on that port with the null notifier in its environment, recording the
decision. If `lsof -i :8000` shows some *other* process (not arcana-server's
runserver), record a Phase 0 SKIP for Phase 2's server-start step with
reason "port 8000 held by <command>, not arcana-server" — do not kill an
unrelated process automatically.

---

## Phase 1 — Self-audit

Runs regardless of Phase 0's outcome (it only touches the mobile repo's
source tree, no devices needed) and its findings **never halt the run** —
they are collected and surfaced in the Phase 4 report as inventory-drift
findings (report §3), nothing more.

**This phase is mechanized as `tools/regression/self_audit.sh`** — run that
rather than re-deriving the greps and the path extractor by hand; it already
encodes the anchored ViewModel grep, the surface globs, the exclusion list,
and the strip-parentheses-before-splitting extraction order. It **always exits
0** (never-halt, same as this phase) and reports on stdout as a `FINDINGS: N`
line — read that line, don't check `$?`.

The prose below remains the specification the script implements. Read it when
the script's output looks wrong, when judgment is needed on whether a basename
hit is genuine (the script can tell you a basename appears on a `- **Source:**`
line; only you can tell whether that entry is really *about* that surface), or
when the script is missing. If the two disagree, the script is the thing to
fix — file it in Phase 5.2.

### Forward — find surfaces the inventory might not cover

Enumerate the four kinds of user-facing surface the inventory is supposed
to track. **Match ViewModel *declarations*, not every file that mentions the
word** — an unanchored `grep -rl "ViewModel\b"` matches KDoc comments and DI
imports and buries the real signal under ~4x its volume in noise:

```
grep -rlE '^[[:space:]]*(open |abstract |internal |private )*class [A-Za-z0-9_]*ViewModel\b' \
  --include='*.kt' sharedUI/src sharedLogic/src | grep -v '/commonTest/'
find sharedUI/src -iname "*Screen.kt"
```
plus the modal/overlay surfaces that are user-facing but are not `*Screen.kt`
(a sheet, dialog or picker has its own state machine and its own way to fail,
so it is a surface even though it never becomes a nav destination):
```
find sharedUI/src -iname "*Sheet.kt" -o -iname "*Dialog.kt" -o -iname "*Picker.kt"
```
Apply the **same judgment rule** to these as to `*Screen.kt`: a basename hit on
a `- **Source:**` line only counts if you read the entry and it is genuinely
about that surface. **On this checkout (2026-08-11) every such file is already
covered** — `BookingSheet.kt` (CLASS-08/-19), `SpotPicker.kt` (CLASS-09),
`SpotMapFullScreen.kt` (CLASS-26) and `LateCancelWindow.kt` (CLASS-19) — so a
finding here means something genuinely new landed, not that the glob is noisy.
(`SpotMapFullScreen.kt` and `LateCancelWindow.kt` match none of the three globs
by name; they are listed because they are the same *kind* of surface, and a
future one named `*FullScreen.kt`/`*Window.kt` should be checked by eye when
the enumeration turns up a new file under `booking/`.)

Plus the nav destination graph itself:
```
sharedLogic/src/commonMain/kotlin/org/arcana/mobile/navigation/ArcanaDestinations.kt
```
(read this file directly and list every `@Serializable data object`/`data
class` destination it declares).

**Verified counts on this checkout (2026-08-11):** 15 ViewModel declarations,
15 files match `*Screen.kt` under `sharedUI/src`, 2 match the
`*Sheet.kt`/`*Dialog.kt`/`*Picker.kt` globs (`BookingSheet.kt`,
`SpotPicker.kt`), 8 nav destinations. Treat a count that has moved as a prompt
to look, not as a finding in itself.

**Exclusions — these are deliberately NOT surfaces, do not report them:**

| Excluded | Why it isn't an uncovered surface |
|---|---|
| Anything under a `commonTest/` source set, and any `*Test.kt` / `*TestFixtures.kt` | Unit tests are not user-facing surfaces; they have no UI to drive on a device. Already excluded by the `grep -v '/commonTest/'` above — the `*Test.kt` filter is the belt-and-braces version if a test ever lands outside that directory. |
| `di/AppModule.kt` | Koin wiring. It imports every ViewModel, so it matches any unanchored grep, but it has no UI surface of its own — the ViewModels it binds are each enumerated on their own. |
| API-seam interfaces (`networking/ScheduleApi.kt`, `networking/ProfileApi.kt`, `signup/CompleteSignupApi.kt`, `signup/SignupSurveyApi.kt`) and DTOs (`data/*Dto.kt`) | Fakeable seams and wire types named in KDoc as "the thing ViewModel X depends on". They are exercised transitively by the entries covering the screens that call them; they have no independent surface to drive. |
| Pure helpers (`schedule/ScheduleDisplayLogic.kt`, `ui/StudioLabels.kt`, `analytics/Telemetry.kt`) | Display/label/taxonomy logic with no surface of its own. They legitimately appear on Source lines as *supporting* files for a screen's entry; that is coverage, not a gap. |

Everything the enumeration yields that isn't excluded above must be checked:
does its path (or, for nav destinations, its name) appear in at least one
inventory entry's **Source:** line? `grep -F` the basename against the
`- **Source:**` lines of `docs/regression/inventory.md` is sufficient — those
lines are full repo-relative paths by convention (see the inventory header).
Any ViewModel, `*Screen.kt` file, or nav destination with **zero** hits is an
**"uncovered surface" finding**: name the file/destination, and if you can
tell from a quick read what it does, one sentence on what functionality is
going untested by the inventory.

A basename that hits only inside an entry that is plainly about something
else is still a finding — read the entry before accepting the hit.

### Reverse — find inventory entries pointing at code that no longer exists

For every **Source:** path listed across all 225 inventory entries, verify
the file exists in the current tree:
```
test -f <path>
```
Extraction is mechanical but **order matters**: a **Source:** line's paths are
comma-separated *and* its parenthetical annotations contain commas of their
own (`ScheduleViewModel.kt (`selectDay`, `ensureSelectedDayLoaded`)`). So:

1. **Strip balanced parentheses first**, across the whole line, before any
   splitting. Splitting on commas first shreds every annotation into
   fragments like ``ensureSelectedDayLoaded`)`` and reports each as a missing
   path — that alone manufactures ~200 phantom findings on a clean tree.
2. *Then* split the remainder on commas, trim whitespace, strip stray
   backticks, and `test -f` each resulting repo-relative path.

Sanity-check the extractor before trusting its output: on a clean tree it
should yield **81 unique paths from 492 comma-split tokens across 225 Source
lines** (one Source line per entry, which is also a free cross-check on the
entry count) — verified 2026-08-11, 2026-08-15, 2026-08-16 (after
`feature/error-states-completion` added ERR-21/ERR-22), and 2026-08-19 (after
`fix/NAV-13-pre-auth-system-back` added NAV-13). `self_audit.sh`
encodes this as a **50–90 unique-path band** and prints a SANITY WARNING
outside it; keep the two in step if either moves. A run that reports findings
in the hundreds is an extractor bug, not inventory drift — fix the extractor
and re-run rather than filing the output.

Any path that doesn't resolve is a **"stale entry" finding**: name the
inventory entry ID and the missing path. Two shapes count as not resolving
even though the code they gesture at exists, because both defeat the point of
this pass (neither can detect a rename): an **abbreviated path** with an
elided package prefix (`sharedLogic/.../schedule/Foo.kt`), and a **bundle
directory** cited instead of a file (`iosApp/iosApp/Assets.xcassets`). Both
are fixed in the inventory by writing the concrete full path — never by
loosening this check to `test -e` or by teaching the extractor to accept
`...`. This catches files renamed/deleted
since the inventory line was last updated (the inventory's own header notes
entries are meant to be kept in sync on every user-facing PR, but drift is
exactly what this phase exists to catch).

Both passes are diagnostic only — **do not edit inventory.md while the run is
driving, and never delete an entry at all** (IDs are stable and removals are
tombstoned, per the repo CLAUDE.md rule). Findings go into report §3
(inventory-drift findings).

Inventory edits happen later and deliberately, in **Phase 5.2**, once the run
has adjudicated what it saw: Expected-corrections for
INVENTORY-EXPECTED-WRONG verdicts, plus new entries for uncovered behavior.
Those edits are left uncommitted for human review. Editing mid-pass would
change the checklist out from under the devices still driving against it,
which is the actual reason for this rule.

---

## Phase 2 — Environment

### 2.1 — Start (or verify) the server

```
cd ../arcana-server
lsof -i :8000   # check first — see Phase 0.7
# only if nothing is listening:
source .venv/bin/activate && python manage.py runserver 0.0.0.0:8000 &
```
Run in the background (or a separate terminal/pane) so the phase can
proceed; capture its PID so Phase 4 (or a manual cleanup) can stop it later
if this run started it. Do not stop a server this run did not start.

**In parallel mode, start three instances instead (ports 8000/8001/8002) and
capture all three PIDs labelled by lane** — see "Run modes" above for the
per-lane port assignment and the kill-only-your-own-PIDs rule.

Sanity-check it's actually answering before moving on:
```
curl -sf http://localhost:8000/api/v1/health/ >/dev/null && echo OK
```
(If no `/api/v1/health/` route exists at run time, any 2xx/3xx from a known
unauthenticated endpoint, e.g. `POST /api/v1/auth/token/` with garbage
creds returning 400/401 rather than connection-refused, is an acceptable
substitute liveness check.)

**Before driving anything that books, cancels, or submits a concierge
request: confirm this server's ops-notifier config.** `arcana-server`'s ops
alerts (new booking, platform cancel, concierge/delete-account request)
route through `get_ops_notifier()` (`notifications/telegram.py`), which
reads `OPS_NOTIFIER_CLASS` from the environment — the safe default is
`NullOpsNotifier` (log-only), but **verified 2026-08-11: this checkout's
`.env` sets `OPS_NOTIFIER_CLASS=notifications.telegram.MultiOpsNotifier`
with children `TelegramOpsNotifier` and `PushoverOpsNotifier`** — the real,
prod-style pipeline, not the dev-safe default. `PushoverOpsNotifier` sends
**every** ops event at hardcoded emergency priority (2) regardless of the
call site's `urgent` flag (`notifications/pushover.py`) — Do Not
Disturb-breaking, siren sound, retried every `PUSHOVER_RETRY_SECONDS` for up
to `PUSHOVER_EXPIRE_SECONDS`. With this `.env` in place, **every booking,
every platform cancel, and every concierge/delete-account request driven
during Phase 3 fires a real Telegram message and a real emergency Pushover
page to the founders** — not a dev-sandboxed no-op. Before starting Phase 3,
either (a) confirm firing real ops pages during an unattended/overnight run
is expected and acceptable, or (b) override `OPS_NOTIFIER_CLASS` to
`notifications.telegram.NullOpsNotifier` in the environment of the server
process this run starts (an env var, not a code change) so Phase 3's many
bookings/cancels stay silent. See the Known-BLOCKED table below for the
PROFILE-14 (delete-account) implication specifically, and
driver-playbook.md's Safety section.

**Second outbound channel — transactional email (found 2026-08-11 post-run):**
this checkout's `.env` also sets
`EMAIL_SENDER_CLASS=notifications.email.LoopsEmailSender` with a live Loops
key. The 2026-08-11 run's three AUTH-08 (password-reset request) passes each
sent a **real Loops transactional email to an `@example.com` address** — hard
bounces on Arcana's real sending domain. Start every server process this run
owns with **`EMAIL_SENDER_CLASS=notifications.email.ConsoleEmailSender`** in
its environment as well (settings' own default; it prints instead of
sending), and prove it the same way as the notifier. Both overrides, one
command:

```
OPS_NOTIFIER_CLASS=notifications.telegram.NullOpsNotifier \
EMAIL_SENDER_CLASS=notifications.email.ConsoleEmailSender \
python manage.py runserver 0.0.0.0:8000
```

(Server-side PostHog capture is off locally — no `POSTHOG_API_KEY` in
`.env` — and the server has no Sentry, so those two are not run hazards.
The MOBILE app's PostHog/Sentry, however, ARE: see "Telemetry pollution"
under Known hazards below.)

### 2.2 — Seed the regression fixture

```
python manage.py seed_regression
```
This is **DEBUG-gated** — it refuses (`CommandError`) unless
`settings.DEBUG` is true, which is exactly what `arcana.settings.dev`
(Phase 0.6's confirmed default) sets. It is **idempotent by purge-then-
rebuild**: every run wipes and recreates the entire `regression-*` namespace
in one transaction. **Capture its stdout — the ONLY thing it prints is the
JSON manifest** — to the run folder:
```
python manage.py seed_regression > ~/arcana-regression-runs/<date>/manifest.json
```

**Manifest shape (verified against the real command output, 2026-08-11):**
```json
{
  "accounts": {
    "ios26":  { "claim": {"email": "...", "welcome_token": "..."}, "member": {"email": "...", "password": "..."} },
    "ios18":  { "claim": {...}, "member": {...} },
    "android":{ "claim": {...}, "member": {...} }
  },
  "classes": {
    "full": <session id>,
    "blocked": <session id>,
    "late_cancel": <session id>,
    "late_cancel_active": <session id>,
    "window_gated": <session id>
  }
}
```
Read the manifest for every account/session id used in Phase 3 — **never
hardcode an id or token**, they are freshly generated on every run
(including the claim tokens — a re-seed always hands you a fresh, unconsumed
`welcome_token`, invalidating any previously-noted one).

**Which fixture feeds which entry.** Each of these entries names its manifest
key in its own Steps line; this table is the index, so a driver can tell at a
glance what a given fixture is for and what breaks if the seed didn't produce
it. All five session fixtures live on the two synthetic `regression-*` studios
(`platform='fake'`), **not** on any real synced studio.

| Manifest key | What it is | Entries that consume it |
|---|---|---|
| `classes.full` | `availability='full'`, 20/20 booked, +2 days 12:00 ET, Regression Test Studio | SCHED-14, CLASS-06 |
| `classes.blocked` | `availability='blocked'`, +2 days 13:00 ET — must never surface | SCHED-19 |
| `classes.window_gated` | `bookable_at` = now + 2 days, +6 days 07:00 ET | SCHED-15, CLASS-05 |
| `classes.late_cancel` | Regression Late Cancel Studio (24h window), +3 days 18:00 ET, freely cancellable | CLASS-19 |
| `classes.late_cancel_active` | Same 24h studio, ~12h out — **inside** the cutoff; the only forfeit-reachable fixture (needs the Phase 3 fulfilment step) | CLASS-18, CLASS-20, CLASS-22 |
| `accounts.<device>.claim` | `awaiting_signup` membership + one unconsumed `welcome_token` | SIGNUP-01 … SIGNUP-23 (the whole welcome-deep-link → survey → claim flow) |
| `accounts.<device>.member` | Activated member (8 credits) + **one confirmed upcoming booking** + **two favorites** (studio-grain + location-grain) | Every authenticated entry logs in with it (AUTH-02 onward). The seeded booking backs SCHED-16, CLASS-16, CLASS-18, HOME-12; the seeded favorites back SCHED-01, SCHED-11, FAV-01, FAV-02 |

If `seed_regression` produced no manifest (see below), every entry in the
right-hand column is the SKIP list.

If `seed_regression` itself errors, this **never halts the run**: record it
as a Phase 4 finding with the error, SKIP every inventory entry that depends
on manifest fixtures (name which entries — anything referencing an
`accounts.*` or `classes.*` manifest value), and continue with whatever
inventory entries remain executable without the manifest.

**Two facts about this command that change how Phase 3 must be driven:**

1. **Re-running `seed_regression` purges ALL bookings against the two
   regression studios, from *any* account** — not just the three regression
   accounts. Run seed exactly once per full regression pass, at the start
   of Phase 2, before any device begins Phase 3. Do not re-seed between
   devices "to get a clean slate" — that both burns the manifest's ids (new
   session ids invalidate anything you wrote down from the previous seed)
   and would erase whatever state earlier devices in this same pass already
   built up.
2. **The seeded member account already carries one confirmed upcoming
   booking** (on a session dedicated to that device) plus two favorites
   (one studio-grain, one location-grain) from the moment it's seeded — this
   is fixture state, not something this run's driving created, and My
   Bookings / Favorites entries should expect to see it present on first
   login, not empty.

### Known hazards — telemetry pollution (measured 2026-08-11)

The mobile app's PostHog + Sentry SDKs initialise on "key is non-blank"
only — the `environment` super-property (`local` for localhost/10.0.2.2) is
a *tag*, not a gate. So a Debug build pointed at the local server still sends
**every event, `$screen`, `identify()`, session recording and Sentry
breadcrumb to production PostHog project 439926.** Measured for the
2026-08-11 run: 3,030 `environment=local` events in the run window vs 875
real prod events (3.5x), 59 of the day's 111 session recordings, 10 permanent
`@example.com` Persons; only dashboard 1849473 filters `environment=prod`,
the primary "Beta — App Health & Usage" dashboard does not, so run-day DAU
read +26% and 30-day `forced_logout` read 5x. Full analysis and the
recommended fix (client-side gate: no PostHog/replay/Sentry unless
`environment == prod` or a default-off Developer Settings toggle) live in
`~/arcana-regression-runs/2026-08-11/telemetry-leak.md` and on the Arcana
Regressions board. **Until that ships, every run pollutes prod analytics —
acceptable in beta, unacceptable at launch.** Interim mitigations a run
cannot apply itself: dashboard-level `environment=prod` filters, and deleting
`@example.com` Persons after a run.

### Known-BLOCKED entries — no fixture or fault-injection path exists yet

These entries have been re-discovered as BLOCKED across multiple shifts for
the same underlying reason each time. Record them BLOCKED with the reason
given here rather than re-deriving it — fixing any of these is a seed/server
change, not something a driver can work around:

| Entry | Why it's blocked | Notes |
|---|---|---|
| ERR-08 (login 5xx) | No documented way to force a specific HTTP status from the seeded fixture — killing/pausing the dev server only produces connection-refused (ERR-07's path), never a 5xx. | `docker stop`/`start arcana_postgres` (see driver-playbook.md) forces a clean, fast 500 from most authenticated endpoints without touching app or server code — worth retrying against the login endpoint specifically next shift; not yet confirmed to work there. |
| ERR-10 (login non-401/non-5xx, e.g. 403) | Same underlying gap as ERR-08 — no lever to produce a specific non-401/non-5xx status without a server code change. | Needs either a dev-only endpoint/middleware to force a status code, or a seeded suspended-membership account. |
| ERR-18 / ERR-19 (claim-form / survey submit-failure paths) | Both need a fresh, unconsumed `welcome_token`, but SIGNUP-01…23 (which must run first per Phase 3's state ordering) consumes the device's only token partway through. | Drive these two BEFORE the successful claim submit (see Phase 3's SIGNUP ordering note) or record BLOCKED with reason "needs a second unconsumed claim token." |
| AUTH-12 (forced logout on refresh-token failure) | No server-side lever to revoke just the refresh token — `SIMPLE_JWT.BLACKLIST_AFTER_ROTATION=False` and no `token_blacklist` app installed. Clearing Keychain/EncryptedSharedPreferences externally nukes the *whole* session (both are fully encrypted per-entry — iOS Keychain, Android Tink AES-GCM — so there's no way to tamper with just the refresh token). | An honest repro needs a client-storage clear followed by waiting out the real access-token TTL (300s in this seed's `SIMPLE_JWT` config) before the next authenticated request — 5+ minutes, non-parallelizable with other driving on that device. **Also:** clearing secure prefs externally also wipes the Developer Settings override (`BaseUrlProvider` persists into the same file) — re-open Developer Settings and confirm "CURRENTLY IN USE" before assuming a resulting login failure is an app bug rather than a reverted-to-prod base URL. |
| SIGNUP-20 (409 account_exists) | The manifest seeds exactly one claim token per device; by the time you'd test "submit with a token whose email already has an account," that token has usually already produced the 410 (expired) path instead (already covered by SIGNUP-19). | Needs either a second claim token per device in the seed, or a documented server-side way to pre-create an account sharing an unconsumed token's email. |
| SCHED-12 (member with zero favorites) | No regression fixture account has zero favorites. | Needs a third throwaway account with none seeded. |
| SCHED-17 (hidden-capacity / `publishes_capacity=False` studio) | No known real studio with this flag has been identified in-app without a DB query. | Needs a documented real studio slug, or a seed addition. |
| CLASS-04 (cancelled_by_studio), CLASS-07 (past-dated session), CLASS-11 (spot-preference dropdown, non-spot future session), CLASS-13/14/15 (out-of-credits / no-active-membership / outside-membership-window) | None of these session/account states exist in the regression manifest or a reachable dev-DB row. A live query confirmed zero `cancelled_by_studio` rows exist anywhere in the dev DB. CLASS-07 additionally has no UI path at all — Schedule doesn't surface already-past "today" sessions and there's no deep link to an arbitrary class id (`DeepLinkHandler.kt` only handles the welcome-token scheme). | Needs `seed_regression` fixture additions, not a driving-technique fix. |
| PROFILE-14 (delete-account success dialog) | **Not a fixture gap — a real-alert hazard.** Confirming Delete fires a real concierge request through the ops pipeline documented in Phase 2.1 above (Telegram + emergency-priority Pushover). Unsafe to drive on an unattended automated pass unless Phase 2.1's `OPS_NOTIFIER_CLASS` override is confirmed in place. | Use PROFILE-15 (the server-down failure path) as the safe substitute for exercising the same dialog machinery. |

Grid-spot-selection studios (SLT, Barry's-style fixtures) are a related trap
when hunting for CLASS-09/CLASS-26 fixtures: they return live spot data
(numbered chips, the "Expand room map" full-screen map) from the
class-detail/booking-sheet API even when their `ClassSpot` DB table has zero
rows for that session — spot data is upstream-fetched/ephemeral, not
DB-synced. Querying for "sessions with `ClassSpot` rows" is the wrong way to
find one of these fixtures; query `ClassTemplate.spot_selection_mode='grid'`
for a studio/time and open the booking sheet in-app instead.

### 2.3 — Build and install: iOS (both simulators)

For **each** of the two pinned UDIDs from Phase 0.3:
```
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,id=<UDID>' build
xcrun simctl install <UDID> <path-to-built-.app>
```
**The built product is `Arcana.app`, not `iosApp.app`.** The Xcode
*project/scheme* is named `iosApp`, but the app's product name (and `.app`
bundle) is `Arcana` — `xcrun simctl install <udid> iosApp.app` fails with
`NSPOSIXErrorDomain code 2 (lstat: No such file or directory)` (verified
2026-08-11). Don't hardcode either name: resolve the real `.app` from the
build's own output —
```
ls ~/Library/Developer/Xcode/DerivedData/iosApp-*/Build/Products/Debug-iphonesimulator/*.app
```
(There is normally exactly one `.app` in that directory; if `xcodebuild` was
pointed at a custom derived-data path, resolve from that path instead of
guessing the DerivedData hash.)
Per driver-playbook.md's "Traps" section: **grep build output for `BUILD
SUCCEEDED`/`BUILD FAILED` explicitly** rather than trusting `$?` if the
build output is piped through anything (e.g. `| tee`), since the pipe's
exit code masks `xcodebuild`'s real one.

If a build or install fails for one of the two UDIDs, this **never halts the
run**: record that device's entire Phase 3 pass as SKIP with the build/install
error as reason in the Phase 4 summary, and continue with the other device.

### 2.4 — Build and install: Android (the `Pixel_9_Pro` emulator only)

The install target is the booted `Pixel_9_Pro` AVD from Phase 0.5. **Never
install onto the connected physical Pixel 9 Pro** — spec non-goal, and
`androidApp` has no debug `applicationIdSuffix`, so `installDebug` onto that
phone collides with Cole's release-signed daily driver (see 0.5 for the full
reasoning: `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, or uninstalling his real app
and session).

Because a bare `installDebug` picks whatever single target `adb` happens to
see, **pin the serial explicitly** whenever the physical device is also
attached (which is the normal state of this Mac):

```
./gradlew :androidApp:assembleDebug
adb -s emulator-5554 install -r \
  androidApp/build/outputs/apk/debug/androidApp-debug.apk
```
(Resolve the real emulator serial from `adb devices` — it is
`emulator-<port>`, never the physical device's `49141FDAP0000L`.) A bare
`./gradlew :androidApp:installDebug` is acceptable **only** when `adb devices`
shows the emulator as the sole attached target.

If the Android build or install fails, this **never halts the run**: record
Android's entire Phase 3 pass as SKIP with the build/install error as reason
in the Phase 4 summary, and continue with the remaining devices.

### 2.5 — Point every install at the local dev server

Each of the three installs needs its base URL overridden via the in-app
**Developer Settings** screen — the runtime override that exists precisely
for this (see this repo's CLAUDE.md "Temporary debug treatment" section).
The default on a fresh install is `https://api.arcana.fit` (prod) on both
platforms, so **this step is mandatory, not optional** — skipping it means
Phase 3 silently drives against production instead of the seeded dev
fixture.

Developer Settings has **no visible entry point** — it is reached only by
tapping the wordmark logo on the (signed-out) Auth screen **10 times in a
row** (`AuthScreen.kt`, gesture has no ripple/indication by design). This
matters specifically because the **CLAIM accounts start signed out** — a
fresh install always boots to AuthScreen when unauthenticated, so the
10-tap gesture is reachable pre-auth exactly when you need it (before
attempting the claim deep link, so the deep link itself resolves against
the right server).

**Reused simulators/emulator are NOT behaviorally fresh, even after a fresh
install.** `xcrun simctl install <udid>` and `adb install -r` install over
whatever app data already exists on the device — neither clears iOS Keychain
nor Android EncryptedSharedPreferences. Phase 0.3/0.5 explicitly reuse
persistent, already-provisioned devices rather than creating them per run, so
a device can already be signed in as a prior test/dev account **and** already
carry a Developer Settings override from an earlier shift. **Verified
2026-08-11: all three devices in one run were already signed in AND already
overridden** (iOS both already at `http://localhost:8000`, Android already at
`http://10.0.2.2:8000`) before this step was even attempted. If a device
boots straight to Home/Schedule instead of the signed-out Auth screen, the
10-tap wordmark gesture is unreachable until you sign out — **sign out via
the Profile tab first**, then run the 10-tap gesture on the resulting Auth
screen. Don't assume an already-present override still points at the right
server either; confirm (or re-set) it even on a device that looks
pre-configured.

Set (sequential mode — one server on 8000):
- **iOS simulators (both UDIDs):** `http://localhost:8000`
- **Android (`Pixel_9_Pro` emulator — `10.0.2.2` is the emulator's alias for
  the host loopback, which is exactly why the emulator is the target and a
  physical device, needing a tunnel, is not):** `http://10.0.2.2:8000`

**In parallel mode each device points at its own lane's port instead**
(ios26 → `http://localhost:8000`, ios18 → `http://localhost:8001`, android →
`http://10.0.2.2:8002`); see the "Run modes" table. Getting this wrong is
silent — two lanes sharing one server means one lane's connection-failure
kill takes the other lane down mid-entry — so verify each device individually
after setting it, not just "the iOS ones."

Cleartext to `localhost`/`10.0.2.2` is already permitted by the debug-only
network security config (`sharedUI/src/debug/network_security_config.xml`
on Android; no ATS exception needed on iOS simulators since simulator
networking isn't sandboxed the way a physical device is) — no extra
manifest/plist work needed for a debug build.

Per driver-playbook.md's "Traps": **the URL field pre-fills with the
current value** — clear it before typing, or you'll get
`http://newhosthttp://oldhost`-style concatenation.

---

## Phase 3 — Execution

**Read this before starting any device.** Seeded favorites flip
`ScheduleViewModel`'s default scope toggle to Favorites — a fresh seeded
member's Schedule tab opens **filtered to only the two regression studios**,
not the full dev-DB schedule. **This is expected fixture behavior, not a
bug**: the seed intentionally gives each member two favorites so the
Schedule surface has deterministic, findable content on a dev DB that
otherwise holds 60k+ real synced sessions. Do not record a FAIL for
"Schedule shows fewer classes than expected" or "Schedule opens on
Favorites instead of All" against a freshly-seeded member account — that IS
the seed working correctly. (A tester who wants to see the full unfiltered
schedule can toggle scope manually; that's a legitimate thing to spot-check
but is not itself an inventory entry's Expected behavior.)

### State ordering — the fixture is pristine exactly once

Several fixtures are **one-shot or state-mutating**, and re-running seed to
recover is forbidden mid-pass (Phase 2.2 note 1). Within each device's pass:

- **Claim/SIGNUP entries run FIRST.** The claim flow consumes
  `accounts.<device>.claim`'s single `welcome_token` — after SIGNUP-18's
  successful submit that membership is `active` and the token is spent, so any
  later entry that assumes an *unclaimed* account will now see the
  "Log in instead" routing (which is what SIGNUP-19/-20 deliberately assert).
  Run the SIGNUP block before anything that presumes the claim account is
  already claimed, and **never re-run a consumed claim entry** — a re-run
  needs a re-seed, and a re-seed burns the whole manifest for every device.
- **The `welcome_token` itself is consumed only by the claim-form submit,
  not by the survey.** `SignupSurveyScreen`'s submit validates the token but
  never consumes it (verified 2026-08-11) — only `SignupCompletionScreen`'s
  final CREATE ACCOUNT submit does. This has three implications:
  - SIGNUP-01 through SIGNUP-10 (the survey-block entries) can be freely
    re-driven within a shift without a re-seed, **as long as the claim form
    itself is never actually submitted.**
  - **Drive ERR-18 and ERR-19 (the claim-form / survey submit-failure paths)
    before the successful claim submit**, not after — both need a
    still-unconsumed token, and the SIGNUP block's own required ordering
    (above) means the token gets consumed partway through it. See the
    Known-BLOCKED table in Phase 2.2 if the window is missed.
  - Consuming the token early also forecloses NAV-06/07/09, TEL-10/11 (the
    fresh-claim-token cases), and SIGNUP-08/09/10/19/20 for the rest of that
    device's pass — this blast radius isn't limited to the SIGNUP block
    itself, so budget for it before starting SIGNUP-01, not after hitting it
    mid-pass in a later block.
  - Survey-scoped entries specifically (SIGNUP-07/08/09/10) additionally
    require being on `SignupSurveyScreen` **pre-completion** — once
    `signup_survey_done:<token>` is persisted (survey Continue tapped),
    those four are permanently blocked for that device with no re-seed
    available mid-pass. Drive SIGNUP-07/08/09/10 contiguously with
    SIGNUP-01-06 in the same shift/session rather than deferring them; their
    window is narrower than the rest of the SIGNUP block.
  - Re-delivering a welcome link whose survey step is already marked done
    but whose claim step has NOT yet been submitted routes to a **blank**
    (not pre-filled) claim form, not an immediate error — the "already
    used" error state only appears after actually submitting the claim form
    with a consumed/expired token. Don't read arriving on a blank claim
    form as evidence the link itself failed.
- **Sign-out entries run LAST on each device.** AUTH-11 (and its
  cross-reference PROFILE-12) end the session; everything authenticated must
  already be recorded before it. AUTH-12 (forced logout) has the same effect —
  group it with AUTH-11 at the end of the device's pass, then log back in only
  if a genuinely re-run entry demands it. This is about *when the entry is
  driven and recorded*, not a ban on signing out: the pass necessarily signs
  out of the freshly-claimed account to sign in as `accounts.<device>.member`.
  Do that as an unrecorded setup step and leave AUTH-11 itself for the end.
- **DEVSET is a sanctioned exception to "sign-out entries run last."**
  Developer Settings is only reachable from the signed-out Auth screen
  (Phase 2.5), so driving the DEVSET block always requires an unrecorded
  sign-out/sign-in round trip mid-pass on whichever device is currently
  authenticated — this applies on Android exactly as it does on iOS. This is
  the same unrecorded-setup-step allowance as the bullet above, not a
  violation of the sign-out-last rule; it's called out explicitly here so a
  driver doesn't skip the DEVSET block or second-guess driving it out of
  AUTH-11's order.
- **Booking/cancel entries mutate credit and reservation state.** The member
  starts with 8 credits and one confirmed booking; every book spends a credit
  and every forfeiting cancel keeps it spent. **Drive them in inventory-ID
  order** and treat each entry's noted after-effects (see "State-mutating
  entries" below) as the expected starting state of the next one, rather than
  re-deriving "what should the count be" from the seed.
- **This ordering is per device and does not reset between devices.** Studios
  and most sessions are shared, so device 2 legitimately sees device 1's
  bookings on the shared ordinary classes. Only the claim account, the member
  account, and the one dedicated reserved session are per-device.

### Device order — sequential by default

**iOS 26 → iOS 18 → Android.** In the default sequential mode this is not
reorderable: complete one device's full pass (all applicable entries,
PASS/FAIL/BLOCKED recorded) before starting the next. This keeps driver
attention on one accessibility tree/tool chain at a time and keeps the Phase 4
report's per-device sections honestly sequential (device 2's state is not
contaminated by device 1 mid-flight, since sessions/classes are shared across
devices per Phase 2.2 note 1 — running strictly sequentially means the second
and third devices see the first device's state-mutating effects on shared
fixtures as prior state, not as a race).

**Parallel mode is the one sanctioned exception**, and only when explicitly
selected: three concurrent lanes, one per device, each on its own server port.
Read "Run modes" above in full before choosing it — every rule in this Phase 3
still applies inside each lane, unchanged.

**Either way, exclusive/environment-wide-fault entries are recorded DEFERRED
and driven by the serialized tail phase** (see the DEFERRED label under "Status
vocabulary"). That is not a parallel-mode-only behavior: sequential runs batch
them for the same reason the tail exists at all, a single stop/restore of the
shared dev Postgres with one verified restore at the end.

### Per device: which entries apply

Every entry's **Platforms:** field is one of `shared`, `iOS-only`,
`iOS26-only`, `iOS18-only`, or `Android-only` (the latter two occasionally
carry a parenthetical explaining why, e.g. "(iOS reaches the same
destination via ... see TEL-11)" — match on the leading token only). Run
the entry if:

| Device | Runs entries tagged |
|---|---|
| iOS 26 | `shared`, `iOS-only`, `iOS26-only` |
| iOS 18 | `shared`, `iOS-only`, `iOS18-only` |
| Android | `shared`, `Android-only` |

Anything else is **SKIP** for that device — silent in the sense that it's
expected, but still recorded as SKIP in the per-item results table (Phase
4), not simply omitted.

### Per device: which manifest accounts to use

Use **only that device's own account pair** from the Phase 2.2 manifest —
`accounts.ios26.*` on the iOS 26 device, `accounts.ios18.*` on the iOS 18
device, `accounts.android.*` on Android. Never log the iOS 26 claim/member
account into the Android build or vice versa — the three account sets exist
specifically so devices don't collide on account-level state (claim-once
tokens, per-device dedicated booked session) even though they share the
underlying studios and most bookable classes.

### Driving discipline

Every entry: drive it via the **dump→act→verify loop** from
driver-playbook.md — one action per UI dump, re-dump, confirm the expected
post-action state before the next action. Use `~/.arcana-tools/idb-venv/bin/idb
ui describe-all --udid <UDID>` (iOS) or `adb shell uiautomator dump` (Android)
as the read model; `~/.arcana-tools/idb-venv/bin/idb ui tap` /
`~/.arcana-tools/idb-venv/bin/idb ui text` or `adb shell input
tap/text/swipe/keyevent` to act. **Always spell out the idb venv path** — bare
`idb` is not on PATH on this Mac (Phase 0.4), so an unqualified invocation
fails with `command not found` and reads as a driver bug. Watch for the traps
documented there (stale scroll position, pre-filled text fields, off-screen
submit buttons, edge-gesture hijacking, the floating tab bar's
touch-interception zone, etc.) before concluding a step failed.

**On any apparent failure, apply the driver-bug protocol BEFORE recording
FAIL:** reproduce a second time via a different interaction path (different
tap sequence to the same state, or the platform's alternate inspection tool
— e.g. the `android layout`/`screen capture -a` fallback on Android
alongside `uiautomator dump`). If it only reproduces one way, or doesn't
reproduce again, record a **suspect-driver** finding instead of FAIL, naming
the specific suspect action/trap — never assert an app bug from a single
reproduction.

**Capture a screenshot at the moment of every FAIL and every suspect-driver
finding**, before navigating anywhere else, saved into this run's
`screenshots/` folder (Phase 4) and named `<device>-<entry-id>-fail.png` /
`<device>-<entry-id>-suspect.png`, and reference that filename from the
entry's result line — a failure with no image is a failure the reader cannot
adjudicate.

**Entries depending on a failed upstream entry become BLOCKED**, not
attempted-and-FAILed — e.g. if AUTH-02 (login success) is FAIL on a device,
every entry on that device that requires being logged in is BLOCKED with
reason "blocked on AUTH-02." Name the specific blocking entry ID, don't just
say "upstream failure."

### Record every result the moment it happens — never batch

A full pass is long enough that the session running it can be killed, hit a
context limit, or be interrupted. **Nothing may live only in the agent's head.**
As each entry finishes, immediately append one line to a results log in the run
folder:
```
~/arcana-regression-runs/<date>/results-<device>.log     # one per device, BOTH modes
```
One line per (entry, device), append-only, e.g.:
```
ios26  SCHED-14  PASS
ios26  SCHED-15  FAIL   overline read "AVAILABLE"; screenshots/ios26-SCHED-15-fail.png
ios26  PROFILE-12 PASS  see AUTH-11
android CLASS-18 PASS   applied Phase 3 fulfilment step first; credit forfeited
```
Write it **before** starting the next entry, not at the end of an area, not at
the end of a device. Phase 4's report is assembled *from* these logs, so they
are the primary artifact and `report.md` is the derived one. **A device writes
only to its own `results-<device>.log`**, in both modes, and never to another
device's; nothing writes a shared `results.log` during Phase 3 (Phase 4 merges
the per-device logs into one, and in parallel mode concurrent appends to a
shared file would interleave and corrupt each other anyway).

**On resume after any interruption:** re-read this device's
`results-<device>.log`, take the last
recorded (device, entry) pair as the high-water mark, and continue from the
first entry after it in inventory order for that device. Do not re-drive
already-recorded entries — several of them mutate state that cannot be
restored without a re-seed (see "State ordering" above).

**The high-water mark alone is not sufficient — it misses gaps below it.** A
prior shift can leave mid-block holes (an entry driven out of order, or
skipped and never logged, not even as SKIP) that a "resume from the line
after the last one" jump will never revisit. Before continuing from the
high-water mark, diff that device's recorded entry IDs in its
`results-<device>.log` against the full inventory-order ID list, and drive/log any earlier gap you
find (SKIP if genuinely inapplicable, driven-and-recorded otherwise) in
addition to continuing forward from the mark. **Verified 2026-08-11:** one
android pass alone had roughly 50 earlier entries with no log line at all —
found only by a full-ID diff, not by the high-water mark.

### Checkpoint screenshots

Capture a screenshot at these three moments **on every device, even when
the entry PASSes** (not just on failure — these are the report's visual
proof of a working pass, not just failure evidence):
- **Post-login home** — immediately after AUTH-02 succeeds, the Home tab's
  first fully-loaded frame.
- **Post-claim success** — immediately after a claim-account flow
  (SIGNUP-xx entries) completes and lands the new member in the app.
- **Booking confirmation** — immediately after a successful `createBooking`
  (CLASS-08 or equivalent), showing the CTA's "REQUESTED ✓" state or the
  confirmation sheet close.

iOS: `xcrun simctl io <UDID> screenshot <path>.png`. Android: `adb shell
uiautomator` doesn't screenshot — use `adb exec-out screencap -p >
<path>.png` (or `android screen capture -a` per this repo's CLAUDE.md, which
also gives an annotated/labeled variant useful for later reference). Save
all screenshots under this run's `screenshots/` folder (Phase 4) named so
the device and checkpoint are obvious at a glance, e.g.
`ios26-post-login-home.png`.

**HOME-01's Loading shimmer is a known-hard-to-catch checkpoint against a
local dev server.** Same-machine Postgres round-trips are sub-second, so
polling a screenshot at fixed offsets (tried across two shifts: 0.3s, 0.4s,
0.8s, 0.9s, 1.3s, 1.7s post-relaunch) reliably lands either mid-splash or
already at the loaded Success state — the shimmer frame itself has never
been caught this way. Treat this as commonly-BLOCKED/unverifiable-by-
screenshot rather than a driving failure: recording PASS on "no crash/blank
observed" (without a true shimmer capture) is acceptable. A future run
wanting the actual shimmer content would need to throttle the local network
or intercept the response — out of scope for a driving pass.

### State-mutating entries — note effects on later expectations

Booking, favoriting/unfavoriting, and profile-edit entries change state that
persists for the rest of the run (and, since studios/most sessions are
shared across devices, sometimes for later devices too). When driving one of
these, write a one-line note of the effect alongside its PASS/FAIL, e.g.
"booked `classes.full`'s sibling ordinary session — reduces its spots by 1
for iOS 18 and Android's passes" or "unfavorited the location-grain
favorite — Schedule's default scope may flip off Favorites if that was the
member's last favorite; re-check FAV-related entries downstream on this
device." This is what lets the Phase 4 report's reader tell "the app is
broken" apart from "an earlier step in this same run legitimately changed
the state the later step observed."

### The forfeit-warning-on-cancel path needs an extra manual step

`classes.late_cancel_active` (from the Phase 2.2 manifest) is seeded
**inside** its studio's late-cancel window specifically so the cancel flow's
forfeit warning can be exercised — but **booking it from the app alone is
not sufficient to reach the warning.** The regression studios are
`integration_mode='manual'`, so a member-initiated booking through the app
lands as `Booking.status == 'requested'` with an empty
`external_booking_id`. The server's `compute_cancel_policy` only reports
`will_forfeit_credit: true` when **all three** of these hold:
```python
booking.status == Booking.STATUS_CONFIRMED
and bool(booking.external_booking_id)
and timezone.now() >= cutoff_at
```
A freshly-booked `requested` booking on `late_cancel_active` therefore
correctly shows **no** forfeit warning on cancel — that is expected behavior
for the device-only path, not a bug, and should not be recorded as a FAIL
against a forfeit-warning inventory entry by itself.

To actually reach the warning state for the relevant TEL/CLASS/booking
inventory entries, insert this **fulfilment step** between booking
`late_cancel_active` and cancelling it. (**This is an exclusive DB-level
mutation** — it touches shared `Booking` rows through one shared dev DB — so a
lane records the affected entries DEFERRED and lets the serialized tail phase
drive them, in either mode; see "Run modes" and the DEFERRED label under
"Status vocabulary.")

```
cd ../arcana-server && source .venv/bin/activate && python manage.py shell -c "
from django.utils import timezone
from bookings.models import Booking
Booking.objects.filter(
    class_session_id=<late_cancel_active session id from manifest>,
    status=Booking.STATUS_REQUESTED,
).update(status=Booking.STATUS_CONFIRMED, external_booking_id='regression-ext-1', confirmed_at=timezone.now())
"
```
(Substitute the real `classes.late_cancel_active` id from this run's
captured manifest — ids are fresh every seed, never hardcode one.) After
running it, re-open the booking in the app (pull-to-refresh My Bookings or
re-enter Class Detail) and only then drive the cancel — `GET
/api/v1/bookings/me/` will now return `cancel_policy.will_forfeit_credit:
true` for that row and the app should render the forfeit warning. Note in
the per-item result that this manual fulfilment step was applied, so the
report doesn't read as the app spontaneously changing booking status.

**Model import reference for ad-hoc `manage.py shell` one-liners** (this
step and any fixture-hunting query): `Booking` lives in `bookings.models`
(as used above); `ClassSession` lives in **`integrations.models`**, not
`classes.models` or `schedule.models` (both of those raise
`ModuleNotFoundError`, verified 2026-08-11) — and its start-time field is
**`start_at`**, not `start_time`. `Booking` has no `member`/`membership_id`
convenience accessor visible from the shell; check `_meta.fields` before
guessing a field name.

### Other fixture/reload behaviors that read as bugs but aren't

- **SCHED-02 (error-retry).** `ScheduleViewModel.reload()` calls
  `refetchForFilters` directly and does not re-run the favorites-
  determination fetch. If the cold-start favorites fetch also failed (e.g.
  the server was down), a successful retry leaves scope at its pre-failure
  default (AllStudios) rather than re-resolving to Favorites — this is
  correct per source, not a regression, and shouldn't be recorded as a FAIL
  against SCHED-02 by itself.
- **CLASS-25 (credit decrement after booking).** `HomeViewModel` has no
  resume-refresh (Schedule has one via `LifecycleResumeEffect`; Home
  doesn't). After booking a class from Class Detail, Home's credit count and
  Next-Up card stay stale until a manual pull-to-refresh. **Pull-to-refresh
  Home before checking the credit count** on CLASS-25, or the stale reading
  looks like a broken decrement when it isn't.

---

## Phase 4 — Report

The run folder:
```
~/arcana-regression-runs/YYYY-MM-DD/report.md        ← THE deliverable
~/arcana-regression-runs/YYYY-MM-DD/screenshots/
~/arcana-regression-runs/YYYY-MM-DD/manifest.json    (from Phase 2.2)
~/arcana-regression-runs/YYYY-MM-DD/results-<device>.log   one per device, written during Phase 3 (both modes)
~/arcana-regression-runs/YYYY-MM-DD/results.log      merged from those, here in Phase 4
```
using today's actual date for `YYYY-MM-DD`. This directory is **never**
inside either git checkout — nothing produced by this run is committed,
staged, or pushed, under any circumstance.

### One document, human-first

**There is exactly one report deliverable: `report.md`.** Working files are
fine (scratch tables, triage notes, a raw learnings dump) and can sit in the
run folder, but they are *not* deliverables and must never be concatenated
into the report. The 2026-08-11 run shipped a `report-draft.md` glued to a
218-row `full_table.md` and the result was unreadable — the reader had to mine
a wall of PASS rows to find the three things that mattered. There is no
`report-draft.md` deliverable and no `full_table.md` deliverable; if the
orchestration writes such files as intermediates, Phase 4's job is to *absorb*
them into the one document, not to ship them.

The document is written for a human who has ten minutes and wants to know what
is broken. The audit trail still exists — it is just last, not first.

### `report.md` structure — five sections, in this order

**§1 — Verdict + per-device summary.** Open with a one-line verdict for the
run as a whole ("App: PASS with 3 confirmed non-blocking defects"; "App: 1
blocking defect — see BOOK-04"), then the per-device table: one row per device
(iOS 26 / iOS 18 / Android) with PASS/FAIL/BLOCKED/SKIP counts and a one-line
per-device verdict. Applicable counts on today's inventory (225 entries: 197
`shared`, 11 `iOS-only`, 1 `iOS26-only`, 1 `iOS18-only`, 15 `Android-only`)
are **209** for iOS 26, **209** for iOS 18, **212** for Android — recompute
from the file rather than trusting these if the inventory has moved. Also name
the run mode (sequential/parallel) and wall-clock here. Nothing else. §1 fits
on one screen.

**§2 — ISSUES DIGEST.** This is the body of the report and the reason the
report exists. Four subsections, in this order:

- **Confirmed app bugs** — adjudicated APP BUG in Phase 5.1.
- **Potential issues & UX observations** — behavior that matches current code
  (so it PASSed, or was adjudicated not-a-bug) but that a reviewer might not
  want shipped as-is.
- **Suite & tooling gaps** — why entries were BLOCKED or unverifiable;
  fixture, fault-injection and harness shortfalls.
- **Operational hazards** — things that can hurt a person or an environment
  (real pages fired at the founders, a kill pattern that takes out the
  emulator), whether or not they were mitigated during the run.

Every item, in every subsection, carries the same seven fields:

| Field | Content |
|---|---|
| **Title** | One line, ≤80 chars, prefixed with the primary entry ID and a `·` (`PROFILE-22 · Edit Profile save blocked by optional fields`). Same shape as the Phase 6 card title, deliberately — Phase 6 lifts it verbatim. If no entry covers it, use the family prefix (`SUITE`, `HAZARD`) and propose the new entry ID in the body. |
| **Symptom** | What a person sees. No code. |
| **Root cause** | The mechanism, with the file/symbol, *if known*. "Not yet established" is a legitimate value — say it rather than guessing. |
| **User impact** | Who is affected, doing what, and how badly. This is what severity is argued from. |
| **Severity** | Low / Medium / High. |
| **Evidence** | Cited entry IDs, learning numbers, screenshot paths, `results.log` line refs. |
| **Proposed disposition** | What should happen: fix here, fold into an existing branch, correct the inventory, needs a product decision, accept as-is. |

Once Phase 6 has run, each item also carries its **tracker card URL**.

Severity is a judgement about user impact, not about how loud the failure
was: a silent wrong value a member acts on outranks a dev-only screen that
looks odd. Rough calibration — **High**: members blocked from a core action
(book, cancel, log in), data shown wrong in a way they'd act on, or anything
that loses their input. **Medium**: a real defect with a workaround, or a
surface degraded enough to erode trust. **Low**: cosmetic, dev-only, or an
observation raised for a decision rather than a fix. Cole may re-label; the
run's job is to argue the severity, not to own it.

**§3 — Inventory-drift findings** (from Phase 1) — every "uncovered surface"
and "stale entry" finding, each naming the file/entry ID involved. State the
zero explicitly when there are none ("0 forward, 0 reverse") — a silent
section reads like the phase was skipped.

**§4 — Suspect-driver findings** — every entry the driver-bug protocol
annotated, with what was suspected and how Phase 5.1 adjudicated it. None may
be left unadjudicated; if one genuinely could not be resolved, say what would
resolve it.

**§5 — APPENDIX: full per-item results** — every entry, every applicable
device, PASS/FAIL/BLOCKED/SKIP, in inventory order. This is the audit trail
and it is **always last**, wrapped in a collapsed `<details><summary>Full
per-item results (225 entries × 3 devices)</summary>` block so it never
competes with §2 for the reader's attention.

**Phase 4 merges the logs before it counts anything.** In both modes this
table is built by concatenating the per-device `results-<device>.log` files
into `results.log` (device order, each device's lines in inventory order),
leaving the per-device files intact. The deferred tail appends a second line
for the entries it drove, so **where an ID appears more than once for a
device the last line is authoritative** — say so in the appendix header, and
verify no DEFERRED status survived the merge.

### §2 is provisional until Phase 5

Phase 4 writes all five sections, but its §2 is a first cut assembled from
what the driving shifts recorded. **Phase 5.1 rewrites §2** with adjudications
(a FAIL that turns out to be a driver artifact moves out of "Confirmed app
bugs" entirely, with a note in §4), and **Phase 6 adds the card URLs**. The
report is final only after Phase 6.

### Delivery — after Phase 6, not here

`report.md` is written here but **delivered once, at the end of the run**,
after Phase 5 has rewritten §2/§4 and Phase 6 has added the card URLs.
Delivering the Phase 4 draft hands the reader a report whose headline findings
may not survive triage — exactly the 2026-08-11 "ten iOS 26 crashes" mistake.
The mechanics, whenever you do deliver:

Deliver the finished report to the user via **SendUserFile** — don't just
leave it on disk and mention the path in chat.

**If no `SendUserFile` (or equivalent file-delivery) tool is available in the
session**, do not silently drop the delivery step and do not skip writing the
file. Write `report.md` to disk as above, then make the session's **final
message** carry both: the report's **absolute path** (`/Users/<user>/arcana-
regression-runs/<date>/report.md`, not `~`-relative) **and §1 inline** — the
verdict line and the full per-device summary table — so the run's verdict is
readable without opening anything. Add a one-line-per-item summary of §2's
Confirmed app bugs; the path is what gets the reader to the rest.

---

## Phase 5 — Triage & fold-back

**Mandatory.** A run that stops after Phase 4 has told you what it saw; it has
not told you what is true, and it has left the next run to rediscover
everything this one learned. Phase 5 is what makes the suite self-improving.

Phase 5 is where the model changes: driving shifts run on a fast model,
**adjudication and fold-back want the strongest one available** (see the
skill's "Orchestration & models"). It reads code, it does not drive devices —
except when reproducing a claimed crash (5.1).

### 5.1 — Adjudicate EVERY FAIL and every suspect-driver

No exceptions, no sampling. Each one resolves to exactly one of:

| Verdict | Meaning | What follows |
|---|---|---|
| **APP BUG** | The code genuinely does the wrong thing. | Goes to §2 "Confirmed app bugs" and gets filed in Phase 6. |
| **INVENTORY-EXPECTED-WRONG** | The app behaves correctly; the inventory entry's **Expected** describes something the code never did (or no longer does). | The entry is corrected in Phase 5.2. Usually also worth an §2 "Potential issues" item if the real behavior is itself a rough edge. |
| **DRIVER ARTIFACT** | The failure was manufactured by how it was driven, not by the app. | Goes to §4 with the mechanism named, and the trap goes into driver-playbook.md in 5.2. |
| **FIXTURE/ENVIRONMENT GAP** | Nothing is wrong with the app, the entry or the driving — the state the entry needs cannot be reached from the current seed/server at all. Usually surfaces as a BLOCKED rather than a FAIL. | Goes to §2 "Suite & tooling gaps" and is filed in Phase 6 as a `suite-gap` card; 5.2 adds or refreshes its row in Phase 2.2's Known-BLOCKED table with the real reason. Never filed as an app bug. |

**Also sweep the run's BLOCKEDs** — not to re-adjudicate the ones Phase 2.2's
Known-BLOCKED table already explains, but to catch any *new* one, which is a
FIXTURE/ENVIRONMENT GAP and needs its own row there plus a §2 "Suite & tooling
gaps" item.

**The method is to read the source, not to re-reason from the symptom.** Open
the file(s) on the entry's `- **Source:**` line and check what the code
actually does against what the entry's Expected claims. An adjudication that
cites no file is not an adjudication. Where the seam is server-side, read the
`arcana-server` code too (the 2026-08-11 HOME-03 call turned entirely on
`serializers.py serialize_me()` substituting the email for a blank
`display_name` — invisible from the client).

**Crash-class claims get a higher bar.** Any claim that the app crashed,
was killed, or "died" must be tested by:

1. **Reproducing on the same device and the same build**, deliberately, with
   the console captured (`xcrun simctl launch --console-pty <UDID> <bundle-id>`
   on iOS; `adb logcat` on Android). Several attempts, not one.
2. **Checking `~/Library/Logs/DiagnosticReports`** for a matching crash log
   around the claimed timestamp — simulator crashes land there on the host,
   under the app's name. (Android's equivalent is the `FATAL EXCEPTION` /
   tombstone trail in `adb logcat -b crash`.)

Know what the absence proves: **a SIGKILL leaves no crash log at all.** So "no
crash log" is not by itself evidence of "no crash" — it is evidence that
*if* the process died, something killed it from outside rather than it
crashing. That is precisely the shape of a driver artifact, which is why the
repro attempt in step 1 is the part that actually decides.

**A claimed crash with no crash log and no reproduction is a DRIVER ARTIFACT
until proven otherwise.** Record it that way and say what would change the
verdict.

> **The cautionary tale — 2026-08-11.** One shift logged **ten** ERR-entry
> FAILs asserting the iOS 26 app crashed to springboard on any network call
> with the server down. Triage could not reproduce a single crash in 15+
> deliberate triggers on the same sim and build, found zero crash logs, and
> confirmed the Darwin network exception is caught by the shared handlers.
> The real cause: that shift killed the server with a port-based
> `lsof -ti :8000` kill, which also matches processes merely *connected* to
> the port — including the app under test. SIGKILL leaves no crash log, which
> is exactly why "no crash log" reads as "crash" to a driver who isn't
> looking for it. Ten headline FAILs, all phantom, from one banned command.
> That is the failure mode this step exists to catch, and it is why the
> playbook now bans port-based kills outright.

Write each adjudication down (a `triage.md` working file in the run folder is
fine), then **rewrite report.md §2 and §4** to match. Anything adjudicated
DRIVER ARTIFACT leaves "Confirmed app bugs" entirely.

### 5.2 — Fold the learnings back into the docs

Every run generates learnings: a documented command that failed, a step that
was missing, a trap nobody had written down, an Expected that was wrong. They
are worthless in a run folder. Fold them into the three checked-in docs:

| Doc | What lands there |
|---|---|
| `docs/regression/driver-playbook.md` | Driving **technique** and **traps**: a tool invocation that doesn't work as documented, a gesture that needs a different approach on one platform, a safety rule (the banned kill pattern), an inspection quirk. Every DRIVER ARTIFACT from 5.1 should leave a trap behind here. |
| `docs/regression/runbook.md` (this file) | **Procedure**: a phase step that was wrong/missing, ordering constraints discovered the hard way, a new Known-BLOCKED row with its real reason, a pinned value that moved, a mode/coordination rule. |
| `docs/regression/inventory.md` | **Coverage**: every INVENTORY-EXPECTED-WRONG correction, plus **NEW entries** for behaviors this run observed that no entry covers. |

Two rules for inventory edits specifically:

- An INVENTORY-EXPECTED-WRONG correction rewrites the entry's **Expected** to
  what the code actually does, and appends a dated note on the entry:
  `_Corrected after run 2026-08-11: server substitutes the full email for a
  blank display_name, so the client-side `substringBefore("@")` fallback is
  unreachable._` The note is not optional — it is what stops the next reader
  from "fixing" the entry back.
- Behavior observed with **no covering entry** becomes a NEW entry, with a
  fresh ID in the right family (IDs are stable and never reused — see the
  repo CLAUDE.md rule). The 2026-08-11 run turned up two: pre-auth system
  back exiting the app and losing typed form state (proposed NAV-13 /
  SIGNUP-24), and the password-reset "Sent" state persisting across
  re-entries.

**These edits are left as an UNCOMMITTED working-tree diff on a branch, for
Cole's review. The run never commits them, never pushes, never opens a PR.**
This is the single sanctioned exception to "outputs never enter git" — and it
is only an exception in that it *touches* the repo. Report in the final
message which files were edited and roughly what changed, so the diff is
expected rather than a surprise.

**Generic "worth flagging for the app team" learnings are issues, not notes.**
If a learning says something is worth someone's attention but doesn't fit any
of the three docs — a UX rough edge, a stale behavior, a design question — it
becomes an **§2 issues-digest item** (usually under "Potential issues & UX
observations") and, in Phase 6, a card. It never gets parked as a bullet in a
run-folder file where nobody will read it. If it is worth flagging, flag it
where flags are read.

### 5.3 — Hand the surviving issues to the tracker

Everything still standing after 5.1 — all APP BUGs, plus the potential
issues / UX observations, suite gaps and hazards from §2 — goes to Phase 6.
Suite gaps and hazards are filed too: they are work someone has to do, and
they are the reason a chunk of the inventory is permanently BLOCKED.

---

## Phase 6 — Tracker filing (Trello)

**Mandatory, with one documented degradation.** File the run's issues to
Trello **when a Trello MCP is available in the session**. As of this writing
one is **not yet connected** — in that case the issues digest in `report.md`
§2 *is* the record, Phase 6 is recorded as SKIPPED-with-reason ("no Trello MCP
available") in the final message, and nothing else changes. Do not invent a
substitute tracker, do not email anyone, and do not drop the digest.

### Board shape

- **Board:** `Arcana Regressions`.
- **One list per run**, named `Run YYYY-MM-DD` (the run date, matching the run
  folder). Create it at run start if the MCP is available then; otherwise
  create it here in Phase 6.
- **A board-level `Done` list**, which persists across runs. Cards move there
  when the issue is addressed — the run itself never moves a card to Done.

### One card per issue

Every §2 digest item becomes one card.

**Title** — a one-line overview, **≤80 characters**, prefixed with the primary
entry ID and a `·` separator:

```
PROFILE-22 · Edit Profile save blocked by optional fields
DEVSET-10 · Developer Settings keeps stale draft across reopen
SUITE · No 5xx fault-injection path — ERR-08/ERR-10 permanently BLOCKED
```
Use the family name (`SUITE`, `HAZARD`) as the prefix when no entry ID
applies, and say in the description which entry should exist.

**Description** — the §2 item, in this order:

1. Symptom
2. Root cause (or "not yet established")
3. User impact
4. Repro steps — concrete enough to follow without the run folder
5. Evidence paths — absolute paths to screenshots and the results log
6. Entry IDs and learning references
7. Proposed disposition

**Labels** — three groups, always:

| Label group | Values |
|---|---|
| Severity | `Low` / `Medium` / `High` — the run's argued severity. **Cole may re-label; a re-label is authoritative and a later run must not revert it.** |
| Category | `bug` / `ux-observation` / `suite-gap` / `hazard` |
| Platform | `ios26` / `ios18` / `android` / `all` |

**MCP constraint (verified 2026-08-11 against Trello's official MCP,
`https://mcp.trello.com/v1`):** the MCP can *attach* existing board labels
(`trelloWriteCard action=attach_label`) but **cannot create or rename
labels**. So: (1) read the board's labels first (`trelloReadBoard
action=list_labels`) and attach every label group that exists by name; (2)
severity ALWAYS gets attached — if no named severity labels exist, fall back
to the board's default color labels, **red = High, orange = Medium, yellow =
Low** (the convention the 2026-08-11 backfill established; Cole names those
three colors in the Trello UI); (3) any group without a matching board label
is carried instead as the card description's first line, in the fixed form
`**[category] · [platform] · severity: X**`, so nothing is lost and a later
run can attach real labels once they exist. The board's actual state is
`Arcana Regressions` (`https://trello.com/b/xfX4x4Vc/arcana-regressions`),
lists `Run YYYY-MM-DD` per run + `Done`. Card descriptions cap at 2048 chars
— trim Evidence/Refs first, never Symptom/Root cause/Impact/Repro/Disposition,
and end a trimmed description with a pointer to the run folder's report.

### Dedup — search before creating

**Before creating any card, search the board's open cards for the same primary
entry ID.** Most of these issues are long-lived; a suite that files
`PROFILE-22` afresh every run buries the board in duplicates and destroys any
signal about how long something has been broken.

- **Match found (card open, not in `Done`):** do **not** create a card.
  Annotate the existing card: `seen again in Run YYYY-MM-DD`, plus anything
  that changed (new device, new severity argument, new evidence path). Link
  the §2 item to that existing card.
- **Match found but the card is in `Done`:** the issue regressed. Create a new
  card, and annotate the Done card pointing at it.
- **No match:** create the card in this run's list.

**How to "annotate" (MCP constraint, verified 2026-08-11):** the Trello MCP
has **no comment action** on any of its write tools. Annotations therefore go
into the card **description**: `trelloReadCard` to fetch the current `desc`,
then `trelloWriteCard action=update` with the original text plus an appended
`---` + `**Run YYYY-MM-DD:** seen again …` line (respect the 2048-char cap:
trim the oldest Evidence/Refs material first, never the Symptom/Root
cause/Impact/Repro/Disposition core). Never overwrite Cole's own edits to a
description; append only.

### Close the loop in the report

**Each §2 digest item links to its card URL.** A digest item with no card URL
(and no "no Trello MCP available" note) means Phase 6 didn't finish — the run
is not complete.

Then, and only then, **deliver `report.md`** per Phase 4's "Delivery", and say
in the final message: the run mode and wall-clock, the verdict, which files
Phase 5.2 left edited-but-uncommitted (and on which branch), and either the
Trello list URL or the "no Trello MCP available" note. That message is the end
of the run.

---

## Quick reference — all pinned values from this doc's Phase 0 run (2026-08-11)

| Item | Value |
|---|---|
| Xcode | 26.6 (build 17F113) |
| iOS 26 device | iPhone 17 Pro Max — `4B113C10-9463-4128-AC7B-A89759328047` — iOS 26.3 |
| iOS 18 device | iPhone 16 Pro (18.5) — `96B93E6F-2F4A-40BA-A0C5-BB6C63E00C99` — iOS 18.5 |
| idb | `~/.arcana-tools/idb-venv/bin/idb` (venv; bare `idb` not on PATH) |
| **Android target** | **`Pixel_9_Pro` AVD — emulator only** (boot via `~/Library/Android/sdk/emulator/emulator`, not on PATH) |
| Android physical device | Pixel 9 Pro, serial `49141FDAP0000L` — **NOT used by this suite.** Spec non-goal ("simulator/emulator only"), and with no debug `applicationIdSuffix` an `installDebug` would collide with Cole's release-signed daily driver (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, or uninstalling his real app and session). Pin `adb -s emulator-<port>` when it is attached. |
| arcana-server venv | `../arcana-server/.venv/bin/activate` (NOT `venv/`) |
| arcana-server default settings | `arcana.settings.dev` (manage.py's own default, no env var needed) |
| Dev Postgres/Redis | docker-compose containers `arcana_postgres`/`arcana_redis`, already up |
| Port 8000 | commonly already held by a prior `manage.py runserver` — check before starting a new one |
| Ports 8001/8002 | parallel mode only — lanes ios18 and android; check all three ports in Phase 0.7 |

Re-run Phase 0's checks at the start of every actual regression pass — this
table is a snapshot from the day this runbook was written, not a live
source of truth.

## Quick reference — the run itself

| Item | Value |
|---|---|
| Phases | 0 Preflight · 1 Self-audit · 2 Environment · 3 Execution · 4 Report · **5 Triage & fold-back** · **6 Tracker filing**. 5 and 6 are mandatory; a run is not finished until 6. |
| Orchestration script | `tools/regression/full-regression.workflow.js` — args `{ runDate, mode: 'sequential' \| 'parallel' }` |
| Phase 1 script | `tools/regression/self_audit.sh` |
| Default mode | **sequential** (~6.8h). Parallel (~2.5–3h) is an explicit opt-in — see "Run modes". |
| Deliverable | ONE `~/arcana-regression-runs/YYYY-MM-DD/report.md`. No separate draft or full-table deliverable. |
| Report sections | §1 verdict + per-device table · §2 ISSUES DIGEST · §3 inventory drift · §4 suspect-driver · §5 appendix (collapsed, last) |
| Results log | `results-<device>.log`, one per device, in **both** modes — merged into `results.log` by Phase 4 (last line for a (device, entry) pair wins) |
| Repo writes | Only Phase 5.2's doc/inventory fold-back, left **uncommitted** on a branch. Never commit, push, or PR. |
| Tracker | Trello board `Arcana Regressions`, list `Run YYYY-MM-DD`, board-level `Done`. No MCP yet → report §2 is the record. |
