# Arcana Mobile — Full Regression Runbook

Execution guide for the agent-run full regression suite: an orchestrated set
of agent shifts that drive the real app — real network calls, real
simulators/emulator, real taps — through every entry in
`docs/regression/inventory.md` (237 entries at last count) on all three
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

**Read the entry's `Platforms:` field BEFORE driving it, not while writing the
result line.** An entry excluded on this device is a silent SKIP; attempting it
anyway and then recording whatever went wrong inflates the device's BLOCKED
count with entries that were never in scope. The 2026-08-27 iOS 26 lane
recorded all eight `Android-only` TEL entries (TEL-01…07, TEL-14) as BLOCKED
for a reason that could not apply to them, and the same shift's iOS 18
counterpart recorded them correctly as SKIP — the two lanes' numbers then
disagreed for no product reason.

**A BLOCKED whose reason is "no mechanism/technique exists" must name the
`driver-playbook.md` section it checked first.** "Exhausted every approach" is
not a reason, it is a summary — and on 2026-08-27 it wrongly blocked 34 applicable
iOS TEL/NAV/DEVSET/LAUNCH/SIGNUP/PLAT entries (42 log lines, 8 of them on
Android-only entries that should have been silent SKIPs) whose capture recipe
was sitting in the
playbook's "Telemetry echo" paragraph, and which that very shift had already
used earlier in its own pass. If the playbook has no section for it, say that;
that sentence is what turns a BLOCKED into a real, fixable gap.

**"I ran out of time" is not BLOCKED.** BLOCKED means an upstream entry failed
or the state is genuinely unreachable — never "not driven this shift due to
time," which 22 log lines across all three lanes said on 2026-08-27 (CLASS-02,
SCHED-02/06/09/10/13, SIGNUP-09/10, DEVSET-02, PROFILE-05/07/18, CONCIERGE-02,
PLAT-10 and others). Those entries are indistinguishable in the report from
entries the suite genuinely cannot reach, and they quietly convert a shift's
pacing problem into what reads as a product/fixture gap. An entry a shift did
not reach is left **unrecorded**; the shift says so in its return, and Phase 4
reports the unrecorded remainder as a run gap in the count reconciliation.

**A "no fixture / no token / no tool" BLOCKED is a factual claim, and this
suite has been wrong about all three.** Before writing one, check the thing:
`python3 -c "import PIL"` (PLAT-11 was blocked on both iOS lanes for a missing
Pillow that was installed, on the same Mac where the Android lane had just used
it), the token's own `consumed_at` (NAV-06/07/08/09, ERR-18, TEL-10/11,
SIGNUP-23/24 were blocked for "all four tokens consumed" while
`regression-ios26-spare` and every `*-conflict` token were unconsumed), and the
capture recipe (the telemetry cluster above). Cheap to check, and a false gap
costs the next run the same entries again.

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

**DEFERRED is not a general "come back to this later" marker.** It means one
of those two exclusive-fault classes and nothing else. "I ran out of an
unconsumed welcome token, try `claim_spare`" is not a DEFERRED — it is either
driven with the spare or recorded BLOCKED with that reason (the 2026-08-27 iOS
26 lane mislabelled SIGNUP-10 this way and had to correct it in-line). A
DEFERRED costs the tail agent a whole exclusive window; spending one on
something a lane could have driven itself is how the tail runs out of time.

Because the results log is append-only, the tail phase does **not** rewrite a
DEFERRED line. It **appends** a new line for the same (device, entry) carrying
the real outcome, and **the last line for a (device, entry) pair is
authoritative**. No DEFERRED status may survive into the report Phase 4
assembles.

**The tail phase sweeps EVERY lane's DEFERRED lines, and it derives them from
the logs, not from what a shift said it deferred.** The authoritative list is
`grep -h DEFERRED ~/arcana-regression-runs/<date>/results-*.log` re-run at the
start of the tail phase; a shift's self-reported `deferred_ids` is a hint that
can be short. On 2026-08-27 the tail drove only `ios26 CLASS-20` and never saw
`ios18 CLASS-20`, which sat in the ios18 log as a live DEFERRED all the way
into the finished report — the exact silent hole this rule exists to prevent.
Before the tail phase reports done, re-run that grep and confirm every line it
returns has a later, non-DEFERRED line for the same (device, entry).

**Cross-reference entries are a sanctioned shape, not a gap.** A few entries
exist only to point at another entry that already covers the same row, gesture
and file (today: PROFILE-12 → AUTH-11, DEVSET-01 → AUTH-13). Record such an
entry with the **referenced entry's status** and the notation `see <ID>` (e.g.
`PROFILE-12: PASS (see AUTH-11)`) — never drive it separately, and never record
it as SKIP.

---

## Work lists and tiers — what a shift actually reads

