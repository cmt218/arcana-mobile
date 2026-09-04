# Driver Playbook

Device-driving techniques for running the agent-operated full regression suite
against the iOS simulators and the Android emulator. These are hard-won
facts from the 2026-08 manual regression sessions — read this before driving
either platform. `docs/regression/runbook.md` (the execution runbook) assumes
the two named techniques below (**the dump→act→verify loop** and **the
driver-bug protocol**) and does not re-explain them.

Every command below names a real binary. Verified 2026-08-11 against tool
`help` output and (where a booted device was available) live spot-runs — see
"Verification notes" at the end of this document.

---

## iOS (idb + simctl)

**Invocation.** The idb CLI is durably installed at
`~/.arcana-tools/idb-venv/bin/idb` — a dedicated venv. **Bare `idb` is NOT on
PATH on this Mac** — always invoke the full venv path, never bare `idb`.
`idb_companion` is installed via Homebrew and resolves normally on `PATH`.
Verified 2026-08-11: `~/.arcana-tools/idb-venv/bin/idb list-targets` lists
simulators.

**Recovery — install from scratch (only if the venv is ever missing).** `idb`
needs both halves: `brew tap facebook/fb && brew install idb-companion`, then
`python3 -m venv ~/.arcana-tools/idb-venv && ~/.arcana-tools/idb-venv/bin/pip
install fb-idb` for the CLI. On Python ≥3.14 the idb CLI entry point needs a
patch: in `site-packages/idb/cli/main.py`, replace `loop =
asyncio.get_event_loop()` with `loop = asyncio.new_event_loop()`. The current
venv already carries this patch — only re-apply it if recreating the venv.

**Alternative driver: the Claude Code iOS Simulator MCP.** `idb` above is the
suite's driver. The MCP `control` tool is a workable substitute for one-off
verification, with three constraints found 2026-08-23:
- It needs `xcode-select` pointed at the full Xcode, not the Command Line
  Tools: `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`.
  Without it every action fails, even though `xcodebuild` itself works. The
  fix needs the user's password, so an agent cannot apply it.
- **Its `text` action has no backspace.** Control characters are typed
  literally, so a pre-filled field (the Developer Settings base URL) cannot
  be cleared, only inserted into at the tap point. To reach a local server,
  temporarily point `defaultBaseUrl()` at it and rebuild rather than editing
  the field by hand; revert after. `idb ui key` does not have this limit.
- Coordinates are device **points**, same as idb, so the pixels/points trap
  in iOS-specific traps applies here too.

**UI state.** `~/.arcana-tools/idb-venv/bin/idb ui describe-all --udid <UDID>`
dumps the current accessibility tree as JSON, including `AXFrame` point
coordinates for every element. This is the read model for the dump→act→verify
loop below.

**Act.**
- `~/.arcana-tools/idb-venv/bin/idb ui tap <x> <y>` — tap a point (coordinates
  are in points, matching the `AXFrame` values from `describe-all`).
- `~/.arcana-tools/idb-venv/bin/idb ui text '<string>'` — type into the
  currently focused field.
- `~/.arcana-tools/idb-venv/bin/idb ui swipe <x1> <y1> <x2> <y2> --duration
  <seconds>` — **always pass `--duration`** (1.0 or higher) for controllable
  scrolling. Without it, the swipe flings with momentum — a single ~400-500pt
  swipe can visually jump an entire long list (see Traps).
- `~/.arcana-tools/idb-venv/bin/idb ui key <keycode>` — send a raw HID
  keycode to the focused field. Backspace is **42**, not 51 (51 is a silent
  no-op against a focused Compose `TextField`). Right-Arrow is **79**. Return
  is **40** and advances focus to the next field in a form rather than
  submitting it. Tab (43) does **not** trigger focus traversal here — see
  Traps.
- `~/.arcana-tools/idb-venv/bin/idb ui describe-point <x> <y> --udid <UDID>`
  — resolve a single point instead of a full dump; use it right after a tap
  to confirm the element it landed on actually has focus before sending
  `idb ui text` (see the focus-race trap below).
- `~/.arcana-tools/idb-venv/bin/idb ui terminate` / `idb ui launch` — **the
  `--udid` flag must come BEFORE the bundle id**, not after. A naive
  invocation matching `idb ui tap`'s positional-then-flag style fails with a
  "multiple companions" error.

**Reinstalling after an erase: use `xcrun simctl install`, not `idb install`.**
On 2026-08-27, against a freshly `erase`d-and-rebooted arm64 simulator that had
been running that same `.app` minutes earlier, `idb install` failed with
`Targets architecture x86_64 not in the bundles supported architectures:
(arm64)`. The bundle was fine: `xcrun simctl install <UDID> <same .app path>`
succeeded immediately. Treat that arch error post-erase as an idb/companion
false positive and fall through to `simctl install` rather than rebuilding.

**Screenshots.** `xcrun simctl io <UDID> screenshot <path>.png`. This writes a
device-**pixel** image, not points — see the coordinate-space trap below
before using pixel coordinates read off a screenshot for a tap.

**Deep links.** `xcrun simctl openurl <UDID> "arcana://welcome?token=..."`.
**Cold-launching this way (app terminated) triggers an iOS system `Open in
"Arcana"?` confirmation alert (Cancel/Open) before the app opens** — the
extra tap isn't optional and isn't mentioned by the OS in any way you'd
notice from a dump alone. A driver not expecting it will misdiagnose a
stuck/failed deep link.

