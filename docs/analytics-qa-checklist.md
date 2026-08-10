# Analytics QA Checklist (PostHog + Sentry)

A manual walkthrough to confirm **every** tracked event flows live into PostHog
(Activity feed + the "Beta — App Health & Usage (Mobile)" dashboard) and that
session replay + Sentry work, on **both** Android and iOS. Run before each App
Store / Play submission.

PostHog project: **439926** (US). Dashboard:
https://us.posthog.com/project/439926/dashboard/1710023

---

## 0. Setup

**Keys must be present** (otherwise SDKs no-op):
- Android: `sharedUI/analytics.properties` has `ARCANA_POSTHOG_API_KEY`, `ARCANA_POSTHOG_HOST`, `ARCANA_SENTRY_DSN`.
- iOS: `iosApp/Configuration/Secrets.xcconfig` has the same, and the PostHog + Sentry **SPM packages** are added to the iosApp target.

**Watch the live event log** (every event prints via the central `Telemetry` tag in debug builds):
- **Android:** `adb logcat -s Telemetry:D` → lines look like `D/Telemetry: ▶ login_succeeded {...}`
- **iOS:** Xcode console (debug area), filter on `D/Telemetry` → `D/Telemetry: ▶ login_succeeded`

**Watch PostHog live:** open *Activity* (live events). Events arrive within a few
seconds. Every event carries a `platform` property (`android` / `ios`) — use it
to confirm the source, and use the dashboard's top filter bar (`platform = ios`
or `android`) to slice all charts by OS.

