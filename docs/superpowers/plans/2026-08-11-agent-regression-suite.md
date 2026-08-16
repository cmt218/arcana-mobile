# Agent-Run Full Regression Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the checked-in, agent-executable full regression suite specified in `docs/superpowers/specs/2026-08-11-agent-regression-suite-design.md`: inventory + runbook + driver playbook + `/full-regression` skill in arcana-mobile, and a `seed_regression` management command in arcana-server.

**Architecture:** Four layered doc artifacts in arcana-mobile separated by rate of change, a repo skill entry point, freshness enforcement (CLAUDE.md rule + self-audit), and an idempotent server-side seed command that manufactures all reachable data states. Run outputs never enter git.

**Tech Stack:** Markdown docs + Claude project skill; Django management command (arcana-server); idb/simctl (iOS sims), adb/uiautomator (Android emulator).

## Global Constraints

- **NO COMMITS in either repo** until Cole gives an explicit "go". Every task leaves the working tree dirty for review. (Standing workflow rule; overrides the usual commit-per-task cadence.)
- arcana-mobile work happens on branch `agent-regression-suite` off up-to-date main; arcana-server work on branch `seed-regression-command` off up-to-date main. arcana-server merges only ever via PR + green CI (later, on go).
- Test data: `@example.com` emails only; never real member data or arcana.fit emails (applies to seed command AND any examples in docs).
- Spec decisions that bind every artifact: full-only (no tiers), never halt on failure, everything runs on all three devices (iOS 26 sim, iOS 18 sim, Android emulator), run outputs go to `~/arcana-regression-runs/YYYY-MM-DD/` (outside git).
- Inventory IDs are stable and never reused; deleted entries leave a tombstone line.
- Executor-model guidance (usage-limit constraint, 2026-08-11): dispatch doc-writing and sweep subagents on **sonnet**; the seed command implementer and adversarial reviewers on **opus**. Keep orchestrator output minimal.
- Repo roots: `/Users/coletomlinson/Desktop/arcana/arcana-mobile`, `/Users/coletomlinson/Desktop/arcana/arcana-server`.

---

### Task 1: Branches

**Files:** none (git only)

- [ ] **Step 1:** In arcana-mobile: `git checkout main && git pull && git checkout -b agent-regression-suite`
- [ ] **Step 2:** In arcana-server: `git checkout main && git pull && git checkout -b seed-regression-command`
- [ ] **Step 3:** Verify both: `git status -sb` shows the new branch names, clean trees. The spec + this plan (untracked in arcana-mobile) ride along on the branch.

---

### Task 2: Feature-surface inventory (`docs/regression/inventory.md`)

**Files:**
- Create: `docs/regression/inventory.md` (arcana-mobile)

**Interfaces:**
- Produces: inventory entry format consumed by Tasks 3/4/6 — `### <AREA>-<NN> — <name>` with **Steps / Expected / Source / Platforms** fields, areas exactly: LAUNCH, AUTH, SIGNUP, HOME, SCHED, CLASS, FAV, PROFILE, DEVSET, NAV, ERR, TEL, PLAT.

- [ ] **Step 1: Multi-agent codebase sweep.** Run a Workflow (sonnet agents) fanning out one reader per area over the arcana-mobile codebase. Each agent reads the relevant screens/ViewModels/nav wiring and returns entries in the schema above. Area → starting points:
  - LAUNCH/NAV/PLAT: `iosApp/iosApp/ArcanaShell.swift`, `iosApp/iosApp/iOSApp.swift`, `sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/` (all files), `sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt` (Android composition), splash screen composable.
  - AUTH/SIGNUP: `sharedUI/.../auth/` (AuthScreen, PasswordResetRequestScreen + VMs), `sharedUI/.../signup/` (SignupSurveyScreen, SignupCompletionScreen + VMs), `sharedLogic/.../session/AppSessionController.kt`, `sharedLogic/.../navigation/DeepLinkHandler.kt`.
  - HOME: home tab screen + VM (greeting, Your Set/Circle, upcoming reservations, warm-loaded profile).
  - SCHED/CLASS/FAV: schedule screen + VM (day picker, category filter, favorites toggle/filter, refresh, loading/empty states, CLASS FULL / bookable_at-gated rendering), class detail + booking flow + cancel + late-cancel copy.
  - PROFILE/DEVSET: profile screen + VM (member info, credits display, edit profile, account footer version), developer settings (base URL override, reset).
  - ERR: error-state components (CONNECTION vs SERVER distinction, retry affordances) as they exist on main.
  - TEL: `sharedLogic`/`sharedUI` Telemetry usage — every `Telemetry.Screens` constant, tab-tap events, identify, app-start events; each becomes a verifiable entry (Debug builds echo events to console).
  Every entry must cite real file paths it derived from (these power the self-audit).