**No driving shift reads `inventory.md`.** Phase 2 extracts it once into
per-device work lists and the shifts read those:

```
python3 tools/regression/build_worklist.py --out ~/arcana-regression-runs/<date> [--tier 1]
```
This writes `worklist-{ios26,ios18,android}.tsv` (plus `worklist-summary.txt`)
— one row per **applicable** entry, in inventory order, tab-separated:
`id, tier, platforms, title, steps, expected, source`. A shift gets its ID list
with `cut -f1`, and pulls one entry at a time with
`awk -F'\t' '$1=="CLASS-08"'`. The `Platforms:` filtering happens here, once,
which also removes the class of error where a lane attempted an `Android-only`
entry on iOS and recorded it BLOCKED.

Why: re-reading runbook + playbook + inventory at the top of every shift was the
single largest cost of the 2026-08-27 run — roughly 150k tokens per shift across
~15 shifts, most of a 6M-token bill spent re-reading prose that had not changed,
and the reason that run needed three attempts to finish. Open `inventory.md`
only when a row looks wrong or truncated, and record a learning when you do.

**Tiers.** `--tier 1` is the release-blocking core (~111 entries per device):
auth, onboarding, schedule, class detail, booking, cancel, session teardown and
the transport-failure handling this suite exists to protect. Tier 2 is
everything else. The split is defined in `build_worklist.py` (`TIER1_AREAS` /
`TIER1_IDS`) so it is auditable and reviewable rather than a judgement made per
run; change it there, in a PR, not in a run.

| Want | Command |
|---|---|
| Full pre-release pass | no `--tier` (all 237) |
| Smoke check after a dependency bump | `--tier 1`, and pass `tier: 1` to the workflow |

A tier-filtered run is **not** a full regression and must say so in its report's
§1 verdict line. Entries outside the tier are absent from the run, not SKIP.

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

**The intended use for parallel is the fast gut-check**, not the pre-release
pass: pair it with `--tier 1` after a major dependency bump (a CMP or Kotlin
upgrade, a Ktor bump) to answer "is the app still fundamentally working on all
three devices" in about an hour rather than overnight. The overnight
full-inventory pass stays sequential — nobody is waiting on it, and sequential
has the simplest failure story.

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

**Write the entry ID exactly as the inventory spells it.** The ID column is
matched by exact string in the resume ID-diff, the DEFERRED sweep and Phase
4's merge, so a variant spelling is invisible to all three. On 2026-08-27 an
Android line recorded `CLASS-14b` for what the inventory calls `CLASS-14(b)`;
harmless there only because the base `CLASS-14` line existed too. Sub-parts go
in the notes column (`CLASS-14  PASS  (b) …`), not in the ID.

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

