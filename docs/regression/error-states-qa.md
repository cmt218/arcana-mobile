# Error States QA Suite

A focused, driver-executable pass over every error state the app can show. Narrower than
`inventory.md` (which covers the whole app) and deeper on this one surface: it pairs each
state with the exact command that forces it.

**Companion tooling:**
- `tools/regression/error-state-harness.sh` — forces every state. Run `preflight` first.
- `docs/regression/driver-playbook.md` — how to drive iOS (idb) and Android (adb).
- `docs/regression/inventory.md` — the ERR-* entries these map to.

**Devices:** iOS 18.5 (iPhone 16 Pro sim) · iOS 26.3 (iPhone 17 Pro sim) · Pixel_9_Pro AVD.

---

## Before you start

```bash
./tools/regression/error-state-harness.sh preflight
```

Three things must be true, and none of them are verifiable from inside the app:

1. **Local server only.** Developer Settings (10 taps on the signed-out wordmark) must read
   `http://localhost:8000`. On Android — emulator or a real phone on USB — pair that with
   `adb reverse tcp:8000 tcp:8000`. **Do not use `10.0.2.2:8000`**: it did not reach the host
   here and requests went silently to the prod default instead. The default is **prod**, and any
   storage clear resets it there with no visible indication, so a login failure against prod
   looks identical to a bad password. Prove it with `harness assert-local`, drive the app, then
   `harness assert-local check`.
2. **Ops paging and real email are off.** `harness start` overrides them per-process. Never
   start the server any other way: `.env` points at the real Pushover/Telegram notifier
   (emergency priority, DND-breaking, 3h retry) and the real Loops email sender.
3. **Android target is the emulator.** If Cole's physical Pixel is plugged in, every bare
   `adb` command is ambiguous. The harness pins `-s` to the emulator; a command you type
   yourself does not.

---

## The two copy families

Everything below is one of these. Learning them once makes the whole pass fast.

| | CONNECTION | SERVER |
|---|---|---|
| **Overline** | `Connection` (muted) | `Server` (Burnt Nectar) |
| **Full-screen headline** | `Can't reach Arcana.` | `Something's off on our end.` |
| **Full-screen body** | `Check your connection and try again.` | `Give it a moment and try again.` |
| **Inline headline** | `Can't load this right now.` | `This didn't load.` |
| **Inline body** | `Check your connection.` | `On our end. Try again.` |

**The single most important check across the whole pass:** CONNECTION copy must never say
"server error", and the two must be unmistakably different. That confusion is the production
bug this work exists to fix — a member was told the server failed while the server was
healthy and their connection had blipped.

---

## ES-01 · Home cold load, CONNECTION
**Forces:** `harness kill-server`, then cold-launch the app onto Home.
**Expect:** Full-screen error **replacing** the whole list, vertically centred. `Connection`
overline with its small Lime dot, `Can't reach Arcana.`, `Check your connection and try again.`,
TRY AGAIN button.
**Watch for:** the TopBar/greeting header must be **gone**, not sitting above the error, and no
shimmering name placeholder. TRY AGAIN must be fully visible without scrolling.
**Covers:** ERR-05. Restore with `harness start`.

## ES-02 · Home cold load, SERVER
**Forces:** `harness db-down` (server stays reachable, DB calls fail), cold-launch.
**Expect:** Same layout, `Server` overline (Burnt Nectar dot), `Something's off on our end.`
**Watch for:** visibly distinct from ES-01 at a glance. Restore with `harness db-up`.

## ES-03 · Retry recovers, on EVERY full-screen error
**Forces:** from ES-01, `harness start`, then tap TRY AGAIN. Repeat on Schedule and
Class Detail, which are the same component and must behave identically.
**Expect:** the button's label is replaced by the dot-matrix loader while the re-fetch
runs, the error stays on screen, then it settles on content.
**Watch for:** the screen must NOT flash the loading skeleton and drop back to the same
error. That was visible on Schedule (month header + day rail) and Class Detail, and was
invisible on Home only because an offline fetch fails too fast to see. Repeat taps while
still down are ignored (no stacking).
**Also covers the inline retries:** My Bookings and the Schedule day-chip card behave the
same way — the card stays put and its Retry link shows the loader.

## ES-04 · Home refresh failure keeps content
**Forces:** load Home successfully, `harness kill-server`, pull to refresh.
**Expect:** content **stays**. Dark Ink toast at the bottom: `Couldn't refresh. Showing your last
update.` with a Lime Retry and an X.
**Watch for (iOS):** the toast must clear the floating glass tab bar, not sit under it.
**Covers:** new entry. This is the behavior that must never regress into wiping good content.