- [ ] **Step 2: Completeness critic.** One opus agent reads the merged draft plus the nav graphs and screen directory listings and hunts for anything missed — dialogs, sheets, empty states, pull-to-refresh, Android back-button behavior, iOS 18 vs 26 shell differences, avatar chip, token-already-used path, logout, forced-logout handling. Findings become entries.
- [ ] **Step 3: Session-knowledge reconciliation.** Verify these known surfaces (exercised in the 2026-08 regression sessions) each have an entry; add any missing: splash min-display; cold start authed/unauthed; login + wrong-password error; password reset request; deep link cold AND warm (app already open); 13-question survey with required-vs-optional gating; claim form (birthday auto-mask 04121995→04/12/1995, gender dropdown, address, password rules); already-consumed token; greeting with member name; category filter (curated categories); favorites add/remove + filter; manual refresh; CLASS FULL rendering; booking + credit decrement + cancellation; late-cancel bolded pre-booking copy; edit profile save + persistence; account footer version string; dev-settings base URL override + reset-to-default; per-tab state preservation on tab switch; tab bar hiding on inner screens; iOS 18 pinned opaque bar vs iOS 26 glass; iOS You-tab avatar initials chip; Android system back; $screen on every screen incl. tab re-visits (bridge-driven on iOS).
- [ ] **Step 4: Verify format.** Every entry has all four fields, stable unique ID, real Source paths (`ls` each cited path; zero misses). Header of the doc states the ID-stability + tombstone rule and points to the CLAUDE.md freshness rule.

---

### Task 3: Driver playbook (`docs/regression/driver-playbook.md`)

**Files:**
- Create: `docs/regression/driver-playbook.md` (arcana-mobile)

**Interfaces:**
- Consumes: nothing. **Produces:** technique names referenced by runbook (Task 4): "dump→act→verify loop", "driver-bug protocol".

- [ ] **Step 1: Write the doc** (sonnet subagent; the payload below is the content, verbatim facts from the 2026-08 sessions — structure it, don't thin it):
  - **iOS (idb + simctl).** UI state: `idb ui describe-all --udid <UDID>` → accessibility JSON with AXFrame point coordinates. Act: `idb ui tap <x> <y>`, `idb ui text '<string>'`. Screenshots: `xcrun simctl io <UDID> screenshot <path>.png`. Deep links: `xcrun simctl openurl <UDID> "arcana://welcome?token=..."`. Telemetry echo (Debug builds print events): `xcrun simctl launch --console-pty <UDID> org.arcana.mobile`. idb install: `brew tap facebook/fb && brew install idb-companion` + `pip install fb-idb` (needed a py3.14 patch: replace `asyncio.get_event_loop()` with `new_event_loop()` in the idb CLI entry; check `which idb` works before assuming).
  - **Android (adb).** UI state: `adb shell uiautomator dump /sdcard/window_dump.xml && adb pull /sdcard/window_dump.xml` → parse `bounds="[l,t][r,b]"`, tap centers. Act: `adb shell input tap <x> <y>`, `input swipe x1 y1 x2 y2 <ms>`, `input text '<str>'` (escape spaces as `%s`), back = `input keyevent 4`. Deep link: `adb shell am start -a android.intent.action.VIEW -d "arcana://welcome?token=..."`. Google's `android` CLI (`android layout`, `android screen capture -a`) is available as an alternative — read the "Android CLI — agent tooling" section of CLAUDE.md first; adb is the primary path.
  - **Core discipline — the dump→act→verify loop:** ONE action per UI dump, then re-dump and verify the expected change before the next action. Never chain taps off a single stale dump (layouts shift; this caused 4/13 failures in early survey driving).
  - **Traps (each cost real debugging time):** text fields pre-fill (the dev-settings URL field pre-fills the current URL — clear it before typing or you get concatenated garbage); scroll position is unknown — sweep to the TOP of a list before searching it; submit/CONTINUE buttons hide below the fold — scroll fully before declaring a button missing; taps within ~4pt of a screen edge trigger OS edge gestures (back/home/control-center) — keep tap points interior; form submit gating (`canSubmit`) usually keys on REQUIRED fields only — optional fields left blank is a valid submit path; match list labels by prefix, not equality (e.g. survey headers render "Q12 · OPTIONAL" — `^Q\d+` prefix matching, not `^Q\d+$`); the claim form's birthday field auto-masks (type `04121995`, field shows `04/12/1995` — don't type slashes); when piping build output (`| tail`), the pipe masks exit codes — grep for `BUILD SUCCEEDED`/`FAILED` explicitly.
  - **Driver-bug protocol:** before recording any FAIL, reproduce it a second time via a different interaction path where possible. If it reproduces only under one driving method, or not at all, record a *suspect-driver* finding instead of FAIL. Roughly half of early "app bugs" in the shell regression were driver mistakes.