**Better than reusing another session's server: take your own port.** On
2026-08-27 port 8000 was held by a live session's runserver on the shared
checkout, so the run started its own on **8010** from a `git worktree` at
`/tmp/regression-server` and pointed all three devices at it. That is now the
preferred shape whenever 8000 is busy — it gives the run its own process,
its own `.env` (so the null-notifier check is this run's to make, not a
verdict on someone else's process) and its own log, at the cost of one
`git worktree add`. **It does not isolate the database**: the worktree still
talks to the same dev Postgres, so every DB-level rule in this doc (the
deferred tail, the shared-row mutations) applies unchanged. When you do this,
carry the port into Phase 2.1's health check, Phase 2.5's per-device override
(`http://localhost:<port>` / `http://10.0.2.2:<port>`) and every
`$BASE/api/v1/_faults/` call — the playbook's examples say 8000 and are
examples, not the pinned value.

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

For every **Source:** path listed across all 237 inventory entries, verify
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
should yield **94 unique paths from 534 comma-split tokens across 237 Source
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

**Serve from an isolated worktree, never the shared checkout.** Other sessions
edit `arcana-server` during the day; an autoreload mid-run restarts the server
and changes the code under test underneath a driving shift. Create it once, at
run start:

```
cd ~/Desktop/arcana/arcana-server && git fetch origin
git worktree add --detach /tmp/regression-server origin/main
ln -sfn ~/Desktop/arcana/arcana-server/.env /tmp/regression-server/.env
```
Serve from `/tmp/regression-server` using the shared checkout's venv by absolute
path (`~/Desktop/arcana/arcana-server/.venv/bin/python`) — the worktree has no
venv of its own, and the dev Postgres is the same, so `seed_regression` works
normally. **Pass `--noreload`**: it gives exactly one PID to track and kill, and
stops an armed fault being silently cleared by a reload. Remove the worktree in
Phase 4 cleanup (`git worktree remove --force /tmp/regression-server && git
worktree prune`), and confirm the shared checkout is still clean afterwards.

**Port 8000 is usually held by another session's runserver out of the shared
checkout.** It is neither yours to kill nor yours to reuse — reusing it defeats
the worktree entirely. Pick a free port (8010 works), point every device's
override at it, and leave 8000 alone.


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

**Manifest shape (verified against the real command output, 2026-08-27):**
```json
{
  "accounts": {
    "ios26": {
      "claim":                 {"email": "...", "welcome_token": "..."},
      "claim_spare":           {"email": "...", "welcome_token": "..."},
      "claim_used":            {"email": "...", "welcome_token": "..."},
      "claim_conflict":        {"email": "...", "welcome_token": "..."},
      "member":                {"email": "...", "password": "...", "member_number": "9001"},
      "member_no_favorites":   {"email": "...", "password": "...", "member_number": "..."},
      "member_no_credits":     {"email": "...", "password": "...", "member_number": "..."},
      "member_no_membership":  {"email": "...", "password": "...", "member_number": "..."},
      "member_outside_window": {"email": "...", "password": "...", "member_number": "..."}
    },
    "ios18":  { ...same shape... },
    "android":{ ...same shape... }
  },
  "classes": {
    "full": <session id>,
    "blocked": <session id>,
    "late_cancel": <session id>,
    "late_cancel_active": <session id>,
    "window_gated": <session id>,
    "cancelled": <session id>,
    "spot_preference": <session id>,
    "hidden_capacity": <session id>,
    "outside_window": <session id>
  }
}
```

**`member_number` is reported, not assumed.** It is unique across every
membership, including local fixtures the command does not own and never purges
(`seed_lapsed_member` already holds 9101). The seed steps past a taken number
rather than aborting, so read the value from the manifest before asserting on
the member card.
Read the manifest for every account/session id used in Phase 3 — **never
hardcode an id or token**, they are freshly generated on every run
(including the claim tokens — a re-seed always hands you a fresh, unconsumed
`welcome_token`, invalidating any previously-noted one).

**Which fixture feeds which entry.** Each of these entries names its manifest
key in its own Steps line; this table is the index, so a driver can tell at a
glance what a given fixture is for and what breaks if the seed didn't produce
it. Every session fixture lives on one of the three synthetic `regression-*`
studios (`platform='fake'`), **not** on any real synced studio.

| Manifest key | What it is | Entries that consume it |
|---|---|---|
| `classes.full` | `availability='full'`, 20/20 booked, +2 days 12:00 ET, Regression Test Studio | SCHED-14, CLASS-06 |
| `classes.blocked` | `availability='blocked'`, +2 days 13:00 ET — must never surface | SCHED-19 |
| `classes.window_gated` | `bookable_at` = now + 2 days, +6 days 07:00 ET | SCHED-15, CLASS-05 |
| `classes.late_cancel` | Regression Late Cancel Studio (24h window), +3 days 18:00 ET, freely cancellable | CLASS-19 |
| `classes.late_cancel_active` | Same 24h studio, ~12h out — **inside** the cutoff; the only forfeit-reachable fixture (needs the Phase 3 fulfilment step) | CLASS-18, CLASS-20, CLASS-22 |
| `accounts.<device>.claim` | `awaiting_signup` membership + one unconsumed `welcome_token` | SIGNUP-01 … SIGNUP-23 (the whole welcome-deep-link → survey → claim flow) |
| `accounts.<device>.member` | Activated member (8 credits) + **one confirmed upcoming booking** + **two favorites** (studio-grain + location-grain) | Every authenticated entry logs in with it (AUTH-02 onward). The seeded booking backs SCHED-16, CLASS-16, CLASS-18, HOME-12; the seeded favorites back SCHED-01, SCHED-11, FAV-01, FAV-02 |
| `classes.cancelled` | `status='cancelled_by_studio'`, +4 days 12:00 ET | CLASS-04 |
| `classes.spot_preference` | Preference dropdown, **no** real spot selection, +4 days 17:00 ET, Regression Test Studio | CLASS-11 |
| `classes.hidden_capacity` | Regression Hidden Capacity Studio (`publishes_capacity=false`), 17/20 booked, +2 days 09:00 ET | SCHED-17 |
| `classes.outside_window` | +13 days 11:00 ET — past `member_outside_window`'s wallet but inside the browse horizon | CLASS-15 |
| `accounts.<device>.claim_spare` | A SECOND unconsumed `welcome_token`, deliberately left alone by the SIGNUP block | ERR-18, ERR-19, NAV-06, NAV-07, NAV-09, TEL-10, TEL-11 |
| `accounts.<device>.claim_used` | A token already consumed → 410 `token_invalid_or_expired` | SIGNUP-19 |
| `accounts.<device>.claim_conflict` | Unconsumed token whose email ALREADY has a User → 409 `account_exists` | SIGNUP-20 |
| `accounts.<device>.member_no_favorites` | Activated member, credited, **zero favorites** | SCHED-12, FAV-04, and the second login FAV-06 needs |
| `accounts.<device>.member_no_credits` | Covering wallet granting 0 credits | CLASS-13 |
| `accounts.<device>.member_no_membership` | Wallet whose window has fully elapsed; browse stays open | CLASS-14 (a) |
| `accounts.<device>.member_outside_window` | Active wallet ending +5 days, so near classes book but `classes.outside_window` does not | CLASS-15 |

**Order matters for the claim accounts.** `claim` is the one the SIGNUP block
consumes; `claim_spare` exists because that consumption is what left every
later fresh-token entry BLOCKED in the 2026-08-11 run. Do not spend the spare
on a SIGNUP entry.

**Four tokens per device is the whole budget, and the SIGNUP block can eat all
four.** The 2026-08-27 iOS 18 lane consumed `claim`, `claim_spare`,
`claim_used` and `claim_conflict` inside the SIGNUP block and then had nothing
left for NAV-06, SIGNUP-23 and every other post-SIGNUP entry needing an
unconsumed token — a self-inflicted BLOCKED cluster, not a fixture gap. Two
ways out, and the shift must pick one before it starts SIGNUP: drive the
token-hungry NAV/ERR/TEL entries (ERR-18, ERR-19, NAV-06/07/09, TEL-10/11)
BEFORE the successful claim submit, or treat `claim_used` and `claim_conflict`
as reserved for SIGNUP-19/SIGNUP-20 alone and never as generic spares. If a
future seed can mint a fifth token per device, reserve it for post-SIGNUP use.

**CLASS-07 stays BLOCKED and is not a seed gap.** Schedule never surfaces an
already-past session and `DeepLinkHandler` handles only the welcome-token
scheme, so a past-dated fixture would exist with no way to navigate to it.
Reaching it needs a class deep link in the app, not a seed change.

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

### Known hazards — telemetry pollution (CLOSED 2026-08-22)

**PostHog: closed.** `TelemetryGate` initializes PostHog only when
`!isDebugBuild && environment == prod`, so a Debug build pointed at localhost
sends nothing. Covered by TEL-22/TEL-23; the Debug console echo still fires, and
is what the TEL entries are driven from. **Sentry: still reports from every
build, by design** — dev and regression crashes are wanted, tagged
`environment=local-debug`. Historical detail (the 2026-08-11 leak of 3,030 local
events into prod project 439926) lives in
`~/arcana-regression-runs/2026-08-11/telemetry-leak.md`; it is closed and does
not need re-reading before a run.

### Known-BLOCKED entries — verify the row before you trust it

**This table has been wrong more often than it has been right, and it is the
most dangerous doc in the suite because it tells a driver NOT to try.** On
2026-08-27, seven of its rows named entries that in fact PASS on all three
devices (ERR-08, ERR-10, SIGNUP-20, SCHED-12, SCHED-17, PROFILE-14, AUTH-16);
they had been unblocked by seed and fault-injection work and nobody removed the
rows. Those seven are now deleted.

**The rule: a row here is a hypothesis, not a verdict.** Attempt the entry
first. Only if your own attempt reproduces the stated reason do you record
BLOCKED — and if it does not, drive the entry and delete the row in Phase 5.2.
Never record BLOCKED by citing this table alone. A "no fixture / no token / no
lever" claim is a factual claim and is cheap to test: check the fixture in the
manifest, check the token's `consumed_at`, try the fault injector.

What remains below is the set still believed genuinely blocked; each needs a
seed/server/app change rather than a driving workaround:

**First, two things that are NOT blocks and must stop being recorded as such
(both cost the 2026-08-27 run real coverage and real credibility):**

1. **An entry your device's `Platforms:` field excludes is a silent SKIP, never
   a BLOCKED.** Check the field BEFORE driving, not while writing the result
   line. On 2026-08-27 the ios26 lane attempted the eight `Android-only` TEL
   entries (TEL-01…07, TEL-14) and recorded them BLOCKED with telemetry
   reasoning, while ios18 recorded the same eight correctly as
   `SKIP  Android-only`. That single mistake accounts for 8 of the 20-entry gap
   between the two iOS devices' BLOCKED counts and made iOS coverage look worse
   than it was.
2. **An entry with an out-of-scope leg is PASSed on the legs you CAN drive, and
   the out-of-scope leg is named — not recorded BLOCKED.** TEL-22 and TEL-23 both
   have a leg requiring a release build pointed at `https://api.arcana.fit`,
   which this suite's safety rules forbid outright and always will. Their
   in-scope negative legs (dev/debug traffic must NOT reach PostHog; Sentry must
   still initialize) are drivable, are the halves that actually protect
   production analytics, and are cheap — on a Debug build the first echoed
   telemetry line is literally `PostHog DISABLED (environment=local).` Retiring
   these entries was considered on 2026-08-27 and rejected for exactly that
   reason: the drivable half is the valuable half. See each entry's own Scope
   note in `inventory.md`.

| Entry | Why it's blocked | Notes |
|---|---|---|
| ERR-18 / ERR-19 (claim-form / survey submit-failure paths) | **Not a fixture gap — a sequencing one, and "the token is used up" is usually false.** Only a successful `complete_signup` sets `SignupToken.consumed_at`; abandoning the survey, a failed submit, a 409 and a 410 all leave the token live. What actually gets used up is the device-local `signup_survey_done:<token>` key (see driver-playbook.md). | Drive these two BEFORE the successful claim submit (Phase 3's SIGNUP ordering note). If a shift believes it is out of tokens, check `consumed_at` before recording BLOCKED — on 2026-08-27 every device still held at least one unconsumed token while both iOS lanes were blocking entries for exhaustion. |
| AUTH-12 (forced logout on refresh-token failure) | No server-side lever to revoke just the refresh token — `SIMPLE_JWT.BLACKLIST_AFTER_ROTATION=False` and no `token_blacklist` app installed. Clearing Keychain/EncryptedSharedPreferences externally nukes the *whole* session (both are fully encrypted per-entry — iOS Keychain, Android Tink AES-GCM — so there's no way to tamper with just the refresh token). | An honest repro needs a client-storage clear followed by waiting out the real access-token TTL (300s in this seed's `SIMPLE_JWT` config) before the next authenticated request — 5+ minutes, non-parallelizable with other driving on that device. **Also:** clearing secure prefs externally also wipes the Developer Settings override (`BaseUrlProvider` persists into the same file) — re-open Developer Settings and confirm "CURRENTLY IN USE" before assuming a resulting login failure is an app bug rather than a reverted-to-prod base URL. |
| CLASS-11 (spot-preference dropdown, non-spot future session), CLASS-13/14/15 (out-of-credits / no-active-membership / outside-membership-window) | None of these session/account states exist in the regression manifest or a reachable dev-DB row. | Needs `seed_regression` fixture additions, not a driving-technique fix. |
| CLASS-04 (cancelled_by_studio), CLASS-07 (past-dated session) | **Not a fixture gap any more — a navigation gap.** `seed_regression` now seeds the cancelled session (`classes.cancelled`; verified live 2026-08-28, session 66469 returns `status: "cancelled_by_studio"` from `GET /api/v1/classes/66469/`). What's missing is any in-app route to it: `classes/views.py`'s `base_class_session_queryset` filters `status='scheduled'` for the LIST endpoint and passes `include_cancelled=True` only for the detail endpoint, and the seeded member holds no booking on that session, so My Bookings can't reach it either. CLASS-07 is the same shape and is accepted-BLOCKED for it — there is no deep link to an arbitrary class id (`DeepLinkHandler.kt` handles only the welcome-token scheme). | CLASS-04 is fixable without app code: have `seed_regression` book the member onto the cancelled session and leave it cancelled, so My Bookings gives a path (unverified that a cancelled session's booking renders there — check before relying on it). CLASS-07 needs a class deep link in the app and stays accepted-BLOCKED until one exists. |
| CLASS-09 / CLASS-26 (grid spot selection) | Every studio in the regression fixture set has `spot_selection_mode='none'`. | Needs a `seed_regression` studio with `spot_selection_mode='grid'`. See the grid-spot fixture trap immediately below this table before hunting for a real one. |
| PROFILE-03 / HOME-18 (next-period wallet), HOME-15 (nonzero week streak) | No seeded account holds an upcoming/next-period wallet while inside the current month, and the week streak is server-computed from real attendance history with no settable fixture field. | Needs two `seed_regression` accounts: one with a next-period wallet, one with several weeks of attendance. |
| DEVSET-08 (Reset to default) | Not a fixture gap — a **prod-contact hazard**: the default it restores is `https://api.arcana.fit`. | Drivable under the ordering constraint in driver-playbook.md's Safety section (reset → verify from the UI → immediately re-set the local override, with no request-issuing tap in between). Record BLOCKED with that reason if the ordering can't be held. |
| AUTH-15 (soft-keyboard CTA reachability, iOS) | The software keyboard never renders while driving with `idb`, which delivers HID keystrokes the simulator treats as a hardware keyboard. | A technique gap, not a fixture gap — see the iOS trap in driver-playbook.md for the two untried options (disable Connect Hardware Keyboard, or pin an SE-class device). |
| TEL-22 (analytics gate) / TEL-23 (Sentry environment) | **Structural, permanent for this suite.** Both entries' remaining leg needs a RELEASE build talking to real PostHog/Sentry with the base URL at `https://api.arcana.fit`, which Safety rule 0 forbids. All three lanes reached that conclusion independently on 2026-08-27. | Half of TEL-22 *is* drivable and should be recorded rather than blocked: a Debug build on a local base URL prints `D/Telemetry: PostHog DISABLED (environment=local)` in the console echo (observed on the pinned iOS 26 sim, 2026-08-28). The release/prod leg is covered by `sharedLogic/src/commonTest/.../analytics/TelemetryGateTest.kt`; verify it by reading that test, not by driving. |
| LAUNCH-04 (first-launch recovery grace) | **An observability gap, not a fixture one.** `AppSessionController.attemptFirstLaunchRecovery()` emits no telemetry at all (the file references `Telemetry` nowhere), and `RECOVERY_ATTEMPTED_KEY` is one-shot per install, so the 700ms grace and the once-per-install marker have no black-box signal. | The outer behavior — a plain icon launch lands on Auth with no spurious routing, on repeated cold starts — is observable and is what the Android lane recorded as PASS on 2026-08-27. Record that half and say which half you proved; the timing half needs instrumentation, i.e. app work, before it is scoreable. |
| HOME-11 (upcoming preview list, 4-row cap + day dividers) | The entry's Steps want 6+ upcoming bookings across multiple days; `seed_regression` gives the member one, and a shift can realistically add one more by booking in-app. | A 2-booking pass proves the day-header divider and nothing about `UPCOMING_PREVIEW_COUNT = 4` or the hairline-suppression rules — both lanes that PASSed it on 2026-08-27 said so in their result line, which is the right way to record it. Fixing it properly is a `seed_regression` change: six bookings spanning three days on one device's member. |

**Two more that are never to be added to this list.** **PLAT-11** — the
optical-centring measurement — is not blocked by a missing Pillow; it is
installed on this Mac and the Android lane used it on 2026-08-27 while both
iOS lanes blocked the entry for its absence. And **the welcome-token cluster**
(NAV-06/07/08/09, ERR-18, TEL-10/11, SIGNUP-23/24) is not a token shortage;
see the ERR-18/19 row above and the token trap in driver-playbook.md.

**Not on this list, and never to be added to it: the iOS telemetry console.**
`xcrun simctl launch --console-pty <UDID> org.arcana.mobile` reaches the
`D/Telemetry: ▶ …` echo on both pinned simulators (re-verified 2026-08-28).
Any TEL/NAV/DEVSET/LAUNCH/SIGNUP entry blocked for "no way to capture
telemetry on iOS" is a driver artifact, not a gap — see driver-playbook.md's
"Telemetry echo".

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

**Within a device, the default account is `accounts.<device>.member`, and an
entry that signs in as one of the edge accounts must sign back out to it.**
`member_no_credits`, `member_no_membership`, `member_no_favorites` and
`member_outside_window` exist for a handful of entries each; leaving one signed
in silently re-points every later entry at the wrong wallet. On 2026-08-27 a
later shift found the Android emulator sitting on `member_no_membership`
(No. 9303) with no record of which entry had left it there — caught only
because that shift checked Profile before it started. **Check the member number
on Profile at shift start, and after any entry that switches accounts, before
trusting anything credit- or booking-shaped.**

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

**That filter is scoped to the SESSION, and every device books the same
session, so one run of it fulfils all three lanes' bookings.** The account
sets are per-device but `classes.late_cancel_active` is one shared row. On
2026-08-27 the tail agent's second run matched 0 rows because the first had
already confirmed the iOS 18 member's booking too — which is convenient (one
mutation serves every lane) but only if the tail knows it: after running the
UPDATE once, the remaining work per device is the in-app verification and the
result line, not another mutation. Add `id=<booking id>` to the filter if you
deliberately want to fulfil one lane's booking and leave the others REQUESTED.

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
- **CLASS-20's forfeit warning is amber, and that is the Expected.** The
  cancel sheet renders the forfeit line in `Warning` and the refund line
  ("You'll get your credit back.") in `Moss` —
  `ClassDetailScreen.kt`'s `CancelBookingSheet`, `willForfeitCredit` branch.
  Two lanes independently remarked on the amber as if a Moss variant were
  missing; it isn't, the Moss copy is the *other* branch of the same `if`.
  Record PASS and move on.