## ES-05 · Toast dismiss
**Forces:** from ES-04, tap the X.
**Expect:** toast clears and **stays** cleared. Tapping Retry while still offline brings it back;
tapping X does not. Verify the X is comfortably tappable, not a pinpoint.

## ES-06 · Schedule cold-start, CONNECTION / SERVER
**Forces:** `kill-server` (then `db-down`) and open the Schedule tab with no cached data.
**Expect:** full-screen error, RETRY wired to reload. The old "COULDN'T LOAD SCHEDULE" block is gone.
**Covers:** ERR-01.

## ES-07 · Schedule refetch keeps content
**Forces:** load Schedule, `kill-server`, then change a filter or tap a TIME preset.
**Expect:** content **stays**, no takeover, dim clears. The staleness guard is the most
regression-sensitive behavior in this file.
**Covers:** ERR-02.

## ES-08 · Uncached day chip **(the silent one)**
**Forces:** load Schedule, `kill-server`, tap a day chip you have not visited under the current filters.
**Expect:** an **inline error card in the day's list area** with an underlined Retry.
**Watch for:** it must NOT sit on the dot-matrix loader. Before this work the day area showed
literally nothing — indistinguishable from still-loading, with no timeout behind it. A trained
driver lost real time to this thinking the app had frozen.
**Covers:** ERR-03.

## ES-09 · Day-chip retry recovers
**Forces:** from ES-08, `harness start`, tap Retry.
**Expect:** that day's classes load; the error clears.

## ES-10 · Day error survives a failed refresh
**Forces:** from ES-08 (error showing, server still down), pull to refresh.
**Expect:** the day error is **still there** afterwards. It must not silently clear back to a
bare spinner. This exact regression was introduced and caught during implementation.

## ES-11 · Favorites scope survives retry **(iOS especially)**
**Forces:** with favorites saved, `kill-server`, cold-launch to Schedule (both fetches fail),
`harness start`, tap RETRY.
**Expect:** schedule loads **and** the scope toggle still reads **Favorites**, not All Studios.
**Covers:** SCHED-02. Root cause was subtler than the card said: a 5xx used to deserialize into
an empty-but-valid favorites object and report success, so the app believed the member had none.

## ES-12 · Class Detail, CONNECTION / SERVER
**Forces:** open a class, `kill-server`, back out and re-enter.
**Expect:** error renders **below the close bar**, and the **X still works**. Both variants distinct.
**Covers:** ERR-04.

## ES-13 · My Bookings
**Forces:** `kill-server`, then Home → "See all".
**Expect:** an **inline card** under the "YOUR BOOKINGS" header with an underlined Retry —
**not** a full-screen takeover. Previously a bare caption with no retry at all.
**Covers:** ERR-06.

## ES-14 · Profile snackbar
**Forces:** `kill-server`, open Profile.
**Expect:** a dark **snackbar pinned to the bottom** with a Lime Retry, reading `Couldn't reach
Arcana.` (CONNECTION) or `Couldn't load your profile.` (SERVER). It replaced an easily-missed
caption on the hero. The hero itself keeps its shimmer placeholders behind the snackbar.

## ES-15 · Booking failure distinguishes the two **(most important action in the app)**
**Forces:** `kill-server` → confirm a booking. Then `harness start; db-down` → confirm again.
**Expect:** CONNECTION → `Couldn't reach Arcana. Check your connection and try again.`
SERVER → `Something went wrong on our end. Try again in a moment.`
**Watch for:** these must differ. Both used to render the identical generic line.
A typed server reason (e.g. class full) must still win over both.
**Covers:** ERR-11. **Confirm ops paging is off before driving this.**

## ES-16 · Cancel failure distinguishes the two
**Forces:** same two levers, on a cancel.
**Expect:** the two categories read differently. Re-tapping CANCEL BOOKING is still the retry
(no dedicated retry control was added — that half is a separate design decision).
**Covers:** ERR-12.

## ES-19 · Concierge submit distinguishes the two
**Forces:** Profile &rarr; Concierge, type a message, then `kill-server` (CONNECTION) or
`db-down` (SERVER), and tap SEND.
**Expect:** CONNECTION -> `Couldn't reach Arcana. Check your connection and try again.`
SERVER -> `Something went wrong on our end. Try again in a moment.`
**Watch for:** these must differ. Until this branch, Concierge showed one fixed line that
ignored the error entirely and blamed us either way.
**Two driving traps:** the Concierge row on Profile sits under the floating tab bar (scroll the
list up first), and SEND stays disabled until the message field has text (check the N/1000
counter moved).

