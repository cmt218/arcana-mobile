# Agent-Run Full Regression Suite — Design

**Date:** 2026-08-11
**Status:** Approved by Cole (this doc is the written record of the approved design)
**Repos touched:** arcana-mobile (suite artifacts), arcana-server (seed command)

## Purpose

A checked-in, agent-executable regression suite that verifies every user-facing
surface of the Arcana mobile app across the full supported device matrix. It
replaces the human cognitive load of exhaustive pre-release manual testing: Cole
runs it overnight on his Mac once per release (after his own manual pass, as due
diligence), and wakes up to an evidence-backed report.

This is **agent-operated, not CI**. It requires this Mac (Xcode + simulators,
idb, the Android emulator, a local arcana-server checkout) and several hours of
agent time. That trade is deliberate: an agent adapts to what is actually on
screen, which is what made this approach work in the 2026-08 shell regression
and what scripted UI suites chronically fail at.

## Decisions (settled with Cole, 2026-08-11)

1. **Full-only, no tiers.** One exhaustive run. Not intended for PRs; run
   before releases. (A smoke tier can be added later if wanted; explicitly out
   of scope now.)
2. **Never halt on failure.** A failing check is recorded (with evidence) and
   the run moves on. Downstream checks that depend on the failure are BLOCKED,
   not FAIL. Inventory drift found by the self-audit is likewise a report
   finding, never a run-stopper.
3. **Run outputs stay out of git.** Reports + screenshots land in a dated local
   folder; the repo carries only the suite itself.
4. **Everything on all three devices.** iOS 26 simulator, iOS 18 simulator,
   and the Android emulator each execute the complete inventory. The only
   exceptions are entries explicitly marked platform-only (e.g. Android back
   button, iOS shell chrome).
5. **Structure = layered docs + a repo skill** (option B below), separating
   fast-changing content (the inventory) from stable content (tooling).

## Architecture

### arcana-mobile artifacts

Four checked-in pieces, separated by rate of change:

**1. `docs/regression/inventory.md` — the feature-surface source of truth.**
Organized by area: Launch & Session, Auth, Signup & Claim, Home, Schedule &
Filters, Class Details & Booking, Favorites, Profile & Edit Profile, Developer
Settings, Navigation & Shell, Error States, Telemetry, Platform-Specific.

Each entry:

```
### SCHED-04 — Modality category filter
- **Steps:** From Schedule, open the filter row, select a category chip, …
- **Expected:** List narrows to classes in that category; chip shows selected
  state; clearing restores the full list.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/…
- **Platforms:** shared
```

- IDs are stable (never renumbered; deleted entries leave a tombstone line so
  IDs are never reused).