- [ ] **Step 2: Verify** — every command in the doc names its tool's real binary; spot-run the read-only ones (`idb ui describe-all` against any booted sim, or note "verified 2026-08-11" if no sim is up); confirm both platform sections + both named techniques exist.

---

### Task 4: Runbook (`docs/regression/runbook.md`)

**Files:**
- Create: `docs/regression/runbook.md` (arcana-mobile)

**Interfaces:**
- Consumes: inventory format (Task 2), playbook technique names (Task 3), seed manifest shape (Task 5 — JSON `accounts.<device>.claim{email, welcome_token}` / `accounts.<device>.member{email, password}` + `classes.{full, blocked, late_cancel, window_gated}` session IDs).
- Produces: phase names 0–4 and status vocabulary PASS/FAIL/BLOCKED/SKIP referenced by the skill (Task 6).

- [ ] **Step 1: Write the doc** (sonnet subagent) with these phases, embedding real commands:
  - **Phase 0 — Preflight.** Checks (report-and-SKIP on missing, never abort): Xcode present; iOS 26.x AND iOS 18.5 sim runtimes (`xcrun simctl list runtimes`); idb functional; Android emulator + AVD (`emulator -list-avds`); arcana-server checkout at `../arcana-server` with venv + dev Postgres; ports 8000 free. Pin the exact runtime/AVD names found on this Mac at writing time.
  - **Phase 1 — Self-audit.** Forward: enumerate screen-level composables, nav destinations, and ViewModels (`grep -rl "ViewModel\b" sharedUI/src sharedLogic/src`, nav graph files, `*Screen.kt`); every hit must appear in some inventory Source line → else "uncovered surface" finding. Reverse: every Source path in inventory must exist → else "stale entry" finding. Findings go in the report; the run continues regardless.
  - **Phase 2 — Environment.** Start server: `cd ../arcana-server && source venv/bin/activate && python manage.py runserver 0.0.0.0:8000` (background). Seed: `python manage.py seed_regression` → capture the printed JSON manifest to the run folder. Build+install: iOS `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'platform=iOS Simulator,id=<UDID>'` then `xcrun simctl install <UDID> <path-to-.app>` for BOTH sims; Android `./gradlew :androidApp:installDebug` to the running emulator. In each app: Developer Settings → set base URL to `http://localhost:8000` (iOS sims) / `http://10.0.2.2:8000` (emulator — host loopback; Debug network config already allows cleartext).
  - **Phase 3 — Execution.** Devices strictly sequential: iOS 26 → iOS 18 → Android; the complete inventory on each (skip only entries whose Platforms field excludes the device); each device uses ONLY its own manifest account set. Per entry: drive per the playbook's dump→act→verify loop; on failure apply the driver-bug protocol, then record FAIL with a screenshot; entries depending on a failed upstream → BLOCKED (name the blocker); never halt. Checkpoint screenshots even on PASS: post-login home, post-claim success, booking confirmation. State-mutating entries (booking, favorites, profile edit) note their effect on later expectations.
  - **Phase 4 — Report.** Write `~/arcana-regression-runs/YYYY-MM-DD/report.md` + `screenshots/`: (1) per-device PASS/FAIL/BLOCKED/SKIP summary table + verdict line, (2) inventory-drift findings, (3) failures first with steps/expected/observed/evidence links/suspect-driver annotations, (4) full per-item results. Deliver the report to the user (SendUserFile). Nothing enters git.
- [ ] **Step 2: Verify** — preflight commands actually run on this Mac (execute each read-only check once and pin real names/UDIDs/paths into the doc); manifest field names match Task 5's implementation exactly; every phase consistent with never-halt.

---

### Task 5: `seed_regression` management command (arcana-server)

**Files:**
- Create: `<app>/management/commands/seed_regression.py` (implementer picks the app that houses existing management commands — look where sync/ops commands live)
- Test: alongside existing management-command tests (mirror their location/pattern)

**Interfaces:**
- Produces: `python manage.py seed_regression` printing a JSON manifest to stdout:
  ```json
  {"accounts": {"ios26": {"claim": {"email": "regression-ios26@example.com", "welcome_token": "..."},
                           "member": {"email": "regression-ios26-member@example.com", "password": "..."}},
                "ios18": {...}, "android": {...}},
   "classes": {"full": <session_id>, "blocked": <session_id>, "late_cancel": <session_id>, "window_gated": <session_id>}}
  ```

