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
- `adb shell input text '<str>'` — escape spaces as `%s`. Two characters this
  app's regression fixtures use are mishandled: `@` is silently **dropped**
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
  Tab-traversal limitation.
- `adb shell svc wifi disable` / `adb shell svc wifi enable` (and the `svc
  data` equivalents) — a clean, lower-risk way to force a network failure
  than killing the local server: no risk of collaterally killing another
  process on the host (see the CRITICAL SAFETY note below), and it recovers
  instantly on re-enable + RETRY.
- `adb shell run-as org.arcana.mobile <cmd>` — reads a debug-installed app's
  private files with no root needed, e.g.
  `adb shell run-as org.arcana.mobile rm shared_prefs/arcana_secure_prefs.xml`
  to simulate an externally-cleared secure store. **This also wipes the
  Developer Settings base-URL override**, since `BaseUrlProvider` persists
  into the same file — the app silently reverts to the prod default
  (`https://api.arcana.fit`) with zero visible indication on AuthScreen, and
  a subsequent login just shows the generic "email and password don't
  match," easily mistaken for a real credential mismatch or a stuck button.
  After any full-secure-prefs clear, re-open Developer Settings and confirm
  "CURRENTLY IN USE" reads the expected local URL before treating a login
  failure as an app bug.

**Telemetry.** `adb logcat -s Telemetry:D` is the primary technique for the
whole TEL block — reliable, cheap, and precise (exact event name + full
property map), far better evidence than inferring correctness from UI state.
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
  it for a "5xx" entry, not a "connection-refused" one. Neither recipe can
  target a single endpoint; killing the server (or the DB) affects every
  endpoint at once. Developer Settings' base-URL override is only reachable
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
  first, or dismiss the keyboard with `adb shell input keyevent 111` (ESC —
  it does not trigger the system BACK gesture).

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
- **System back on PRE-AUTH secondary screens exits the whole app to the
  Android launcher instead of popping one nav step** — reproduced on
  PasswordResetRequestScreen, SignupSurveyScreen, and SignupCompletionScreen
  (the claim form), with/without the IME open, cold and warm launches.
  Relaunching afterward does NOT restore in-progress state — SignupSurvey
  answers and claim-form field values are silently discarded, no confirm
  dialog. `adb shell input keyevent 4` IS SAFE specifically for dismissing
  the IME (it only closes the keyboard) — the app-exit bug only fires on a
  back press with the IME already closed. This is narrow to these three
  pre-auth screens: system back on authenticated non-tab destinations
  (Class Detail, My Bookings, Edit Profile, etc.) correctly pops the nav
  stack as expected — don't conflate the two.
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

---

## Safety

- **CRITICAL: never kill the local server by a port-based `lsof` pattern
  when an Android emulator might also be running on the same Mac.**
  `lsof -ti :8000` (or similar) can match and kill unrelated processes
  bound near that port — this collaterally killed the `Pixel_9_Pro`
  emulator process itself in one shift. Always capture and kill the
  specific `manage.py runserver` PID(s) from `ps aux | grep manage.py`.
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

## Verification notes (2026-08-11)

Every command above was checked against its tool's real `help`/usage output
before landing in this doc; where a booted device was available, the
read-only ones were spot-run live rather than only checked against `--help`.

- `~/.arcana-tools/idb-venv/bin/idb ui describe-all --udid <UDID>` → spot-run
  live against a freshly booted iPhone 17 Pro (iOS 26.3) simulator: returned
  the full accessibility JSON (AXFrame etc.), exit 0. The companion
  auto-spawn path works from the venv binary. (This was a separate, later
  verification pass on its own booted device — not the iPhone 17 Pro Max /
  iPhone 16 Pro pair in the "Two simulators were booted" note below.)
- `~/.arcana-tools/idb-venv/bin/idb list-targets` → confirmed working,
  listing simulators. The idb CLI lives in its own venv rather than on bare
  `PATH` (bare `idb` still resolves to nothing on this Mac — always use the
  full venv path, per the Invocation note above). `idb_companion` is on
  `PATH` (`/opt/homebrew/bin/idb_companion`) via Homebrew, as usual.
- `which adb` → `/opt/homebrew/bin/adb`. Confirmed working.
- `xcrun simctl help | head -5` → confirmed `simctl` exists and the `help`
  subcommand works.
- `xcrun simctl help io` / `help openurl` / `help launch` → confirmed
  `io screenshot`, `openurl <device> <URL>`, and `launch [--console-pty]
  <device> <bundle-id>` are real subcommands with the flags used above.
- Two simulators were booted at verification time (iPhone 17 Pro Max, iOS
  26.3; iPhone 16 Pro, iOS 18.5), and `~/.arcana-tools/idb-venv/bin/idb
  list-targets` listed both. Both the *companion* half of the stack and the
  `idb` CLI half now check out; the CLI's py3.14 patch (see the Invocation
  note above) is already applied in the venv.
- `adb shell uiautomator dump` was spot-run live against a connected physical
  Android device (Pixel 9 Pro) and completed successfully ("UI hierchary
  dumped to: /sdcard/window_dump.xml" — the misspelling is Android's own
  output, not a typo here). `adb shell input` (no args) printed real usage
  confirming `tap`, `swipe`, `text`, and `keyevent` are genuine subcommands.