- **SIGNUP-06's selected survey chip is Moss, not Burnt Nectar.**
  `SignupSurveyScreen.kt`'s `SurveyOptionChip` fills the selected state with
  `Moss` and the label with `Stone`, matching `BookingSheet.kt`'s `VisitChip`.
  Its own KDoc still says "Burnt Nectar when selected" — the comment is wrong,
  the code is right, and the inventory Expected was corrected to match on
  2026-08-28. Don't re-file it off the comment.
- **"{N} classes remaining." on its own is HOME-16, not HOME-15.** HOME-15
  needs the *second* line ("{N}-week streak. Keep it going."), which only
  renders for a nonzero `weekStreak` — and no seeded account has one. A lane
  PASSed HOME-15 on 2026-08-27 quoting only the credits line; that is the
  zero-streak branch of the same card and proves HOME-16. Record HOME-15
  BLOCKED against the streak fixture gap unless the streak line is actually
  on screen.
- **The iOS tab bar is HIDDEN while a non-tab destination is pushed**
  (`ArcanaShell.swift`'s `refreshTabBarVisibility`, driven by TabRoots'
  `onRootChanged`) — this is NAV-04's assertion and it is deliberate. It also
  means "push a detail, then tap another tab" is not a gesture that exists on
  iOS; see NAV-03's corrected Steps rather than recording the missing tab bar
  as a defect.

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

