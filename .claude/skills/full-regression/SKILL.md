---
name: full-regression
description: >-
  Run Arcana mobile's agent-operated full regression suite: an overnight,
  unattended pass through the entire regression inventory on all three target
  devices (iOS 26 simulator, iOS 18 simulator, Android emulator)
  against a locally seeded dev server. Use whenever the user asks to "run a
  full regression", "run the regression suite," wants a "pre-release
  regression" pass, or asks what needs checking before cutting a release
  build. Carries the three non-negotiable operating rules (never halt on a
  failure, keep every output artifact out of git, run all three devices
  against the complete inventory rather than a subset), the orchestration and
  model split, and the fact that a run ends in triage, doc fold-back and
  tracker filing rather than at the report. Points at the runbook, inventory,
  and driver playbook that do the actual work — this skill orients, it does
  not re-derive them. Prefer this over improvising a manual pass through the
  app.
---

# Full Regression

This is the agent-operated, overnight full regression pass for
`arcana-mobile` (a Kotlin Compose Multiplatform app). Orchestrated agent
shifts drive the real app — real network calls, real simulators/emulator, real
taps — through every entry in `docs/regression/inventory.md` on all three
target devices (iOS 26 simulator, iOS 18 simulator, and the `Pixel_9_Pro`
Android emulator — **simulators and emulators only; never a connected physical
device**), against a server running locally and seeded with a dedicated
regression fixture. It produces one human-first report, folds what it learned
back into these docs, and files the surviving issues to a tracker.
Nothing about this run is interactive: no pausing for a human mid-pass, no
partial credit for stopping early.

## The three iron rules

1. **Never halt.** A failed check, a missing tool, a FAILed entry, an
   inventory/code drift finding — none of it stops the run. Record it,
   classify it, and keep going. Only total inability to proceed on any
   device at all justifies stopping early, and even then write the partial
   report and still run Phases 5 and 6 over what you have.
2. **Outputs never enter git.** Everything this run produces — the report,
   screenshots, results logs, the seeded manifest — lives under
   `~/arcana-regression-runs/YYYY-MM-DD/`, entirely outside both the
   `arcana-mobile` and `arcana-server` checkouts. Never `git add`, commit, or
   push anything as a side effect of running this suite. Phase 5.2's
   doc/inventory fold-back is the one thing that touches the repo, and it is
   left **uncommitted** on a branch for review.
3. **Every device runs the complete inventory.** iOS 26, iOS 18, and the
   Android emulator each get every applicable entry — never a subset "to save
   time," never a device skipped because an earlier one had trouble.

## How to run it

Follow `docs/regression/runbook.md` phase by phase — Phase 0 (Preflight),
Phase 1 (Self-audit), Phase 2 (Environment), Phase 3 (Execution), Phase 4
(Report), Phase 5 (Triage & fold-back), Phase 6 (Tracker filing) — exactly as
written. That doc, not this skill, is the source of truth for procedure; read
it before starting and do not improvise around it.

**Phases 5 and 6 are part of a run, not follow-up work.** Phase 5 adjudicates
every FAIL and suspect-driver against the source (an unreproduced,
crash-log-less "crash" is a driver artifact until proven otherwise), then
folds the run's learnings back into the runbook, the driver playbook and the
inventory as an uncommitted diff — that fold-back is what makes each run leave
the suite better than it found it. Phase 6 files the surviving issues to
Trello. A run that stops after the report is an unfinished run.

Use `docs/regression/inventory.md` as the checklist Phase 3 drives against —
it is the enumeration of what "complete" means for this run. Use
`docs/regression/driver-playbook.md` for the actual device-driving technique
(the dump-act-verify loop, the driver-bug protocol, exact binaries/paths for
idb and adb) — the runbook assumes it and does not re-explain it.

## Orchestration & models

**The initiating session orchestrates; it does not drive.** Run the suite
through the **Workflow tool**, using the checked-in reference script
`tools/regression/full-regression.workflow.js` (args
`{ runDate, mode: 'sequential' | 'parallel' }`). **This skill instruction is
the opt-in to use Workflow for this task** — no further confirmation is
needed. The script dispatches the phase agents; the runbook remains the
contract it implements.

**Mode.** `sequential` is the default: three devices one after another, one
server, ~6.8h wall-clock. `parallel` is an explicit opt-in: three concurrent
lanes on ports 8000/8001/8002, ~2.5–3h. Both modes write one
`results-<device>.log` per device (merged into `results.log` by Phase 4) and
both defer environment-wide faults to the serialized tail phase. Pick parallel
when someone is waiting on the answer; leave it alone for an unattended
overnight pass. The runbook's "Run modes" section is the contract.

**Model split** — this matters for both cost and correctness:

| Work | Model | Why |
|---|---|---|
| Setup (Phases 0+2), self-audit (Phase 1), driving shifts and the deferred tail (Phase 3), report assembly (Phase 4), tracker filing (Phase 6) | **sonnet** | Proven sufficient across the whole 2026-08-11 run. This work is mechanical: dump, act, verify, append a line; then merge logs and transcribe an already-adjudicated digest into cards. It is also the overwhelming bulk of the tokens. Setup runs at high effort, the rest at medium. |
| Triage, adjudication and fold-back (Phase 5) | **opus**, high effort | Judgement work: reading source to decide app-bug vs inventory-expected-wrong vs driver artifact vs fixture gap, arguing severity, and writing doc edits a human will review. The 2026-08-11 run's ten phantom "iOS 26 crash" FAILs are what a weak adjudication pass costs. |

**The initiating session itself is best on Opus at default effort.** Its job
is dispatch, reading phase summaries, and judgement on adjudication — not
driving. It should not be burning context on UI trees; the runbook's token
discipline (filter dumps through bash, never paste a full accessibility tree)
applies to the shifts, and the orchestrator should never see one at all.

This skill is an orientation layer, not a second source of truth. Two things
it deliberately owns rather than points at: the three iron rules above
(restated so an agent knows the shape of the run before it opens anything),
and the orchestration/model split (a decision about *how to dispatch* the run,
which the runbook — a procedure doc read by the agents being dispatched —
would be the wrong place for). Everything else (procedure, phase detail, the
checklist, driving technique, report structure, the tracker contract) lives
only in the three docs. **On any divergence, in either direction, the docs
govern and this file is the thing that's stale.**
