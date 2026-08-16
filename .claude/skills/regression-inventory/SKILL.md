---
name: regression-inventory
description: >-
  Keep docs/regression/inventory.md honest whenever arcana-mobile's
  user-facing surface changes. Use this skill whenever you add, change, or
  remove user-facing functionality in this repo — a new screen, tab, flow,
  dialog, sheet, or picker; changed behavior on an existing surface; a
  removed surface — or whenever you're asked to "update the regression
  inventory." Teaches the entry format, the ID scheme, how to write
  driver-executable Steps and observable Expected text, the Source-citation
  rules the self-audit script depends on, the tombstone rule for removals,
  and the iOS/Android KEEP-IN-SYNC trap. This is for the agent DOING the
  implementation work, not for running the regression suite itself (that's
  `.claude/skills/full-regression/`).
---

# Regression inventory

`docs/regression/inventory.md` is the checklist the agent-run full regression
suite (`.claude/skills/full-regression/`) drives against. CLAUDE.md's
"Regression inventory — keep it current" section makes this a hard rule:

> Any PR that adds, changes, or removes user-facing functionality MUST update
> `docs/regression/inventory.md` in the same PR.

This skill is the how-to for that update. It does not cover *running* the
suite — see `docs/regression/runbook.md` and the `full-regression` skill for
that.

## Entry anatomy

Every entry is a `###` heading plus exactly four fields:

```
### AREA-NN — Short, specific title
- **Steps:** ...
- **Expected:** ...
- **Source:** ...
- **Platforms:** ...
```

**ID scheme.** `AREA-NN` — a three-to-nine-letter area code, a hyphen, a
zero-padded two-digit number scoped to that area (`CONCIERGE-01`,
`DEVSET-11`). IDs are permanent: never renumber, never reuse a retired
number, even if the entry it belonged to is long gone.

**Pick an existing area if the surface is genuinely part of it** (a new
Profile row is `PROFILE-NN`, not a new area). The current areas: `LAUNCH`,
`AUTH`, `SIGNUP`, `HOME`, `SCHED`, `CLASS`, `FAV`, `PROFILE`, `CONCIERGE`,
`DEVSET`, `NAV`, `ERR`, `TEL`, `PLAT`.

**Create a new area section when the surface is genuinely a new top-level
destination**, not a variant of an existing one. Precedent: `CONCIERGE` was
added in the 2026-08-11 completeness sweep because the Concierge Request
screen (reached from Profile) had no natural home in the other 13 areas —
see `docs/regression/inventory.md`'s `## CONCIERGE` section (entries
CONCIERGE-01 through CONCIERGE-04) for the shape a brand-new area takes: one
entry for the screen rendering/validation, one for an edge case (truncation),
one for the failure path, one for the success path + its telemetry. A new
tab is the same kind of event — see the worked example below.

**Whichever you do, update the inventory header** (the top of
`docs/regression/inventory.md`, currently the `**N entries across M areas**`
line): bump the total, bump that area's per-area count (or add a new
`AREA N` to the list if you created one), and — per the header's own
parenthetical — keep `docs/regression/runbook.md`'s pinned entry-count
mentions in sync too (it references the total in a couple of places as a
sanity cross-check for its own Phase 1 reverse pass).

## Writing good Steps

Steps must be **driver-executable from a known start state** — write them
the way you'd instruct someone who has never seen the app and cannot ask
questions, not the way you'd describe the feature to a teammate. Two
concrete implications:

- Name the starting state explicitly ("From Profile, tap...", "With no
  cached schedule data (fresh app/session)...", "Cold-launch the app while
  authenticated..."). Never start mid-flow with unstated assumptions.
- **When a seeded state is required, name the manifest fixture by its key**,
  not by a description you invented — `classes.full`, `classes.blocked`,
  `accounts.<device>.member`, etc. These keys are defined in
  `docs/regression/runbook.md`'s Phase 2.2 fixture table and are what a
  driver greps the seed manifest for. "a class that's already full" is not
  drivable; "`classes.full`" is.

## Writing good Expected

**Behavior-level and observable, never pixel-level.** Describe what state
changes, what text/copy appears, what event fires, what a driver can
confirm by reading the screen or the debug telemetry echo — not exact
coordinates, exact colors, or anything that requires a design tool to
verify. ("The Send CTA is disabled while the message is blank" — yes. "The
button is 2px lower than the field" — no.)

**Note platform differences with the `Platforms` field**, using the existing
vocabulary exactly (don't invent new values):

| Value | Meaning |
|---|---|
| `shared` | Same behavior/entry point on both platforms |
| `iOS-only` | Doesn't apply to Android |
| `Android-only` | Doesn't apply to iOS |
| `iOS26-only` | iOS behavior that only exists on the Liquid Glass (26.x) runtime |
| `iOS18-only` | iOS behavior specific to the pre-26 (18.x) runtime |

If the same user-facing outcome is reached via genuinely different code paths
per platform (e.g. Android's single `NavHost`-driven `$screen` vs iOS's
bridge-driven tab-root `$screen`), that's a signal to write **separate
entries**, not one `shared` entry with a platform aside buried in the
Expected text — see TEL-01 (`Android-only`) vs TEL-12/TEL-13 (`iOS-only`) for
how the existing inventory handles this split.

## Source citation rules

The Phase 1 reverse audit (`tools/regression/self_audit.sh`) mechanically
`test -f`s every path on every `- **Source:**` line, so these rules aren't
style preferences — violating them breaks the audit:

- **Full repo-relative paths to concrete files.** Never elide a package
  prefix (`sharedLogic/.../schedule/Foo.kt` is wrong — write the whole
  path). Never cite a bundle directory (`iosApp/iosApp/Assets.xcassets`,
  `AppIcon.icon`) — name a concrete file inside it (e.g.
  `iosApp/iosApp/Assets.xcassets/LaunchBackground.colorset/Contents.json`).
  Both shortcuts defeat the whole point of the reverse pass, which is
  detecting renames.
- **Parenthetical annotations are fine and may contain commas** — e.g.
  `ScheduleViewModel.kt (`selectDay`, `ensureSelectedDayLoaded`)`. The
  self-audit script strips balanced parens before splitting on commas, so
  annotations don't get shredded into phantom missing-path findings.
- **Line numbers are hints, not contract.** If you cite one (`App.kt (lines
  152-206)`), understand it will drift the moment code above it moves — that
  drift is not a finding the audit will catch or that anyone needs to chase.
  Prefer citing a symbol name over a line number when you can.
- Comma-separate multiple files on one `- **Source:**` line when an entry
  genuinely spans several (screen + ViewModel + platform shell file is
  typical).

## Removing a surface

Never delete an entry's line. Replace its four fields with a single
tombstone line in place:

```
### AREA-NN — RETIRED (YYYY-MM-DD): one-line reason
```

The ID is retired with it — never reassign `AREA-NN` to a new, unrelated
entry later. Update the header counts the same way as an addition (the
retired entry still counts toward the area's total unless you're also told
to renumber, which you should not do).

## KEEP-IN-SYNC: the iOS shell mirrors Android's App.kt

A chunk of this app's logic exists twice by design: Android's `App.kt`
(`MainScaffold`, the unauthenticated branches) and iOS's
`AuthFlowRoot.kt`/`TabRoots.kt`/`IosShellBridge.kt` are deliberate mirrors
(see CLAUDE.md's "iOS Liquid Glass shell" section, "KEEP IN SYNC" note).
**If the surface you're adding touches auth-flow branching, tab roots, or
`$screen`/telemetry emission, check whether both the Android source (`App.kt`
or `TabBar.kt`) and the iOS source (`AuthFlowRoot.kt`, `TabRoots.kt`,
`ArcanaShell.swift`, or `IosShellBridge.kt`) need their own Source citation —
or their own separate entry, if the two platforms' code paths genuinely
diverge (see the TEL-01 vs TEL-12/13 split above).** Missing the iOS half of
a mirrored change is the single easiest way for this inventory to go stale
on a surface that looks Android-only but isn't.

## Worked example: adding a new tab

Say you're adding a fourth bottom-tab destination. Here's the shape the
inventory update takes, section by section:

1. **New area section**, e.g. `## STUFF` with `STUFF-01`, `STUFF-02`, ... for
   the tab's own screen content, empty/loading/error states, and any
   sub-flows — same pattern as `## CONCIERGE`. Update the header's area list
   and counts (add `STUFF N` to the per-area breakdown, bump the total).
2. **Telemetry (`TEL` area).** A new tab root needs its own `$screen` entry
   (Android: fires from `App.kt`'s `MainScaffold`; iOS: fires from the
   bridge — see TEL-01 vs TEL-12/13 for why these are usually two entries,
   not one). If the tab bar's tap event carries a `tab` value, that's
   another `TEL` entry (see TEL-14/TEL-15 for the Android/iOS split there
   too).
3. **Tab bar / navigation (`NAV` area).** Update or add to NAV-01 (tab
   switching) and NAV-04 (tab bar hides on pushed destinations) if the new
   tab changes their Source lists; add a new entry if the new tab has its
   own back-stack/back-button quirk worth tracking (see NAV-02/NAV-03's
   Android/iOS split for precedent).
4. **Sources, both platforms.** Cite the iOS shell files
   (`sharedUI/src/iosMain/.../shell/TabRoots.kt`,
   `iosApp/iosApp/ArcanaShell.swift`) alongside the Android ones
   (`sharedUI/src/commonMain/.../App.kt`,
   `sharedUI/src/commonMain/.../ui/TabBar.kt`) per the KEEP-IN-SYNC note
   above — a tab is exactly the kind of surface that touches both shells.
5. **Error states (`ERR` area).** If the new tab has its own data load that
   can fail, add its cold-start-failure and refetch-failure entries — see
   ERR-01/ERR-02/ERR-03 for the three-way split (full-screen error / silent
   keep-content / silent per-item loading state) this app actually uses; pick
   whichever matches what the new ViewModel really does, don't assume it's
   the full-screen-error case.
6. **Seed fixture, if the tab needs data to be testable.** If Steps need a
   specific seeded state to exercise (e.g. "a member with an empty STUFF
   list" vs "a member with 3 STUFF items"), that's a `seed_regression`
   fixture addition (`docs/regression/runbook.md` Phase 2.2) — name the new
   manifest key in your Steps once it exists, don't describe the state in
   prose.

## Before you finish

Run the self-audit and require **zero findings**:

```
tools/regression/self_audit.sh
```

It mechanically re-derives both directions — every ViewModel/`*Screen.kt`/
`*Sheet.kt`/`*Dialog.kt`/`*Picker.kt`/nav-destination in the source tree
traces to a `- **Source:**` line, and every `- **Source:**` path resolves
with `test -f` — and always exits 0, printing `FINDINGS: N` at the end. If N
isn't 0, the printed lines tell you exactly what's uncovered or stale; fix
the inventory (not the script) and re-run.

Finally: **the PR must include the inventory diff.** Per CLAUDE.md, an
inventory update living only in your working tree doesn't satisfy the rule —
it has to ship in the same PR as the functional change.