**The per-device logs are the only ground truth for the numbers.** Every count
in §1 — recorded, PASS, FAIL, BLOCKED, SKIP, applicable — is recomputed from
`results-<device>.log` (last line per (device, entry) wins) against the
inventory's `Platforms:` fields. Whatever per-device tallies the orchestration
hands the report agent are a cross-check to be *reconciled and reported on*,
never a source. On 2026-08-27 those tallies were wrong on all three devices and
internally impossible on two (one device's claimed PASS count exceeded the
whole inventory; another's claimed "recorded" was smaller than its own
pass+blocked+skip) because they sum each shift's self-reported numbers and
resumed shifts re-count the entries they re-walked. The report caught it only
because it recomputed. Disagreement between the two is itself a §2 finding.

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
per-device verdict. Applicable counts on today's inventory (230 entries: 202
`shared`, 11 `iOS-only`, 1 `iOS26-only`, 1 `iOS18-only`, 15 `Android-only`)
are **214** for iOS 26, **214** for iOS 18, **217** for Android — recompute
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
per-item results (230 entries × 3 devices)</summary>` block so it never
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

**A second Phase 4 over the same run folder overwrites Phase 5's work — check
before you write.** `report.md` is rewritten from scratch, so re-dispatching a
run that already reached Phase 5 (a resumed workflow, a re-invocation for the
same run date) silently drops the adjudicated §2/§4 and the appended
`## Phase 5` section, while the doc fold-back survives because it lives in the
repo. That happened on 2026-08-27: the checked-in docs carried the TEL-cluster,
ERR-16 and CLASS-04 adjudications hours before a re-run's `report.md` was
written still listing all three as open. **If `report.md` already contains a
`## Phase 5` heading, copy it to `report-preadjudication-<HHMM>.md` before
overwriting and tell Phase 5 it exists**, so the second adjudication starts
from the first rather than from the raw logs.

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