**Telemetry echo.** Debug builds echo every `Telemetry` call to the console
(see CLAUDE.md's Telemetry section). To capture that stream when driving
headlessly: `xcrun simctl launch --console-pty <UDID> org.arcana.mobile`.
**This blocks/streams in its own shell and can't share a terminal with
interactive `idb ui` driving.** Run it backgrounded to a log file instead —
`xcrun simctl launch --console-pty <UDID> org.arcana.mobile > telemetry.log &`
— then drive normally with `idb` in a separate call and grep the log
afterward. This is a cheap way to bulk-confirm an entire TEL-* section: every
telemetry call echoes `D/Telemetry: ▶ ...`, so `$screen`/`tab_tapped`/
`identify`/`reset`/`app_start_completed` all show up in order with zero
extra device interaction beyond normal navigation.

**`--console-pty` is the ONLY thing that reaches this stream, and it does
work — do not go hunting for a second mechanism and do not block an entry on
its absence.** Two plausible-looking alternatives return nothing: `xcrun simctl
log stream` with a `Telemetry`/`screen`/`posthog` predicate (the echo goes to
the process's stdout, not to os_log, so the predicate matches only OS framework
noise), and `simctl launch --stdout=/--stderr=` redirection (the files are
never created). Re-verified live 2026-08-28 on both pinned sims: eleven
`D/Telemetry:` lines inside 25s of a cold launch on iPhone 17 Pro Max (26.3)
and on iPhone 16 Pro (18.5), same builds the run drove. On 2026-08-27 the two
iOS lanes recorded 34 applicable entries BLOCKED for "no capture mechanism
exists on iOS" — TEL, plus NAV-11, DEVSET-11, LAUNCH-03, SIGNUP-23 and
PLAT-06 — while that run's own `telemetry-ios26.log`, which its earlier
LAUNCH-03 and SIGNUP-23 PASSes were scored from, had been produced by this
exact command. That claim is false; never record it again.

---

## Android (adb)

adb is the primary path. Google's `android` CLI (`android layout`, `android
screen capture -a`) is available as an alternative inspection tool — read the
"Android CLI — agent tooling" section of this repo's `CLAUDE.md` first before
reaching for it; it can inspect but not interact (no `android tap`/`input`
command), so `adb shell input ...` is still required for actions either way.

**UI state.** Dump the accessibility hierarchy and pull it locally:
```
adb shell uiautomator dump /sdcard/window_dump.xml
adb pull /sdcard/window_dump.xml
```
Parse `bounds="[l,t][r,b]"` on each node and tap the **center** of the
bounding box (`y + height/2`, not the top-left `y`) — using the raw top-left
`y` can land a tap in the wrong row when several similarly-sized rows are
stacked. **This dump does NOT include the soft keyboard (IME)** — a button
the dump reports at a given bounds can be physically hidden behind the
on-screen keyboard. Dismiss the IME (tap elsewhere, or a single back-key
press while a field has focus) and re-dump before trusting a post-text-entry
submit button's coordinates. The dump also renders a `TextField`'s
placeholder text under the same `text` attribute as real typed content when
the field is empty — a screenshot is needed to tell "field genuinely still
has stale text" from "field is empty, that's just the placeholder."

**Act.**
- `adb shell input tap <x> <y>`
- `adb shell input swipe <x1> <y1> <x2> <y2> <ms>`
- `adb shell input text '<str>'` — escape spaces as `%s`, **every space,
  always**. This is not optional polish: a multi-word string handed to `adb
  shell` through Bash is split by the device-side shell and only the FIRST
  word lands in the field, silently (typing `A friend at the gym` into a
  survey "specify" field yields `A`). Two characters this app's regression
  fixtures use are also mishandled: `@` is silently **dropped**
  (`user@example.com` types as `userexample.com`) — send
  `adb shell input keyevent 77` (`KEYCODE_AT`) between the two halves
  instead. A literal backslash before `!` (attempting to escape it,
  `input text 'Pass\!'`) is **not** stripped by the device shell — it types
  the backslash and the `!` as two literal characters. Just pass a bare `!`
  inside single quotes on the host side; POSIX `sh` (unlike interactive
  bash/zsh) has no `!` history-expansion, so it reaches the device fine
  unescaped.
- `adb shell input keyevent <code>` — key reference: **4** = system back;
  **67** = backspace/DEL (unreliable alone against a Compose `TextField` —
  see Traps for the pacing/positioning fix); **123** = MOVE_END; **61** =
  `KEYCODE_TAB` and, unlike iOS, this **does** correctly trigger Compose's
  `onPreviewKeyEvent` focus-traversal path (confirmed via PROFILE-26: focus
  genuinely moved to the next field) — don't assume Android inherits iOS's
  Tab-traversal limitation. **Shift+Tab is `adb shell input keycombination 59
  61`, not `keyevent --shift 61`.** `--shift` is not a flag this adb's `input`
  accepts (`adb shell input --help` lists no modifier flags); it is parsed away
  and a bare second Tab is sent, so focus jumps FORWARD two fields and reads
  exactly like a focus-traversal bug. PROFILE-26 was logged suspect-driver on
  2026-08-27 for precisely this and passed cleanly once re-driven with
  `keycombination`.
- `adb -s <serial> exec-out screencap -p > file.png` — **the `-s <serial>` goes
  before `exec-out`, not after.** `adb exec-out -s <serial> screencap -p >
  file.png` does not error visibly: adb writes its own usage/error TEXT down
  the same redirect, producing a `file.png` full of ASCII that every later step
  treats as an image. Sanity-check with `file file.png` (expect `PNG image
  data`) whenever a screenshot "looks wrong" in a tool.
- `adb shell svc wifi disable` / `adb shell svc wifi enable` (and the `svc
  data` equivalents) — a clean, lower-risk way to force a network failure
  than killing the local server: no risk of collaterally killing another
  process on the host (see the CRITICAL SAFETY note below), and it recovers
  instantly on re-enable + RETRY. **It does NOT cut the emulator's route to
  `10.0.2.2`, which is the base URL every Android lane actually uses.**
  Host-loopback traffic on the `Pixel_9_Pro` AVD is not gated by the simulated
  wifi state: on 2026-09-04 a survey submit driven "offline" returned a real
  200 that landed in the server log, which both failed ERR-19's CONNECTION leg
  and burned the device-local `signup_survey_done:<token>` state it was
  supposed to preserve, chaining five further entries into BLOCKED. Use it only
  against a tunnel or a real host, and **verify the outage before trusting it**
  (`adb shell curl`-equivalent, or just watch the server log stay silent).
  For a `10.0.2.2` lane, force the CONNECTION leg by restarting the run's own
  server instead — the recipe under "Killing and restarting your own server"
  below.
- `adb shell run-as org.arcana.mobile <cmd>` — **debuggable builds only.** On a
  minified `qa`/`release` install it fails outright with `package not
  debuggable` and there is no substitute short of a rooted image, so every
  technique built on it (LAUNCH-06's staged keyset restore, AUTH-12 leg (c),
  any secure-prefs inspection) is unavailable on such a run; record BLOCKED
  naming the build type rather than hunting for an alternative. On a debug
  build it reads the app's private files with no root needed, e.g.
  `adb shell run-as org.arcana.mobile rm shared_prefs/arcana_secure_prefs.xml`
  to simulate an externally-cleared secure store. **This also wipes the
  Developer Settings base-URL override**, since `BaseUrlProvider` persists
  into the same file — the app silently reverts to the prod default
  (`https://api.arcana.fit`) with zero visible indication on AuthScreen, and
  a subsequent login just shows the generic "email and password don't
  match," easily mistaken for a real credential mismatch or a stuck button.
  After any full-secure-prefs clear, re-open Developer Settings and confirm
  "CURRENTLY IN USE" reads the expected local URL before treating a login
  failure as an app bug. **`rm`-ing that file does NOT reproduce AUTH-12
  part (c)'s "unreadable storage" codepath.** A deleted file is simply an
  absent one: `SecureStorage` recreates it empty on its next write and the
  refresh succeeds (observed 200 with the file silently rebuilt on
  2026-08-27), so the run proves only the outcome AUTH-12(c) asserts (no
  forced logout) and never exercises a real read failure. The only recipe in
  these docs that produces a genuine `AEADBadTagException` is LAUNCH-06's
  staged keyset restore (save the prefs file off-device → `pm clear` →
  relaunch to mint a new master key → force-stop → write the OLD file back →
  cold launch). Use that if the entry needs the failure itself; otherwise say
  in the results line which half you proved.

**Telemetry.** `adb logcat -s Telemetry:D` is the primary technique for the
whole TEL block — reliable, cheap, and precise (exact event name + full
property map), far better evidence than inferring correctness from UI state.
**It assumes a Debug build and produces literally nothing on any other.**
`Telemetry.kt` gates the whole echo on `isDebugBuild`, so on a `qa`/`release`
install the filtered logcat is empty and so is the unfiltered ring buffer —
verified 2026-09-04 across a full cold launch. An empty capture on a
non-debuggable build is not a broken technique and not a missing event: record
the affected entries BLOCKED **with the build type in the reason**, and never
infer from silence that an event did not fire.
Start it once at the top of the TEL/NAV block:
`adb -s emulator-5554 logcat -s Telemetry:D -v brief > telemetry.log &`, then
grep the tail after each action. A backgrounded `> file &` pipe can
intermittently stop capturing into that file across an `am force-stop` +
relaunch of the app (new process, same filter) even though the underlying
`logcat` process is still alive with the events in its own buffer — if the
tailed file comes up empty around a cold-start entry (TEL-18/19-style),
fall back to a one-shot dump of the whole ring buffer:
`adb logcat -d -s Telemetry:D`.

**Deep links.** `adb shell am start -a android.intent.action.VIEW -d
"arcana://welcome?token=..."`.

---

## Core discipline — the dump→act→verify loop

**ONE action per UI dump, then re-dump and verify the expected change before
the next action.** Never chain multiple taps off a single stale dump —
layouts shift underneath you (a field appearing/disappearing, a list
scrolling, a sheet animating in) and a dump taken before the last action no
longer describes the screen you're about to act on. This exact mistake caused
4 of 13 failures in early survey-driving sessions: taps landed on the wrong
element because the driver kept acting against a dump that was already stale.

The loop, concretely: dump UI state → decide the one action it justifies →
perform that action → dump again → confirm the dump shows the expected
post-action state before deciding on the next action. If the post-action dump
doesn't show what you expected, stop and diagnose before proceeding — don't
plow ahead on the assumption the action "probably worked."

---

## Traps

Each of these cost real debugging time in the 2026-08 sessions. Check for
them before concluding something is broken.

### Cross-platform

- **Text fields pre-fill — and typing into them does NOT clear them.** The
  dev-settings URL field pre-fills with the current URL on **every** screen
  entry, not just the first. Typing a new value into a field that already
  has content concatenates or interleaves garbage instead of replacing it
  (`http://newhosthttp://oldhost`-style corruption on iOS; the same failure
  mode hits Auth email/password fields too, not just the dev-settings URL
  field). Backspacing from an unknown cursor position is unreliable — full
  clearing recipes: **iOS** — tap near the field's visible **right edge**
  (not center/left) to reliably place the cursor at end, then Right-Arrow
  (keycode 79) ~40x to force true end-of-string, then Backspace (keycode 42)
  ~40x; **Android** — `keyevent 123` (MOVE_END) then repeated `keyevent 67`
  (DEL), paced ~0.15-0.2s apart — a fast back-to-back burst gets
  coalesced/dropped by the input system and silently leaves stale characters
  (a field showing 22 dots needed ~85 total backspaces across two paced
  bursts to actually empty). If in-place clearing still won't take on
  either platform, a full app relaunch (fresh empty fields) is the only
  100%-reliable fallback — don't burn more time fighting a stuck field.
- **Scroll position is unknown.** You cannot assume a list starts at the top.
  Sweep to the TOP of a list before searching it for a label or control.
- **Submit/CONTINUE buttons hide below the fold.** Scroll fully before
  declaring a button missing — it is frequently just off-screen, not absent.
- **The floating tab bar's touch-interception zone swallows taps on
  on-screen controls, not just off-screen ones.** Distinct from the
  edge-gesture trap below and from plain below-the-fold: a control whose
  reported bounds sit in roughly the last 80-90pt of screen height can be
  fully present in the UI dump and visually on-screen, and still have its
  tap swallowed by the tab bar sitting on top of it — reproduced on both
  iOS 18.5 and Android when a seeded member's favorites pushed a SIGN OUT
  button (once opened a stray Concierge modal) or a Home SEE ALL row into
  that zone. Fix: scroll the screen up (~150-200pt) so the target sits
  fully above the tab bar's top edge, re-dump, then tap.
- **The tab bar's own reported AXFrame is not a reliable tap target
  either.** Separately from the swallowing trap above (about *other*
  content getting eaten): tapping the tab bar's own icons using the
  coordinates `describe-all`/`layout` report for it is itself flaky — taps
  at the reported center repeatedly missed, and empirically-found working
  coordinates (cropped from a screenshot) were themselves inconsistent
  (worked once, then ~10 consecutive identical-coordinate attempts failed,
  then worked again later). This reads as a hit-test/animation-timing issue
  specific to the floating glass bar, not a coordinate-space bug — every
  other Compose-rendered control's AXFrame was reliable all shift. Document
  a screenshot-derived verified tap point per pinned device/UDID rather
  than trusting the AX tree for this control, and use a retry-with-redump
  loop for tab switches.
- **Edge gestures hijack taps.** Taps within roughly 4pt of a screen edge
  trigger OS-level edge gestures (back / home / control center) instead of
  hitting the app's UI. Keep tap points interior to the screen.
- **A disabled Compose submit/save button often has NO button semantics at
  all.** It can render as a plain non-interactive text node (small frame,
  no button role) and silently no-op on tap; only once its gate flips
  (e.g. dirty+valid) does it become a real tappable button with a full-size
  frame. Don't conclude "button ignored my tap" without first checking
  whether its role/frame actually changed between dumps — a tap on the
  disabled state looks identical to a dropped tap.
- **Sticky-bottom CTAs need a fresh dump immediately before every tap.**
  Their coordinates shift 20-50px between "the same" screen's instances
  depending on prior scroll/keyboard state, and there's a real timing race
  on Compose forms where a scroll-into-view animation is still settling
  when the tap fires — taps at the freshly-dumped center can land on the
  field/row just above instead (observed opening a text-selection/Paste
  popup on a password field instead of submitting). A longer settle delay
  (~2.5s) after the scroll, with no further scrolling before the tap, is
  what reliably got the real tap through.
- **Form submit gating usually keys on required fields only.** A `canSubmit`
  -style gate (or equivalent) typically checks only REQUIRED fields —
  leaving optional fields blank is a valid, submittable path. Don't treat an
  enabled submit button with blank optional fields as a bug.
- **Match list labels by prefix, not equality.** Survey headers render like
  "Q12 · OPTIONAL" — match with a `^Q\d+` prefix, not `^Q\d+$` equality, or
  you'll fail to find labels that have trailing annotations.
- **Multi-field forms silently re-lay-out after any text-entry or
  keyboard-open action.** A second tap computed from an earlier dump can
  land on the WRONG field and corrupt it (typed text landing in Street
  Address instead of City, or Last Name instead of Phone). Re-dump (or
  re-screenshot) immediately before every tap in a multi-field form, not
  just after the first action in a sequence.
- **The claim form's birthday field auto-masks.** Type `04121995` and the
  field renders `04/12/1995` on its own — do not type the slashes yourself,
  or the mask logic produces garbage.
- **Multi-select survey questions tolerate a stray tap; single-select and
  text fields don't.** An errant tap landing on an unintended multi-select
  option just adds a second valid selection rather than corrupting the
  answer. Single-select and text-field taps have no such safety net —
  prefer verifying against multi-select questions when coordinate
  confidence is low, and always re-verify single-select/text-field taps
  explicitly.
- **A server killed with `SIGSTOP` does NOT reproduce a network failure.**
  It stalls the TCP socket (the OS still accepts the connection) so an
  in-flight request just hangs and then succeeds once resumed, rather than
  failing fast. Use a full `kill`/`pkill` (SIGTERM/SIGKILL, terminating the
  process outright) to get a real, fast connection-refused failure.
  `docker stop`/`start arcana_postgres` is a separate, distinct recipe: a
  clean, fast way to force a 500 from most authenticated endpoints (server
  stays reachable, DB calls fail) without touching app or server code — use
  it for a "5xx" entry, not a "connection-refused" one. **For an entry that
  needs a SPECIFIC status from a SPECIFIC endpoint, use fault injection
  instead of either** (see below); killing the server or the DB affects every
  endpoint at once and only ever produces one failure shape. Developer Settings' base-URL override is only reachable
  pre-auth, so for a network-failure entry reached only while authenticated
  (e.g. Home), kill/restart the actual local dev server process instead —
  find the specific `manage.py runserver` PID via `ps aux | grep manage.py`
  (see the CRITICAL SAFETY note below for why NOT `lsof -ti :8000`), then
  restart with `python manage.py runserver 0.0.0.0:8000 &` from the venv.
- **Piping build output masks exit codes.** When piping build output (e.g.
  `| tail`), the pipe's exit code masks the real build exit code. Grep the
  output for `BUILD SUCCEEDED` / `BUILD FAILED` explicitly rather than
  trusting `$?` after a pipeline.
- **A background local dev server can die silently between tool calls with
  no error surfaced.** This produced an apparent client-side "stuck Loading
  forever" on a Schedule day-chip tap that looked exactly like an app
  freeze — reproduced via two independent interaction paths before
  discovering (via `curl` + `ps`) that the server process itself was simply
  gone. Whenever ANY fetch appears to hang indefinitely with no error state
  ever rendering, `curl` the health endpoint and `ps`-check the server
  FIRST, before spending driver-bug-protocol reproduction effort assuming
  it's a client bug.
- **A stray tap on a sticky booking CTA after a cancel can silently reopen
  the Confirm-booking sheet for the same, now-cancelled session.** Worth
  remembering as a trap when dumping post-cancel state before the next
  intended action.
- **Tapping the body of an active Schedule filter chip (TIME/MODALITIES)
  does not clear it** — it reopens the filter panel instead. The precise
  remove-icon (X) tap target hasn't been isolated; force-stopping and
  relaunching the app is the fastest reliable way to clear an active filter
  mid-pass instead (filter state doesn't persist across a process restart),
  when the in-panel clear path isn't working.
- **The studio/location accordion's DONE control sits BELOW the whole studio
  list, not at the panel's top.** With the real synced studios present the
  list is long enough that DONE is several screens down, which reads as "this
  panel has no way to confirm" — the 2026-08-27 iOS 26 lane blocked SCHED-13's
  close step, SCHED-17 and SCHED-19 on exactly that reading while the iOS 18
  lane found DONE by scrolling the panel to its bottom and closed it cleanly.
  Scroll the panel, or collapse the accordion; neither entry needs a new
  fixture.
- **A welcome token is consumed ONLY by a successful `complete_signup`; the
  survey gate that makes the screen look "used up" is a DEVICE-LOCAL key.**
  `AppSessionController.SURVEY_DONE_KEY_PREFIX` stores `signup_survey_done:
  <token>` in SecureStorage, so once a device has finished the survey for a
  token that device skips straight to claim-your-name forever — even though
  server-side `SignupToken.consumed_at` is still null. Abandoning the survey
  ("Log in instead"), a failed claim submit, a 409 collision and a 410 are all
  non-consuming. Measured on this run's own DB at the end of 2026-08-27:
  `regression-ios26-spare` and all three `*-conflict` tokens still had
  `consumed_at = None`, while the two iOS lanes had blocked NAV-06/07/08/09,
  ERR-18, TEL-10/11, SIGNUP-23 and SIGNUP-24 for "all four tokens consumed."
  To reach the survey again on the same device: wipe app data (iOS `simctl
  erase`/reinstall, Android `pm clear`) to drop the local key, **immediately
  re-set the Developer Settings override** (the wipe restores the prod
  default — see Safety), then re-deliver a token whose `consumed_at` is null.
  `claim_conflict` is the best candidate: its collision only bites at submit.
- **`measure_centering.py` runs — Pillow IS installed on this Mac.** PLAT-11
  was blocked on both iOS lanes on 2026-08-27 for "`python3 -c "import PIL"`
  fails, `pip3 show pillow` reports not found"; re-checked 2026-08-28, both
  `/opt/homebrew/bin/python3` and `/usr/bin/python3` import Pillow 12.3.0, and
  the Android lane of that same run scored PLAT-11 with the script (ink offset
  +0.33pt/+0.00pt on the `TRY AGAIN` RetryButton). If the import genuinely
  fails, name the interpreter you tried before blocking the entry — and never
  fall back to eyeballing, which the parent CLAUDE.md forbids outright.

### Cross-platform — the reviewer sign-in destroys your base-URL override

**Signing in as a reviewer account (AUTH-16) leaves the device pointed at
PRODUCTION after sign-out. Restore the override before driving anything else.**
This is correct app behavior, not a bug, and must never be "fixed" in the app to
suit the suite — accommodating it is the driver's job.

The mechanism (`auth/ReviewerRedirect.kt` + `networking/BaseUrlProvider.kt`):
`applyFor(email)` runs on *every* sign-in attempt (`AuthViewModel.kt`). For
`apple-reviewer@test.com` / `google-reviewer@test.com` it saves a marker and
calls `setUrl(STAGING_URL)` — `BaseUrlProvider.set()`, writing **the same
persisted key the Developer Settings override uses**, so the local URL is gone.
On sign-out `onSessionEnded()` → `clear()` → `resetUrl()` →
`BaseUrlProvider.reset()`, which **deletes the key and falls back to
`defaultUrl` = `https://api.arcana.fit`**. A real member has no override, so
reset-to-prod is exactly right for them; a regression device just lost its
pointer to the local server.

So: after the reviewer sign-out, re-run the 10-tap wordmark gesture, re-set the
override, and prove it took by watching a real request land in the run's own
server log — the "CURRENTLY IN USE" label alone is not proof. If you cannot
restore it, stop and record BLOCKED; a device left on production is worse than
an unverified entry. The class KDoc's "A Developer Settings override (no
marker) is never touched" is true only until a reviewer signs in.

**The marker arms on a FAILED reviewer login too, and that is the dangerous
case.** `applyFor()` runs *before* `api.login()`, so the marker is persisted on
any reviewer-email sign-in attempt whether or not the credentials work — and
against a local run they will NOT work, because the reviewer accounts live only
in staging's own seed. You are then signed out with the marker armed, so there
is no Profile → Sign out path to clear it, and the next non-reviewer sign-in
fires `clear()` → `reset()` to the prod default *before* sending its request.
**That would put real regression-member credentials on the wire to production.**

**This recurred on 2026-09-04 despite being written down here**, because the
entry's own numbered Steps put recovery at "sign out" — which is a no-op after
a login that failed. AUTH-16's Steps have been corrected; drive the recovery
below the moment the reviewer login returns anything other than a session, and
do not treat "I re-set the override and CURRENTLY IN USE reads the local URL"
as recovery. The marker outlives that save and fires on the next sign-in.

Recovery, used successfully on all three devices on 2026-08-28: wipe the
persisted store, which clears the marker with zero network calls, then re-set
and verify the override before any further sign-in.
- iOS: `xcrun simctl shutdown <UDID> && xcrun simctl erase <UDID> && xcrun simctl boot <UDID>`, then reinstall.
- Android: `adb -s <serial> shell am force-stop org.arcana.mobile && adb -s <serial> shell pm clear org.arcana.mobile`.

**Post-erase iOS install trap:** `idb install` can fail on a freshly erased
simulator with `Targets architecture x86_64 not in the bundles supported
architectures: (arm64)` for the *same* .app it ran minutes earlier. `xcrun
simctl install <UDID> <path>` works immediately — use it as the fallback rather
than rebuilding.

### iOS-specific

- **idb's AXValue is unreliable for text inputs right after `idb ui text`.**
  It frequently reports `None` even when the field visibly holds the typed
  text (confirmed via screenshot on AuthScreen email/password,
  PasswordResetRequestScreen email, and the Developer Settings base-URL
  field) — don't treat an AXValue of `None` as "field is empty," always
  screenshot-verify before concluding a text-entry action failed. On long
  strings (multi-hundred characters, e.g. CONCIERGE-02's 1000-char cap)
  `idb ui text` injects asynchronously — a dump taken immediately after the
  command undercounts characters; add a settle delay (or poll until stable)
  before reading AXValue, or you'll misdiagnose a truncation bug that isn't
  real.
- **`idb ui text` has a real focus race after a tap.** Even a 0.5-0.6s sleep
  after `idb ui tap` sometimes isn't enough for focus to land before the
  next `idb ui text` fires — text can concatenate into the PREVIOUSLY
  focused field instead of the newly tapped one (happened repeatedly on
  Street Address → City style transitions). Verify focus actually landed —
  via `idb ui describe-point <x> <y>` on the tapped coordinate, or a
  re-dump — before sending `idb ui text`, rather than trusting a fixed
  sleep.
- **Tab (HID keycode 43) does not do hardware-keyboard focus traversal on
  the iOS Simulator via idb.** It's been observed to insert a literal `\t`
  character into the focused field in one context and trigger an
  unexplained navigation back to the previous screen in another — neither
  reproduces real Tab-key focus traversal. There is no documented
  alternative iOS input path for hardware-keyboard-only behaviors
  (PROFILE-26/SIGNUP-22-style entries) on a simulator; this needs research
  (real hardware-keyboard passthrough via `simctl`?) before it can be driven
  reliably. Android's `keyevent 61` does NOT have this limitation — see the
  Android section above.
- **Screenshot pixel coordinates are NOT idb's point coordinates.**
  `xcrun simctl io screenshot` writes device-**pixel** images (e.g.
  1206x2622 on an iPhone 16 Pro), but `idb ui tap`/`swipe`/`describe-all`
  all operate in device **points** (402x874 on the same device) — a flat 3x
  ratio there, but don't assume it's always 3x. Always get coordinates from
  `describe-all`'s `AXFrame` (using the frame's true center,
  `y + height/2`, not its top-left `y`), never estimate a tap point by
  eyeballing a screenshot's pixel coordinates.
- **FIXED 2026-08-23 (PR #34):** `PasswordResetRequestScreen` used to keep
  its post-submit "Sent" state across re-navigation, so a retest needed a
  full app kill. It now resets on entry like AuthScreen does; re-entering
  gives a fresh form and the kill+relaunch step is no longer needed. Same
  for Developer Settings' unsaved base-URL draft (PR #35).
- **"Returning to Home" means two different things on iOS, and only one of
  them re-runs a `LaunchedEffect`.** Measured 2026-08-23 by counting
  `/memberships/me` on the dev server: an in-tab push/pop (Home → My
  Bookings → close) rebuilds the composition and refetched **1x** on both
  platforms even before CLASS-25's fix, but a TAB SWITCH (Home → Schedule →
  Home) refetched **0x** on iOS, because tab compositions persist there and
  the effect never re-ran. Android refetched either way. When scoring any
  staleness entry, say which of the two paths you drove: they are not
  interchangeable, and a card that says "return to Home" without saying how
  is ambiguous. (Home now refetches on both, via `LifecycleResumeEffect`.)

- **The software keyboard never appears while you drive with `idb`, so
  AUTH-15's soft-keyboard-obscures-the-CTA case is not reachable on a
  full-size simulator this way.** `idb ui text`/`idb ui key` deliver HID
  keystrokes, which the simulator treats as a connected hardware keyboard and
  therefore suppresses the on-screen one — screenshots show the form with no
  keyboard at all. Recording AUTH-15 BLOCKED for "the keyboard never renders"
  is honest but leaves the entry permanently uncovered on iOS. To actually
  drive it: turn the software keyboard back on for the sim (`I/O → Keyboard →
  Connect Hardware Keyboard` off, or `xcrun simctl` a device that has it off),
  or pin an SE-class short-viewport device where the CTA falls under the
  keyboard's band even at rest. Neither has been run yet — pick one and record
  what it does before the entry is scored again.

### Android-specific

- **Neither `uiautomator dump` nor `android layout` includes the soft
  keyboard, so both report controls at coordinates the IME is physically
  covering.** A blind `adb shell input tap` at those coordinates lands on the
  keyboard: the tap "succeeds", nothing happens, and it reads exactly like a
  dead button. This cost a full run on 2026-08-16 and produced a bug card for
  an app defect that does not exist (the sign-in form scrolls; see AUTH-15).
  Before tapping anything in the lower half of a screen with the keyboard up,
  get the IME top edge and treat everything below it as unreachable:
  ```
  adb shell dumpsys window displays | grep -A2 InputMethod
  ```
  or just `android screen capture` and look. Scroll the target above the IME
  first, or dismiss the keyboard. **`keyevent 111` (ESC) is not a reliable
  dismissal on the `Pixel_9_Pro` AVD** — it left the keyboard up repeatedly
  across the 2026-08-27 run. `keyevent 4` (BACK) does dismiss it, and is safe
  for that purpose *provided* no leave-confirmation is armed on the current
  screen (see the pre-auth back trap below); prefer it, and re-dump to confirm
  the IME actually went away rather than assuming either key worked.

- **The IME opening or closing re-lays-out the whole form, so every
  pre-keyboard coordinate is stale.** Android auto-scrolls the focused field
  to sit just above the keyboard, which moves every other field with it: a tap
  computed from a dump taken before the keyboard came up lands one or two
  fields off once it is up, and vice versa. On the 2026-08-27 run this typed a
  password into the claim form's ZIP field twice (corrupting it with digits and
  a comma) and silently dropped two attempts at the survey "specify" field.
  After ANY action that opens or closes the IME, take a fresh **screenshot**
  (not just a dump — see the placeholder-text caveat above) before the next
  tap, or move between adjacent fields with `keyevent 61` (Tab) instead of
  tapping by coordinate. Budget for it: a full claim-form pass costs ~15-20
  dump/tap cycles, and SIGNUP-19/-20 each need their own complete re-fill
  because the collision/expiry check only fires at submit, not at survey time.