- Behavior-level expectations, not pixel-level ("shows CLASS FULL and no BOOK
  button", not screenshot matching).
- The **Source** references are load-bearing: they power the self-audit.

**2. `docs/regression/runbook.md` — how a run executes.** Phases:

- **Phase 0 — Preflight.** Verify machine prerequisites: Xcode with iOS 26.x
  and iOS 18.5 simulator runtimes, patched idb, the Android AVD, an
  arcana-server checkout with its dev database, required ports free. Missing
  prerequisites are reported and their checks SKIPped, not fatal where partial
  progress is possible.
- **Phase 1 — Self-audit.** Diff inventory ↔ code (mechanics below). Drift in
  either direction becomes top-of-report findings.
- **Phase 2 — Environment.** Start local arcana-server; run
  `manage.py seed_regression`; capture its manifest; build Debug apps and
  install on all three targets (Debug so Developer Settings can point the app
  at the local server: `localhost` for iOS sims, `10.0.2.2` for the emulator —
  the debug network-security config already permits cleartext).
- **Phase 3 — Execution.** One device at a time — iOS 26 → iOS 18 → Android —
  full inventory each, using that device's own seeded account set.
- **Phase 4 — Report.** Assemble and deliver (format below).

**3. `docs/regression/driver-playbook.md` — stable device-driving knowledge.**
The hard-won techniques from the 2026-08 sessions, so no future run re-learns
them: the idb dump→act→verify loop; one verified action per UI dump (layouts
shift); sweep to scroll-top before searching a list; the URL/text-field
pre-fill trap (clear before typing); taps within 4pt of a screen edge become OS
edge gestures; submit buttons can sit below the fold (scroll fully before
declaring a button missing); `canSubmit`-style gating usually keys on required
fields only; adb `uiautomator dump` + `input` equivalents for Android; the
Debug-build telemetry echo via `simctl launch --console` for verifying events.

Plus the **driver-bug protocol**: before recording any FAIL, reproduce it a
second time via a different interaction path where possible. Roughly half of
early "failures" in the shell regression were driver mistakes, and a report
full of false alarms is worse than no report. Unreproducible-but-seen-once
oddities are recorded as findings marked *suspect-driver*, not FAIL.

**4. `.claude/skills/full-regression/SKILL.md` — the entry point.** A project
skill checked into the repo, so any future session runs `/full-regression`. It
orients (overnight run; never halt; where outputs go) and points into the three
docs. It contains no duplicated content — the docs are the truth.

Optionally, genuinely reusable helper utilities (UI-dump parsers) may be
promoted into `tools/regression/`; full scripted flows are explicitly out of
scope (they calcify and break on every UI change).

### arcana-server artifact

**`manage.py seed_regression`** — an idempotent management command (safe to
re-run; it owns, deletes, and recreates only its own `@example.com` rows, per
the sandbox-hygiene rule: no real member data, no arcana.fit emails).

Creates a **separate account set per device** so one device's mutations never
contaminate another's expectations:

- `regression-ios26@example.com`, `regression-ios18@example.com`,
  `regression-android@example.com`
- Each set: one fresh awaiting-signup membership + welcome token (for the full
  survey → claim flow), and one pre-activated member with credits, an upcoming
  reservation, and favorites (for everything behind login without re-claiming).

Also manufactures the data states that make edge cases reachable: a FULL class,
a hidden/blocked class, a studio with a late-cancel window, and a
booking-window-gated (`bookable_at` in the future) class.

Finishes by printing a **manifest** (emails, passwords, welcome tokens, class
IDs for each special state) that Phase 2 captures and Phase 3 consumes.

Ships via the standing arcana-server workflow: branch → PR → green CI → merge.

## Freshness enforcement

Three layers:

1. **CLAUDE.md rule (arcana-mobile).** A new section: any PR that adds,
   changes, or removes user-facing functionality MUST update
   `docs/regression/inventory.md` in the same PR. New surface → new entry;
   changed behavior → updated Expected; removed surface → tombstoned ID.
2. **Self-audit phase (every run).** Forward: every screen-level composable,
   nav destination, and ViewModel in the codebase must be reachable from some
   inventory entry's Source references — unmatched code is an "uncovered
   surface" finding. Reverse: every Source reference must still exist —
   dangling references are "stale entry" findings. Both directions report;
   neither halts.
3. **README pointer** so humans discover the suite and the rule.

## Execution semantics

- **Statuses:** PASS / FAIL (reproduced, evidence attached) / BLOCKED (an
  upstream dependency failed — named in the entry) / SKIP (environmental, with
  reason).
- **Never halt.** The run always reaches the report.
- **Evidence:** screenshot on every FAIL, plus at key checkpoints (post-login
  home, post-claim success, booking confirmation) even when passing.
- **State discipline:** each device uses its own seeded accounts; checks that
  mutate state (booking, favorites, profile edits) note their effects so later
  entries' expectations account for them; where an entry needs pristine state,
  it says so and the runbook orders it accordingly.

## Report

Outside git: `~/arcana-regression-runs/YYYY-MM-DD/report.md` +
`screenshots/`. Structure:

1. Summary table — per-device PASS/FAIL/BLOCKED/SKIP counts, verdict line.
2. Inventory-drift findings (uncovered surfaces, stale entries).
3. Failures first, each with steps, expected vs observed, evidence links,
   and suspect-driver annotations where honest.
4. Full per-item results per device.

The agent hands Cole the report at the end of the run.

## Non-goals

- Not CI; no cloud execution; requires this Mac.
- No scripted end-to-end flows (agent-driven by design).
- No smoke/PR tier for now (future option; would be per-entry tags).
- No pixel-diff/visual-regression testing.
- No physical-device automation (simulator/emulator only).

## Implementation notes (for the plan)

- Building the inventory is the big lift: enumerate every surface by sweeping
  the codebase (screens, nav graphs, ViewModels, dialogs, error states) —
  well-suited to a multi-agent fan-out with an adversarial "what's missing"
  completeness pass — then reconcile against what the 2026-08 regression
  sessions actually exercised.
- The seed command should reuse the fixture patterns from the 2026-08 manual
  QA sessions (awaiting-signup status gotcha: claim requires
  STATUS_AWAITING_SIGNUP, not `pending`).
- The runbook's prerequisite checks should pin exact tool paths/versions at
  implementation time (idb install location, AVD name, runtime versions).
- Validation of the suite = actually running it end-to-end once and comparing
  its findings against the known-good state of the app.