No exceptions, no sampling. **§6.0's filing bar applies to every verdict below
that produces a card, including findings raised by code review rather than by
driving.** Each one resolves to exactly one of:

| Verdict | Meaning | What follows |
|---|---|---|
| **APP BUG** | The code genuinely does the wrong thing. | Goes to §2 "Confirmed app bugs" and gets filed in Phase 6. |
| **INVENTORY-EXPECTED-WRONG** | The app behaves correctly; the inventory entry's **Expected** describes something the code never did (or no longer does). | The entry is corrected in Phase 5.2. Usually also worth an §2 "Potential issues" item if the real behavior is itself a rough edge. |
| **DRIVER ARTIFACT** | The failure was manufactured by how it was driven, not by the app. | Goes to §4 with the mechanism named, and the trap goes into driver-playbook.md in 5.2. |
| **UNVERIFIED CLAIM** | The symptom is real but the stated cause was inferred, not measured — or the symptom itself was only ever read out of the code. | Filed as `needs-triage`, never `bug`. Must satisfy §6.0's filing bar before it can be relabelled. Three Run 2026-08-16 cards belonged here and were labelled `bug`; all three were later closed as invalid. |
| **FIXTURE/ENVIRONMENT GAP** | Nothing is wrong with the app, the entry or the driving — the state the entry needs cannot be reached from the current seed/server at all. Usually surfaces as a BLOCKED rather than a FAIL. | Goes to §2 "Suite & tooling gaps" and is filed in Phase 6 as a `suite-gap` card; 5.2 adds or refreshes its row in Phase 2.2's Known-BLOCKED table with the real reason. Never filed as an app bug. |