- **A `clickable="false"` reading on CREATE ACCOUNT or CONTINUE can be stale
  even when the button is really enabled.** Both showed `clickable=false` in a
  dump taken moments before a screenshot of the same screen showed them fully
  enabled (Moss-filled, arrow visible). Re-dump or screenshot immediately
  before trusting a disabled-button reading on those two CTAs — one dump is
  not enough to conclude the required-fields gate is broken.

- **`android layout`'s reported coordinates can go stale across repeated
  dumps when a screen has just transitioned to an error/expanded state**
  (e.g. an inline error banner pushing a button down). The JSON kept
  reporting the pre-error `y` across many repeated invocations, and taps at
  that stale `y` landed on empty space with no visible effect — easy to
  mistake for "nothing changed" when actually the old error text is just
  being re-read. Cross-verify with the annotated-screenshot path
  (`android screen capture -a` + `android screen resolve
  --string="tap #N"`) when a screen has just changed state, rather than
  trusting the previous `layout` dump.
- **Screenshot coordinates from an image-viewing tool are downscaled from
  the device's real resolution** (observed: real 1280x2856 rendered at
  ~896x2000, a ~1.43x scale factor) — eyeballing tap coordinates directly
  off a displayed screenshot without correcting for this causes repeated
  mis-taps. Always prefer a fresh `uiautomator dump` for exact bounds over
  estimating from a screenshot.