## ES-17 · Request timeout **(sharpest single check)**
**Forces:** start a load, then `harness stall` (SIGSTOP — socket stalls, no bytes flow).
**Expect:** the CONNECTION state, and crucially **not** an endless spinner.
**Timing differs by platform and that is expected:** Android fails at ~30s (it honours the
socket timeout); iOS at ~60s (Ktor's Darwin engine ignores it, so only the request timeout
bounds it). Do not fail the entry on iOS for taking longer. Trello vVs2x4jG.
**Watch for:** before this work, this hung **forever** — no error, no timeout, just a spinner.
If it still hangs, the timeout is not installed correctly. `harness unstall` to recover.
**Run on iOS as well as Android** — nothing automated covers the real HTTP client.

## ES-18 · Silent-success regression sweep **(highest risk in the branch)**
These four used to render an *empty state* on a 5xx and now render an error. Drive each with
`harness db-down`, then **restore with `db-up` and confirm the happy path still works** — that
second half is the actual regression check.

- Favorites / Studio Selection → error, **not** "no favorites saved"
- Schedule → error, **not** an empty day with zero classes
- Profile → error, **not** a blank profile
- **Edit Profile save** → the form error banner fires, **not** a silent "saved" that never persisted

**Caveat before you conclude "didn't reproduce":** this only ever happened for **JSON** error
bodies. An HTML 5xx (a Django debug page, a Cloud Run 503) threw a different exception and did
not silently succeed. Check the response content type before calling it.

## ES-20 · Studio Selection, CONNECTION / SERVER
**Forces:** `kill-server`, then Profile → Manage (or Schedule → "Manage Favorites"). Repeat with
`db-down` for the SERVER variant.
**Expect:** the shared full-screen error, both variants distinct — no more one fixed
"Couldn't load Studios." for every failure. The **sticky Save bar is absent** in this state (it
renders only for `Ready`), so nothing covers the retry. Tapping Retry keeps the error on screen
while it runs rather than flashing the loader; restore the server and Retry reaches the picker.
**Covers:** ERR-14.

## ES-21 · Edit Profile load failure, CONNECTION / SERVER
**Forces:** `kill-server`, then Profile → Edit. Repeat with `db-down`.
**Expect:** the shared full-screen error with the **close (X) still working** on top of it, the
same shape as ES-12. Both variants distinct — the old fixed "Couldn't\nload." headline and its
"Pull to retry." body are gone, along with the pull gesture that copy named and this screen never
had. Retry keeps the error on screen while it runs.
**Covers:** ERR-16.

---

## Cross-cutting, on every device

- [ ] No em or en dashes in any copy on screen
- [ ] Button labels optically centred — not a pixel high, not a pixel left.
      Measure it rather than eyeball it, on a screenshot of ES-01:
      `tools/regression/measure_centering.py <shot.png> 283b15 3 <x0 y0 x1 y1>`
      (crop box required — Moss also appears in the tab bar and wordmark, and an
      unconstrained search unions them into one huge "button" and reports a
      meaningless 0.0px). Under 0.5pt on both axes passes.
- [ ] Full-screen block stays lower-third on a small screen, nothing clipped
- [ ] iOS: nothing hidden under the floating glass tab bar
- [ ] Every retry control is comfortably tappable

## iOS 26 note

The 2026-08-11 run recorded ten consecutive connection-refused entries FAILing on iOS 26.3 with
the app terminating to the springboard. Triage could not reproduce it in 15+ attempts and no
crash report has ever existed, so it is **Unconfirmed** and is not a work item.

Drive the iOS 26 pass with the console attached anyway:

```bash
xcrun simctl launch --console-pty booted org.arcana.mobile
```

If it recurs, that captures the escaping exception type — genuinely new evidence. Do **not**
"fix" it with a blanket `catch (e: Throwable)`; that would swallow real programming errors.

---

## When you're done

```bash
./tools/regression/error-state-harness.sh reset
./tools/regression/error-state-harness.sh telemetry-check   # MUST exit 0 before any PR
```

`telemetry-check` is not optional. QA blanks the PostHog key so local runs don't pollute the
production project, and both key files are **gitignored** — a forgotten restore produces no
diff at all, so code review cannot catch it and the next release would silently ship with no
product analytics.