**Also sweep the run's BLOCKEDs** — not to re-adjudicate the ones Phase 2.2's
Known-BLOCKED table already explains, but to catch any *new* one, which is a
FIXTURE/ENVIRONMENT GAP and needs its own row there plus a §2 "Suite & tooling
gaps" item. Two cheap cross-checks pay for themselves here, and both fired on
2026-08-27: **an entry BLOCKED on one device and PASSed on another is almost
never a fixture gap** (57 of that run's 102 BLOCKEDs were in this shape, and
the ones that mattered — PLAT-11, the token cluster, the telemetry cluster,
SCHED-13/17/19 — were all driver artifacts), and **a "no fixture / no tool /
no token" reason is a factual claim you can test in a minute** rather than
inherit.

**And sweep the PASSes whose result line doesn't actually satisfy the
Expected.** A wrong PASS is more expensive than a wrong BLOCKED, because
nothing downstream re-examines it. Read each result line against the entry's
Expected wherever the line quotes what it saw: HOME-15 was PASSed on the
strength of "7 CLASSES REMAINING." alone, which is the zero-streak branch and
therefore HOME-16, while HOME-15's streak line has no fixture that can produce
it. Correct such a line's status in the report (with the reason) — the log
itself stays append-only.

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

**These edits are left as an UNCOMMITTED working-tree diff, on whatever branch
the checkout is already on, for Cole's review. The run never commits them,
never pushes, never opens a PR — and never creates or switches a branch
either.** Other sessions drive these same checkouts, so a `git checkout -b`
here moves the tree under them; leave the branch exactly as found (usually
`main`). This is the single sanctioned exception to "outputs never enter git"
— and it is only an exception in that it *touches* the repo. Report in the
final message which files were edited and roughly what changed, so the diff is
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