- **System back on PRE-AUTH screens does three different things, and using
  `keyevent 4` to dismiss the IME can trip any of them.** As of NAV-13
  (shipped 2026-08-19, re-confirmed by driving on 2026-08-27):
  **SignupSurveyScreen and SignupCompletionScreen** now pop a confirm-to-leave
  alert ("Leave signup?" / "Your details are not saved yet…") as soon as any
  field has been touched — tap "Keep going" to stay, and expect the dialog
  rather than a silent dismissal. **PasswordResetRequestScreen** returns to
  the Auth screen (NAV-12 (a)). **AuthScreen itself** still backgrounds/exits
  the app to the launcher on a bare back press with no dialog, correctly — it
  is the root of the signed-out flow. The older blanket claim that all three
  secondary screens silently exit the app and discard typed state described
  the NAV-13 defect, not today's behavior; do not score the confirm dialog as
  a regression. Still narrow to the pre-auth flow: system back on
  authenticated non-tab destinations (Class Detail, My Bookings, Edit Profile)
  pops the nav stack as expected — don't conflate the two.
- **The Auth screen's hidden 10-tap Developer Settings gesture target is
  NOT the visible "SIGN IN" text node** — it's a separate, unlabeled
  clickable node above it (the same clickable region that contains the
  wordmark graphic). Tapping the visible "SIGN IN" text 10x does nothing;
  target the node above it.