- [ ] **Step 1: Read before writing** (opus subagent): membership/payment/reservation/favorite models, the claim endpoint's status requirement (**claim requires `STATUS_AWAITING_SIGNUP` — `pending` 409s with `membership_not_eligible`**, learned the hard way 2026-08-09), `Payment.strict_window` credit wallets, `ClassSession.availability` + `bookable_at`, the per-studio late-cancel hours field, and any existing seed/fixture code to mirror.
- [ ] **Step 2: Write the failing test.** Django TestCase calling the command twice (idempotency) and asserting:
  ```python
  def test_seed_regression_idempotent_and_complete(self):
      out1 = call_command_capture("seed_regression")
      out2 = call_command_capture("seed_regression")   # re-run must not error or duplicate
      m = json.loads(out2)
      for device in ("ios26", "ios18", "android"):
          claim = Membership.objects.get(<email-field>=m["accounts"][device]["claim"]["email"])
          assert claim.status == Membership.STATUS_AWAITING_SIGNUP
          member = <User/Member lookup>(m["accounts"][device]["member"]["email"])
          assert <member has credits, one upcoming reservation, >=1 favorite>
      assert all email addresses end with "@example.com"
      assert ClassSession.objects.get(pk=m["classes"]["full"]).availability == <FULL>
      assert ClassSession.objects.get(pk=m["classes"]["window_gated"]).bookable_at > timezone.now()
  ```
  (Exact model/field names come from Step 1 — keep the behavioral contract identical.)
- [ ] **Step 3:** Run it; expect FAIL (command missing).
- [ ] **Step 4: Implement.** Idempotent = command first deletes every row it owns (only objects tied to `regression-*@example.com` emails and the marker-named class sessions it created), then recreates. Per device set: one awaiting-signup membership + fresh welcome token; one activated member with a credit wallet (strict window covering now), one upcoming reservation, favorites. Special classes: create/flag sessions for FULL, blocked/hidden, late-cancel-window studio, `bookable_at` in the future — prefer flagging synthetic sessions it creates over mutating synced rows. Print the manifest JSON as the only stdout output.
- [ ] **Step 5:** Run the test → PASS. Run the full suite the repo's usual way to ensure nothing else broke.
- [ ] **Step 6:** Run it against the real dev DB once; verify the manifest parses and a manifest-listed member can log in via the API (curl the login endpoint). Leave uncommitted.

---

### Task 6: Skill + freshness enforcement (arcana-mobile)

**Files:**
- Create: `.claude/skills/full-regression/SKILL.md`
- Modify: `CLAUDE.md` (new "Regression inventory — keep it current" section)
- Modify: `README.md` (one pointer line/paragraph)

- [ ] **Step 1: SKILL.md** (sonnet subagent). Frontmatter name `full-regression`, description triggering on "full regression", "run the regression suite", "pre-release regression". Body: what it is (overnight, agent-operated, full matrix); the three iron rules (never halt; outputs to `~/arcana-regression-runs/`, never git; everything on all three devices); read `docs/regression/runbook.md` and follow phases 0–4, with `inventory.md` as the checklist and `driver-playbook.md` as technique reference. No content duplicated from the docs.
- [ ] **Step 2: CLAUDE.md rule.** Any PR adding/changing/removing user-facing functionality MUST update `docs/regression/inventory.md` in the same PR (new entry / updated Expected / tombstoned ID). Note the self-audit will flag drift in every run.
- [ ] **Step 3: README pointer** to `docs/regression/` and the skill.
- [ ] **Step 4: Verify** file paths referenced all exist; skill loads (name matches directory).

---

### Task 7: Self-audit dry-run (validation)

- [ ] **Step 1:** Execute the runbook's Phase 1 exactly as written (opus subagent) against the fresh inventory. Deliverable: drift findings list.
- [ ] **Step 2:** Fix real gaps found (add inventory entries); if instructions were ambiguous or wrong, fix `runbook.md` Phase 1 so the next agent needs no interpretation.
- [ ] **Step 3:** Re-run once; expect zero (or only consciously-accepted) findings; note accepted exclusions in the inventory header.

---

### Task 8: Final review + handoff

- [ ] **Step 1:** Adversarial review (opus subagent): every spec section maps to shipped artifact content; cross-references (IDs, phase names, manifest fields, technique names) consistent across all files; no run-output paths inside the repo; no `@arcana.fit`/real-member data anywhere.
- [ ] **Step 2:** Fix findings.
- [ ] **Step 3:** Report to Cole: file list per repo, review summary, both trees left dirty on their branches. **No commits** — wait for explicit go (mobile: commit+PR on go; server: PR + green CI on go). The first real overnight `/full-regression` run is the suite's true validation and happens when Cole chooses.