### 6.0 — The filing bar (applies to EVERY card, whatever its origin)

Added 2026-08-24 after **three of the seven Run 2026-08-16 cards were closed
as invalid** on re-verification: the Android soft-keyboard CTA, the iOS
`socketTimeoutMillis` claim, and the `IconCircle` tap target. All three named a
confident root cause, two of them under a bold **Root cause:** heading. None
survived a measurement that took under an hour. Each one then cost a full
reproduce-and-disprove cycle to close, which is more expensive than the fix
would have been had the bug been real.

**The common thread: none of the three came from Phase 3 driving.** They were
raised by code review on a feature branch and filed straight as `[bug]` with a
severity, bypassing the FAIL → §5.1 adjudication pipeline entirely. The rigor in
this runbook only binds entries that were driven. That is the hole.

**A card may be filed only when all five hold.** No exceptions for "obvious from
the code", and no exceptions for review-sourced findings.

1. **The symptom was OBSERVED on a running build**, not inferred from reading
   code. Reading `.size(d.dp).clickable(...)` and concluding the touch target is
   `d` is an inference. Tapping at increasing offsets until it stops responding
   is an observation, and it returned 48dp.
2. **The stated cause was CHECKED, not guessed.** If the card claims the code
   lacks something, grep for it. If it names a library's behavior, open that
   library's source — both engine claims here were falsified by one `grep` in an
   already-downloaded sources jar. A cause you have not verified goes under
   **Suspected cause** and the card is `[needs-triage]`, never `[bug]`. If you
   catch yourself typing "to confirm", you are writing a Suspected cause.
3. **A negative control was run.** Either apply the proposed fix and confirm the
   behavior changes, or confirm the current code genuinely misbehaves. The
   `IconCircle` fix was built and measured at exactly the same 48dp as the
   unmodified control — a five-minute check that would have killed the card.
4. **The tool was ruled out as the cause.** Any silent non-response to a
   synthetic tap is a driver artifact until proven otherwise: `uiautomator` and
   `android layout` do not report the soft keyboard, so both hand you
   coordinates the IME is covering. See the Android traps in
   `driver-playbook.md`. This is §5.1's DRIVER ARTIFACT verdict; apply it to
   review-sourced findings too.
5. **Every quantity in the card was re-read from the source at filing time.**
   The `socketTimeoutMillis` card warned about a 90s booking override that has
   never existed in the code — it lived only as an unchecked step in a plan doc.
   Numbers copied from a plan, a comment, or an earlier draft are not evidence.

**Comments are not evidence.** The `IconCircle` card traces to a stale code
comment asserting the clickable box equals the visual diameter. When a comment
is your source, verify the claim and then fix the comment: an uncorrected one
regenerates the same card next run.

**Prefer the cheap disproof first.** For each of the three, the disproof was
cheaper than the write-up: one swipe, one grep of the Ktor sources, one tap
probe. Spend that before writing the card, not after someone else picks it up.

**Filing a valid observation with a wrong cause is still a bad card.** It sends
the fixer down a specific path. `[needs-triage]` with the raw measurement and no
cause is strictly more useful than a confident wrong mechanism.

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
| Category | `bug` / `needs-triage` / `ux-observation` / `suite-gap` / `hazard` |

`bug` asserts the app is wrong AND that you verified why. A real observation
whose cause you have not confirmed is `needs-triage` — see §6.0. If the board
has no `needs-triage` label, attach `bug` and open the description with
**SUSPECTED CAUSE, NOT VERIFIED**.
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