- **`SignupSurveyScreen` is one long scrolled column (~13 questions), not
  one-question-per-page.** Driving it reliably needs a fresh dump/screenshot
  after EVERY individual tap, not batches of remembered coordinates across
  multiple scrolls — partial-context batched taps caused questions to be
  silently skipped. Budget it its own dedicated top-to-bottom pass and
  check the "`<N> OF 13 ANSWERED`" counter after each tap.
- **Grid spot-selection Compose-canvas dots have no text nodes in the
  `uiautomator` dump** between "PICK YOUR SPOT" and the credits line — find
  them by filtering `clickable=true` nodes whose bounds fall in that
  y-range, not by text search. `CONFIRM`'s reported `enabled=true`
  accessibility attribute is misleading and does not reflect the real
  `canConfirm` gate — a tap before a spot is picked (and before the "HAVE
  YOU BEEN TO `<STUDIO>` BEFORE?" prompt is answered) silently no-ops. The
  only observable disabled signal is the button's visual color
  (olive/muted vs. vivid Moss fill), and even that's ambiguous from a
  text-only dump — screenshot to confirm before concluding a tap "did
  nothing."

- **After an IME open/close, tap the MIDPOINT between a field's label and the
  next field's label, from a fresh `uiautomator dump`.** The IME-relayout trap
  above says coordinates go stale; on the claim form specifically the error is
  systematic rather than random — across ~6 occurrences on 2026-09-04 a tap
  computed from a capture even one action old landed one field **earlier** than
  intended, never later (typing into Street Address when City was the target,
  First Name when Last Name was). A fixed pixel offset below a label is what
  fails. The recipe that worked every time: `keyevent 4` to dismiss the IME,
  fresh `uiautomator dump` (a dump, not a screenshot), then tap the vertical
  midpoint between that field's own label bounds and the next label's bounds.

- **A sticky-bottom CTA on a Compose form needs a settle AND a fresh dump, not
  a remembered coordinate.** Tapping CREATE ACCOUNT / CONTINUE / SAVE at a
  coordinate read off a screenshot taken moments earlier landed on the STATE
  field twice on 2026-09-04, corrupting it with typed keyboard-shortcut text
  (`NY BY`) — the kind of damage that then reads as a form-validation bug.
  Reliable sequence: `keyevent 4` → swipe to the bottom → wait ~2s → fresh
  `uiautomator dump` → tap.

- **`clickable` in a `uiautomator dump` is not a reliable enabled/disabled
  signal, in either direction.** It read `false` for a visibly enabled,
  Moss-filled CREATE ACCOUNT on at least two occasions on 2026-09-04, and
  Developer Settings' SAVE reports `clickable="false"` on the TextView
  permanently — its real tap target is the enclosing `android.view.View` a few
  nodes up, found by filtering the dump for `clickable="true"` and
  coordinate-matching. Same shape as the Auth screen's 10-tap gesture target,
  on a different screen. Screenshot to decide enabled-vs-disabled; use the dump
  only to locate the container to tap.

- **A dump parser that only matches self-closed `<node .../>` tags misses every
  clickable ANCESTOR.** Container nodes are written `<node ...>…</node>`, and
  on the Auth screen the SIGN IN CTA *is* such a container (it wraps the
  email/password/button group). Match `<node[^>]*>` generally, or you will
  conclude a working control does not exist.

- **A one-off visual anomaly is not adjudicable unless the frame is saved into
  the run folder.** PLAT-04 on 2026-09-04 produced one capture of a blank
  avatar chip, taken ~0.5–1s after a tab tap, that three later captures
  contradicted; the anomalous frames were written to `/tmp` and were gone by
  triage, so the only possible verdict was "driver artifact." When a capture
  disagrees with the ones around it, immediately re-capture after a full settle
  AND write both frames to the run's `screenshots/` directory with the entry ID
  in the filename. Tab-switch and other 150ms transitions are exactly where
  a too-fast screenshot manufactures an empty-looking control.

- **LAUNCH-06's keyset-restore recipe may not fault at all on the AVD.** Run
  verbatim on `Pixel_9_Pro` (2026-08-27) it produced no crash, no
  `token_storage_failure {op=discard}` telemetry, and a clean landing on
  AuthScreen — the app behaved exactly as a healthy cold start. The outer
  behavior the entry asserts (starts, signed out, no crash) is still
  observable and still worth a PASS; the `AEADBadTagException` self-heal path
  specifically is NOT confirmed by an emulator run. **Suspected cause, not
  measured:** the emulator's Keystore master-key alias may survive `pm clear`
  differently than a physical device's, so the restored keyset stays
  decryptable and the fault condition never arises. Say which half you proved
  in the results line rather than recording a bare PASS.

---

## Fault injection (specific status, single endpoint)

`arcana-server` exposes a dev-only fault injector for the error-path entries
that killing the server cannot reach: ERR-08 (login 5xx), ERR-10 (login
non-401/non-5xx), FAV-05 (favorites fails while the rest of the API is up),
and any similar single-endpoint case. Faults are armed on the SERVER, not via
a request header, because the app is the client and sends no header of ours.

```bash
BASE=http://localhost:8000   # this run's server port — 8010 on 2026-08-27,
                             # see runbook Phase 0.7; never assume 8000

# ERR-08 — login returns 500
curl -X POST $BASE/api/v1/_faults/ -H 'Content-Type: application/json' \
     -d '{"path": "/api/v1/auth/token/", "status": 500}'

# ERR-10 — login returns a non-401, non-5xx code
curl -X POST $BASE/api/v1/_faults/ -H 'Content-Type: application/json' \
     -d '{"path": "/api/v1/auth/token/", "status": 403}'

# FAV-05 — ONLY favorites fails; login, schedule, bookings stay real
curl -X POST $BASE/api/v1/_faults/ -H 'Content-Type: application/json' \
     -d '{"path": "/api/v1/users/me/favorites/", "status": 500}'

curl $BASE/api/v1/_faults/            # list what is armed
curl -X DELETE $BASE/api/v1/_faults/  # clear everything
```

Spec keys: `path` (required, prefix match on the request path), `status`
(required, 100-599), `method` (optional, defaults to any), `body` (optional,
defaults to a JSON `{"detail": ...}` so the client parses an error shape),
`times` (optional, expire after N matches so a retry gets through).

- **`path` is a PREFIX, and the auth paths nest.** `/api/v1/auth/token/` is a
  prefix of `/api/v1/auth/token/refresh/`, so arming the former faults login
  AND refresh. Arm the full `/refresh/` path when you mean only refresh
  (AUTH-12), and remember that an ERR-08/ERR-10 fault on `/auth/token/` covers
  refresh too, which is harmless pre-auth but not once a session exists.
- **`/api/v1/users/me/` nests too, and a `times` fault on it never survives to
  Edit Profile.** `/api/v1/users/me/favorites/` is under that prefix, and the
  app fetches favorites at cold launch and again on every Profile-tab load
  (`ProfileViewModel.fetchFavorites()`; visible as `api_request
  {endpoint=favorites}` in the telemetry echo). So a `{"path":
  "/api/v1/users/me/", "status": 500, "times": 1}` armed for ERR-16 is consumed
  by favorites before Edit Profile's own `GET /users/me/` ever fires, and Edit
  Profile then loads normally — which reads as "the screen cached the profile
  and never re-fetched." It did re-fetch: `EditProfileViewModel` calls `load()`
  from `init {}` unconditionally, every time the screen is constructed.
  Measured 2026-08-28: armed `times:1` on `/api/v1/users/me/`, one request to
  `/api/v1/users/me/favorites/` returned the injected 500 and dropped
  `remaining` to 0; the next `/api/v1/users/me/` passed straight through.
  **For ERR-16 (and anything else behind a screen the driver must navigate
  to), arm the fault with NO `times` key, drive the entry, then `DELETE`.** An
  unlimited fault cannot be eaten by request ordering; `times` exists for
  "let the retry through," not for aiming a fault at one call.
- **Clear faults between entries.** An armed fault has no timeout and will
  silently corrupt every later entry that touches that path. `DELETE` is the
  last step of any fault-driven entry, not an afterthought.
- Faults live in the server process's memory, so they also die on restart,
  and `runserver`'s autoreloader clears them on any file edit.
- Dev and test settings only. `prod.py` and `staging.py` hard-code the flag
  off AND omit the middleware from the stack, locked by
  `arcana/tests/test_staging_settings.py`.

**Expiring an access token on demand.** Entries that turn on a token actually
expiring (AUTH-12) would otherwise need a five-minute wait per attempt. Start
the dev server with a short lifetime instead:

```bash
ACCESS_TOKEN_LIFETIME_SECONDS=30 python manage.py runserver
```

Dev settings only; prod keeps 5 minutes, where a short lifetime would just
hammer the refresh endpoint. Refresh-token lifetime and rotation are
untouched, so only the access half shortens.

## Safety

- **CRITICAL: never kill the local server by a port-based `lsof` pattern
  when an Android emulator might also be running on the same Mac.**
  `lsof -ti :8000` (or similar) can match and kill unrelated processes
  bound near that port — this collaterally killed the `Pixel_9_Pro`
  emulator process itself in one shift. Always capture and kill the
  specific `manage.py runserver` PID(s) from `ps aux | grep manage.py`.
- **Killing and restarting your own server is the reliable CONNECTION-fault
  lever, and it is safe when done by PID.** Capture the run's own `manage.py`
  PID from `ps aux | grep manage.py`, `kill <that PID>`, drive the entry, then
  relaunch with the two safety overrides in the environment and re-prove them:
  ```
  OPS_NOTIFIER_CLASS=notifications.telegram.NullOpsNotifier \
  EMAIL_SENDER_CLASS=notifications.email.ConsoleEmailSender \
  python manage.py runserver 0.0.0.0:<this run's port> --noreload &
  ps eww <new PID> | grep -E 'OPS_NOTIFIER|EMAIL_SENDER'
  ```
  Under five seconds end to end, repeatable, and it produces the exact
  NETWORK_MESSAGE state the ERR-0x entries want (2026-09-04). Two hard
  boundaries: **never by port** (the rule above), and **never a server this run
  did not start** — a shift that drifts onto a pre-existing server's port and
  then kills it to inject a fault has broken runbook Phase 2.1 and destroyed
  another session's work, which is exactly what happened on 2026-09-04 while
  the run's own dedicated server sat idle on a different port.
- **Relaunching the AVD after any crash/kill resumes a quick-boot snapshot,
  not a clean boot.** It can carry a stale, non-regression signed-in
  account (observed: an unrelated "Android QA" account) and a suspended app
  process bound to stale in-memory state (producing instant, misleading
  `network_error` responses despite a correct Developer Settings override).
  Do a full `am force-stop` + relaunch for a genuinely fresh process, then
  explicitly re-sign-in as the correct regression account and re-check the
  Developer Settings base URL before trusting any post-recovery driving.
- **`pm clear org.arcana.mobile` wipes the Developer Settings base-URL
  override back to the prod default** (`https://api.arcana.fit`) along with
  all app data. Always re-verify/re-set the base URL immediately after any
  `pm clear`, before driving anything else, or subsequent requests will
  silently hit production.
- **Typing a reviewer email into the sign-in form retargets the base URL even
  if that sign-in FAILS, and it overwrites the Developer Settings override.**
  `AuthViewModel.login()` calls `ReviewerRedirect.applyFor(email)` *before*
  `api.login()`, and `applyFor` arms its persisted marker and calls
  `BaseUrlProvider.set(STAGING_URL)` on any reviewer-email attempt, success or
  failure. The local override is not saved anywhere: it is overwritten, and the
  later `clear()` restores the bundled **prod** default, not it. So after a
  failed reviewer login there is no Profile to sign out from, and the next
  ordinary sign-in ships real credentials at whatever host the marker resolves
  to. All three lanes hit this on 2026-08-27. Recovery, used three times and
  the only one that costs no network call: wipe the app's data (iOS `simctl
  shutdown` + `erase` + boot + `simctl install`; Android `am force-stop` +
  `pm clear`), then **re-set and re-verify the Developer Settings override
  before any other tap**. Never "just try the member login and fix it after."
- **PROFILE-14 (delete-account submit) is unsafe to drive on an unattended
  automated pass unless the ops-notifier override in runbook.md's Phase 2.1
  is confirmed in place.** `DeleteAccountViewModel.submit()` only posts an
  async concierge request — it does not delete the account immediately (see
  the code's own doc comment) — but that concierge request fans out through
  the same `MultiOpsNotifier` pipeline as every booking/cancel: Telegram
  **and** Pushover at hardcoded emergency priority (siren, DND-breaking,
  retried for hours) regardless of the call site's `urgent` flag. On a
  checkout whose `.env` wires the real notifier classes (verified
  2026-08-11 on this one), confirming Delete pages the founders for real.
  Use PROFILE-15 (the server-down failure path) as the safe substitute for
  exercising the same dialog machinery without submitting a real request.
- **DEVSET-08 ("Reset to default") points the app at production**, because the
  default it restores IS `https://api.arcana.fit` — Safety rule 0 forbids the
  run reaching prod even momentarily. The reset itself fires no request
  (`DeveloperSettingsViewModel.reset()` deletes the key, sets `Saved`, and the
  row's click handler calls `onClose()`; nothing in that path performs a
  network call), so the sanctioned way to drive it is: reset → immediately
  verify from the UI alone that the field and "CURRENTLY IN USE" now read
  `https://api.arcana.fit` and the screen closed to Auth → immediately re-enter
  Developer Settings and re-set this device's local override, **before any tap
  that could issue a request** (no sign-in, no relaunch, no tab). If you cannot
  hold that ordering, record BLOCKED with reason "reset would leave the build
  pointed at prod" rather than driving it. Same substitution logic as
  PROFILE-14 → PROFILE-15.

---

## Driver-bug protocol

Before recording any FAIL, **reproduce it a second time via a different
interaction path where possible** (e.g. a different tap sequence to reach the
same state, or the alternate platform-inspection tool). If the failure only
reproduces under one specific driving method, or doesn't reproduce at all on
the second attempt, record it as a ***suspect-driver* finding instead of a
FAIL** — name what's suspect (the exact action, dump staleness, a trap from
the list above) rather than asserting an app bug.

This matters because roughly half of the "app bugs" surfaced in early shell
regression sessions turned out to be driver mistakes, not real defects — a
report full of false alarms is worse than a shorter, trustworthy one. Applying
this protocol is what keeps the eventual regression report credible enough to
act on.

---

## Verification notes

Every command in this doc was checked against its tool's real usage output when
it landed, and the read-only ones were spot-run against a booted device. The
per-command transcript of that original 2026-08-11 audit added ~50 lines of
one-time evidence and was removed on 2026-08-28; the commands it validated are
the ones documented above, and each subsequent run re-validates them by using
them. Pinned facts worth keeping from it: `idb` lives only at
`~/.arcana-tools/idb-venv/bin/idb` (bare `idb` is not on PATH), `adb` is at
`/opt/homebrew/bin/adb`, and `uiautomator dump`'s success message misspells
"hierchary" — that is Android's own output, not a typo here.