**Deep link for the signup flow** (no in-app signup; it's invite-only):
- Android: `adb shell am start -W -a android.intent.action.VIEW -d "arcana://welcome?token=TOKEN" org.arcana.mobile`
- iOS: `xcrun simctl openurl booted "arcana://welcome?token=TOKEN"`
- A **bogus** token still opens the screen (fires `signup_started`) and lets you test the failure path; a **real** invite token is needed for `signup_completed`.

> Note: funnels (`Signup`, `Login`, `Booking`) only show conversion once you've
> completed the *whole* sequence at least once. Single events show up immediately
> in Activity and in the trend charts.

---

## 1. App open + identify

- [ ] Launch the app (signed out). → PostHog autocaptures `Application Opened`; log shows `▶ $screen Auth`.
- [ ] Sign in (see §2). → right after, Home/Profile loads `/me` and you see `▶ identify <memberId>` once. (Feeds **Active members (DAU/WAU)**, and ties every later event to the member.)

---

## 2. Login + session health  → *Funnel — Login*, *Health — Login failures*, *Sessions — Logouts*

- [ ] On the Auth screen, enter a **wrong** password → Sign in. → `▶ login_submitted`, `▶ login_failed {reason: invalid_credentials}`.
- [ ] Turn on Airplane mode, try Sign in → `▶ login_failed {reason: network}`. Turn Airplane mode off.
- [ ] Enter **correct** credentials → Sign in. → `▶ login_submitted`, `▶ login_succeeded`, then `▶ identify …`. Lands on Home (`▶ $screen Home`).
- [ ] (later, after exploring) Profile tab → **Sign out**. → `▶ logout {type: manual}`, `▶ reset`.
- [ ] *(Optional / advanced)* Forced logout: while signed in, invalidate the refresh token server-side (rotate it / end the session / change password elsewhere), then navigate or pull-to-refresh to force an authed call. → `▶ forced_logout {cause: refresh_error}`, `▶ reset`. Hard to trigger casually — skip if no server access.

---

## 3. Signup funnel  → *Funnel — Signup*, *Health — Errors overview*

- [ ] Open a welcome deep link with a **bogus** token (cmd in §0). → `▶ $screen SignupCompletion`, `▶ signup_started`.
- [ ] Fill first name, last name, phone, password + confirm → Submit. → `▶ signup_submitted`, then `▶ signup_failed {reason: token_expired}` (bogus token).
- [ ] *(If you have a real invite token)* repeat with a valid token and complete it. → `▶ signup_submitted`, `▶ signup_completed`, `▶ identify …`. Full funnel converts.

---

## 4. Navigation + schedule usage  → *Screen views*, *Tab taps*, *Schedule day changes / filter mode / browse depth*

- [ ] Tap each bottom tab (Home, Schedule, Profile). → `▶ tab_tapped {tab: …}` + `▶ $screen …` for each.
- [ ] On Schedule, tap a **different day chip**. → `▶ schedule_day_changed {method: chip_tap, direction, day_offset_from_today}`.
- [ ] **Swipe** left/right on the schedule list to change day. → `▶ schedule_day_changed {method: swipe}`.
- [ ] Open the filter → switch between **Favorites / All Studios / Custom**, toggle a studio or location. → `▶ schedule_filter_changed {mode, studio_count, location_count}` per change.
- [ ] Scroll to the bottom of a busy day (needs >1 page of classes) to load more. → `▶ schedule_load_more {page_index, day}`. *(Won't fire if the day has only one page — fine.)*

---

## 5. Class view → booking funnel  → *Funnel — Booking*, *Class views by studio*, *Booking failures*, *spot picks & cancel intent*

- [ ] Tap a class row. → `▶ $screen ClassDetail`, `▶ class_viewed {session_id, studio_id, studio_name, location_id, location_name, modality, spots_available, requires_spot, is_full, load_ms}`.
- [ ] On a **bookable** class, tap the Reserve/Request CTA. → `▶ booking_sheet_opened`.
- [ ] If the class has **spot selection**, tap a spot. → `▶ spot_selected {spot_id, spot_label}`.
- [ ] **Dismiss** the sheet without confirming. → `▶ booking_sheet_abandoned {reached_spot_selection, had_selected_spot}`.
- [ ] Re-open and **confirm** the booking (need credits). → `▶ booking_submitted`, `▶ booking_succeeded {booking_id, status, studio_id, location_id, has_spot}`.
- [ ] Try to book something you can't (out of credits / full / already booked). → `▶ booking_failed {reason_code, session_id}`.
- [ ] *(Optional)* tap a class then kill the network mid-load → `▶ class_view_failed`.

---

## 6. Cancellation  → *Engagement — Cancellations*, *spot picks & cancel intent*, *Errors overview*

- [ ] On a booked class, tap **Cancel**. → `▶ booking_cancel_started {booking_id, session_id, will_forfeit_credit}`.
- [ ] **Confirm** the cancel. → `▶ booking_cancelled {credit_refunded, late_cancel, studio_id, location_id}`.
- [ ] *(Optional)* cancel with network off → `▶ booking_cancel_failed`.

---

## 7. Favorites (by studio + location)  → *Favorites — Added by studio / by location / adds-removes-saves*

- [ ] Go to manage Studios/Favorites. → `▶ $screen StudioSelection`.
- [ ] Toggle a **studio** on, a **location** on, then toggle one **off**, and **Save**.
  → `▶ favorite_added {type: studio, studio_id, studio_slug, studio_name}`,
  `▶ favorite_added {type: location, location_id, location_name, …}`,
  `▶ favorite_removed {…}`, `▶ favorites_saved {studio_count, location_count, …}`, and a `$set` person-property update (`▶ $set …`).

---

## 8. Concierge / support  → *Support — Concierge requests*

- [ ] Profile → Concierge/contact. → `▶ $screen ConciergeRequest`.
- [ ] Type a message → Submit. → `▶ concierge_request_submitted`.
- [ ] *(Optional)* submit with network off → `▶ concierge_request_failed {reason}`.

---

## 9. Session replay (PostHog)

- [ ] Use the app normally for ~30s, then background it.
- [ ] PostHog → **Session Replay** → confirm a recording appears for your session, and that **all text/inputs are masked** (no readable PII). Recordings can take a minute or two to process.

---

## 10. Sentry (crashes + nonfatals)

- [ ] **Nonfatal:** any of the optional "network off" failure steps above route handled errors through `telemetry.recordError`. Confirm they appear in the **arcana-android** / **arcana-ios** project → Issues. (Quickest deterministic test: temporarily add `throw RuntimeException("sentry test")` behind a debug tap, run, remove it.)
- [ ] **Crash:** force an uncaught crash once; confirm it lands in Sentry. iOS stacks symbolicate once dSYMs upload (Archive build); Android stacks are already readable (minify off).
- [ ] Confirm the signed-in member is attached to the issue (from `identify`/`setUser`).

---

## 11. Cross-platform check

- [ ] Repeat §§1–9 on the **other** platform (Android then iOS, or vice-versa).
- [ ] In the dashboard, set the date to **Today** and add a `platform` filter → flip between `android` and `ios` → confirm both sources show data and the charts render.

---

## Event coverage (tick once seen live at least once)

Auth/session: `$screen` · `signup_started` · `signup_submitted` · `signup_completed` · `signup_failed` · `login_submitted` · `login_succeeded` · `login_failed` · `logout` · `forced_logout` · `identify`
Nav/schedule: `tab_tapped` · `schedule_day_changed` · `schedule_filter_changed` · `schedule_load_more`
Booking: `class_viewed` · `class_view_failed` · `booking_sheet_opened` · `spot_selected` · `booking_sheet_abandoned` · `booking_submitted` · `booking_succeeded` · `booking_failed`
Cancel: `booking_cancel_started` · `booking_cancelled` · `booking_cancel_failed`
Favorites: `favorite_added` · `favorite_removed` · `favorites_saved`
Support: `concierge_request_submitted` · `concierge_request_failed`
Plus: PostHog `Application Opened` (autocapture), session replay, Sentry issue.
