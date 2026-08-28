# Arcana Mobile — Regression Inventory

Entry IDs are stable and never reused. Deleted entries leave a tombstone line in place: `### <ID> — RETIRED (<date>): <reason>`. Any PR changing user-facing functionality must update this file (see CLAUDE.md "Regression inventory" section). This file is the checklist consumed by the `/full-regression` suite (docs/regression/runbook.md).

**Source-path convention (the Phase 1 reverse pass depends on it).** Every path on a `- **Source:**` line must be a **full repo-relative path to a concrete file** that resolves with `test -f`. Do not abbreviate a package prefix as `sharedLogic/.../schedule/Foo.kt` — an elided path can never detect a rename, which is the whole point of the reverse pass. Do not cite a bundle directory (`*.xcassets`, `*.icon`) either; name a concrete file inside it (e.g. `.../LaunchBackground.colorset/Contents.json`). Parenthetical annotations after a path are fine and may contain commas.

**Line numbers in those annotations are hints, not contract.** Where a Source
line carries a parenthetical like `(lines 584-635)` or `(line 144 …)`, treat it
as a navigation aid that drifts the moment anything above it in the file moves.
The **file paths** are the contract the Phase 1 reverse audit enforces (it
strips parentheses before extracting, so it never checks a line number); a
stale line number is not a finding. Prefer naming a symbol over a line number
when adding a new annotation.

**Consciously-accepted exclusions.** None. Every user-facing surface the Phase 1 forward pass enumerates (ViewModel declarations, `*Screen.kt`, nav destinations) currently traces to at least one entry — including the debug-only Developer Settings screen, which is covered by the DEVSET area rather than excluded, because a wrong base URL there silently sends a tester's whole session at the wrong server. If a future surface is deliberately left untested, list it here with a one-line reason instead of letting it surface as a recurring Phase 1 finding.

**230 entries across 14 areas** (keep this line and the runbook's pinned count in sync when adding an entry): LAUNCH 6, AUTH 16, SIGNUP 24, HOME 20, SCHED 19, CLASS 26, FAV 9, PROFILE 26, CONCIERGE 4, DEVSET 11, NAV 13, ERR 22, TEL 23, PLAT 11. CONCIERGE was added during the 2026-08-11 completeness sweep (the Concierge Request screen, reached from Profile, had no coverage in the original 13-area plan); a new top-level surface warrants a new area section rather than squeezing into a neighbor. ERR grew from 20 to 22 on 2026-08-16 (`feature/error-states-completion`): ERR-21 (Home refresh-failed toast) and ERR-22 (client request timeout) are new surfaces this branch created; every other area's count is unchanged — that work rewrote several existing entries' Expected text in place but added no new surfaces outside ERR. NAV grew from 12 to 13 on 2026-08-19 (`fix/NAV-13-pre-auth-system-back`): NAV-13 covers the new confirm-to-leave dialog, and NAV-12's Expected was rewritten in place because it had encoded the defect as intended behavior. TEL grew from 21 to 23 on 2026-08-22 (`fix/HAZARD-posthog-release-only-prod`): TEL-22 (analytics gate) and TEL-23 (Sentry environment tagging) cover the new gate, and TEL-20's Expected was corrected since a Debug build no longer reaches PostHog at all. AUTH grew from 14 to 15 on 2026-08-24: AUTH-15 covers soft-keyboard CTA reachability, which no entry had claimed, and which a run had mis-scored as an app bug because the layout dump omits the keyboard. PLAT grew from 9 to 10 on 2026-08-24: PLAT-10 records the measured 48dp touch-target floor, after a card claimed icon controls were 36dp targets. PLAT-11 was added 2026-08-25 (`fix/UI-textlink-optical-centring`): no entry covered optical centring at all, and the fix turned on which of the two helpers a component needs, so the distinction is recorded rather than left to the next reader to rediscover. PROFILE-02's Expected was rewritten in place on 2026-08-26 (`profile-credits-empty-state`) for the same reason NAV-12's was: it had encoded the defect as intended behavior, describing the endless Credits shimmer as the expected no-wallet rendering. No count changed. AUTH grew from 15 to 16 on 2026-08-24 (`App-review accounts sign in against staging`): AUTH-16 landed with the entry but not with this count line, which is why the run of 2026-08-27 reconciled 230 entries against a doc claiming 229 — the count was corrected on 2026-08-28, and the entry itself was already correct. HOME-09, CLASS-21 and SIGNUP-06 had their Expected text corrected in place on 2026-08-28 after that run read each one against its Source.

## LAUNCH

### LAUNCH-01 — Cold start, unauthenticated, splash minimum display
- **Steps:** Fresh install (or Keychain/app-data cleared), launch the app with no stored session.
- **Expected:** The dot-matrix splash (moss field, flickering dots settling into the wordmark, then a breath pulse) is visible for at least `SPLASH_MIN_DISPLAY_MS` before it fades (300ms fade) to reveal the Auth screen. The splash never disappears earlier than the dance+settle+200ms tail, even on a fast device.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/SplashScreen.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt, sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/SplashHost.kt, iosApp/iosApp/ArcanaShell.swift
- **Platforms:** shared

### LAUNCH-02 — Cold start, authenticated (session restore)
- **Steps:** With a previously-logged-in session (valid stored token), force-quit and relaunch the app.
- **Expected:** Splash plays its minimum duration, then the app lands directly on the Home tab (no Auth screen shown). Underlying data fetches (Home/Profile) begin during the splash window so content is ready, not shimmering, the instant the splash fades.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`isAuthenticated` gate, `AppStartTracker.onFirstContent`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/IosShellBridge.kt (`isAuthenticated()`), iosApp/iosApp/ArcanaShell.swift (`buildControllers(authenticated:)`)
- **Platforms:** shared

### LAUNCH-03 — App-start telemetry fires exactly once per process
- **Steps:** Cold-launch the app (authenticated or not) and watch the debug telemetry echo (`▶ Telemetry` in logcat / Xcode console).
- **Expected:** `app_start_completed` fires exactly once, carrying `start_type=cold` and `authenticated` matching the actual session state at first content. A second call (e.g. tab switch, backgrounding) does not re-fire it.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/AppStartTracker.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt, sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/TabRoots.kt
- **Platforms:** shared

### LAUNCH-04 — First-launch welcome-token recovery grace period
- **Steps:** Fresh install, sign out state, launch the app with NO deep link pending (plain app-icon tap). On Android, this exercises the Play Install Referrer lookup.
- **Expected:** The app waits `RECOVERY_DEEP_LINK_GRACE_MS` (700ms) before consulting the platform recovery source, so a deep link that arrives within that window wins and the recovery source is never queried (avoiding, on iOS, a pasteboard-permission prompt). The recovery attempt is marked persistently and only ever runs once per install, even across later launches. **Observable half:** repeated plain icon launches land on the Auth screen with no spurious routing and, on iOS, no pasteboard prompt. **Not observable:** the 700ms window and the once-per-install marker — `attemptFirstLaunchRecovery` emits no telemetry, so score the outer behavior and say in the result line which half you proved.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/session/AppSessionController.kt (`attemptFirstLaunchRecovery`, `RECOVERY_ATTEMPTED_KEY`, `RECOVERY_DEEP_LINK_GRACE_MS`)
- **Platforms:** shared
_Annotated after run 2026-08-27: two lanes blocked this outright for "no telemetry or UI signal", which is true of the timing half and not of the entry as a whole. `AppSessionController.kt` references `Telemetry` nowhere; instrumenting it is app work, not a driver technique._

### LAUNCH-05 — iOS launch screen has no white flash
- **Steps:** Cold-launch the iOS app and watch the very first frames before the Compose splash mounts.
- **Expected:** The native launch screen background renders the `LaunchBackground` (MossDeep) colorset, not white/system default, so there is no flash before the dot-matrix splash takes over.
- **Source:** iosApp/iosApp/Assets.xcassets/LaunchBackground.colorset/Contents.json, iosApp/iosApp/iOSApp.swift (per CLAUDE.md "iOS Liquid Glass shell" section)
- **Platforms:** iOS-only

### LAUNCH-06 — Cold start with an unreadable secure store self-heals instead of crashing
- **Steps:** Android only. Launch once so `shared_prefs/arcana_secure_prefs.xml` exists, then stage a phone-to-phone restore: save that file off-device (`adb shell run-as org.arcana.mobile cat shared_prefs/arcana_secure_prefs.xml`), run `adb shell pm clear org.arcana.mobile` (which also drops the app's Keystore entries), relaunch so a fresh master key and keyset are minted, force-stop, then write the saved file back over the new one. Cold-launch.
- **Expected:** The app starts. `EncryptedSharedPreferences.create` throws (`AEADBadTagException`, caused by `KeyStoreException` internal code -30 / `VERIFICATION_FAILED`) because the restored keyset was encrypted under a master key that no longer exists; `SecureStorage` deletes the prefs file plus the `_androidx_security_master_key_` alias, rebuilds the store empty, and the member lands on AuthScreen signed out. Exactly one `token_storage_failure {op=discard, key=__store__, os_status=-2147483647}` fires. A second cold launch fires no further `discard` — the heal is one-shot, not per-launch. Before this existed the process died in Koin DI before any UI (ARCANA-ANDROID-9).
- **Source:** sharedLogic/src/androidMain/kotlin/org/arcana/mobile/auth/SecureStorage.android.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/SecureStorageDiagnostics.kt, sharedUI/src/androidMain/AndroidManifest.xml, sharedUI/src/androidMain/res/xml/data_extraction_rules.xml, sharedUI/src/androidMain/res/xml/backup_rules.xml
- **Platforms:** Android-only

## AUTH

### AUTH-01 — Cold-start lands on login (no welcome token, unauthenticated)
- **Steps:** Fresh install / signed-out cold start with no pending welcome deep link. Observe the first screen shown.
- **Expected:** `AuthScreen` renders directly (sign-in only — there is no in-app sign-up entry). Header reads "Sign in" / "Welcome back." with email + password fields and a "Sign in" CTA.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/AuthScreen.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt, sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt
- **Platforms:** shared

### AUTH-02 — Login success
- **Steps:** On AuthScreen, enter a valid member email + password, tap "Sign in" (or Done on the keyboard from the password field).
- **Expected:** CTA swaps to a Moss pill with a Lime spinner while in flight (`AuthUiState.Loading`). On success `AuthViewModel.uiState` becomes `Success`, `ArcanaApiClient.isAuthenticated` flips true, and the app transitions into the authenticated shell (AuthFlowRoot renders inert Stone for one frame during the swap on iOS; App.kt swaps directly on Android).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/AuthScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/AuthViewModel.kt
- **Platforms:** shared

### AUTH-03 — Wrong email/password shows inline credential error
- **Steps:** On AuthScreen, submit an email/password combination that returns 401.
- **Expected:** `AuthViewModel.login` sets `AuthUiState.Error(isCredentialError = true)`. The message "That email and password don't match. Double-check and try again." renders as the Password field's inline `error`, not as a general banner. The form is not cleared.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/AuthViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/AuthScreen.kt
- **Platforms:** shared

### AUTH-04 — Server 5xx / network failure on login shows general banner
- **Steps:** Submit valid-looking credentials while the server returns a 5xx, or with no network connectivity.
- **Expected:** `AuthUiState.Error(isCredentialError = false)`. Copy is "Something went wrong on our end. Please try again in a moment." for 5xx, or "Couldn't reach the server. Check your connection and try again." for a network exception; other non-401 status codes show "Couldn't sign you in (error <code>)." Rendered as a general `BodyText` banner below the fields, not attached to a field.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/AuthViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/AuthScreen.kt
- **Platforms:** shared

### AUTH-05 — Re-entering AuthScreen resets stale state
- **Steps:** Trigger a login error (AUTH-03/AUTH-04), then leave and return to AuthScreen (e.g. back-navigate from password reset).
- **Expected:** `LaunchedEffect(Unit) { viewModel.resetState() }` clears the prior error/loading state back to `Idle` on every fresh composition of AuthScreen — no stale error banner or spinner reappears.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/AuthScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/AuthViewModel.kt
- **Platforms:** shared

### AUTH-06 — Forgot password navigates to reset with email prefilled
- **Steps:** On AuthScreen, type an email into the Email field, then tap "Forgot your password?" without submitting login.
- **Expected:** Navigates to `PasswordResetRequestScreen` with the trimmed email from the Email field pre-populated as `passwordResetInitialEmail`, passed into `PasswordResetRequestViewModel(initialEmail=...)`.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/AuthScreen.kt, sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/PasswordResetRequestViewModel.kt
- **Platforms:** shared

### AUTH-07 — Password reset request: Send button disabled for invalid/blank email
- **Steps:** On PasswordResetRequestScreen, leave the email field blank, or type an obviously invalid address (no "@", no domain dot).
- **Expected:** `PasswordResetRequestViewModel.canSubmit` is false (`isValidEmail` regex fails), so the "Send reset email" `PrimaryCta` renders disabled and tapping it is a no-op.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/PasswordResetRequestViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/PasswordResetRequestScreen.kt
- **Platforms:** shared

### AUTH-08 — Password reset request: submit shows loading pill then confirmation
- **Steps:** Enter a valid-looking email, tap "Send reset email".
- **Expected:** State moves Idle → Submitting (Moss pill + Lime spinner replaces the CTA) → on success, `Sent` state renders a confirmation block: "If an account exists with this email, we'll send password reset instructions to it." plus a "Back to sign in" button. Note the copy is intentionally non-committal (does not confirm whether the account exists) and fires regardless of the actual account state.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/PasswordResetRequestScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/PasswordResetRequestViewModel.kt
- **Platforms:** shared

### AUTH-09 — Password reset request: network failure shows retry-able error
- **Steps:** Submit a valid email while the request throws (network unreachable / server error).
- **Expected:** `PasswordResetSubmit.Failed`; a Danger-colored line "Couldn't reach the server. Check your connection and try again." appears above the CTA, which reverts to its enabled "Send reset email" state so the member can retry. Editing the email field while in the Failed state clears it back to Idle.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/PasswordResetRequestViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/PasswordResetRequestScreen.kt
- **Platforms:** shared

### AUTH-10 — Password reset: back to sign in
- **Steps:** From either the editing form or the post-submit "Sent" confirmation on PasswordResetRequestScreen, tap "Back to sign in".
- **Expected:** Returns to AuthScreen (`onBackToLogin` callback); no reset state persists into the fresh AuthScreen (AuthScreen resets its own state per AUTH-05).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/PasswordResetRequestScreen.kt, sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt
- **Platforms:** shared

### AUTH-11 — Manual sign out clears session
- **Steps:** From the Profile tab, scroll to the account section and tap "Sign out".
- **Expected:** `ArcanaApiClient.logout()` fires: `telemetry.logoutManual()` + `telemetry.reset()`, `tokenStorage.clear()`, the Ktor bearer-token cache is cleared, and `isAuthenticated` flips false — the app falls back to AuthScreen. This is distinguished from a forced logout by telemetry (`logoutManual` vs `forcedLogout`).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt
- **Platforms:** shared

### AUTH-12 — Forced logout ONLY when the server rejects the refresh token
- **Steps:** Three parts, and the differences are the whole point. Start the dev server with a short access-token lifetime so a token actually expires while you watch (`ACCESS_TOKEN_LIFETIME_SECONDS=30 python manage.py runserver`; the real 5 minutes is why this entry was BLOCKED), log in, and wait it out. (a) Arm a **401** on `/api/v1/auth/token/refresh/` (driver-playbook.md "Fault injection"), then trigger any authenticated request. (b) Arm a **503** on the same path instead, and trigger an authenticated request. (c) Make the stored refresh token unreadable, with the server healthy: clear the Keychain / EncryptedSharedPreferences externally while a session is active, then trigger an authenticated request. **Use the full `/refresh/` path**: the fault matcher is a prefix match, so arming `/api/v1/auth/token/` would break login too.
- **Expected:** (a) signs the member out; (b) and (c) must NOT. (a) `forceLogout("refresh_error")` records `SecureStorageDiagnostics.lastFailureFor(REFRESH_TOKEN_KEY)`, fires `telemetry.forcedLogout(cause, osStatus, storageOp, storageKey)`, reports a `ForcedLogoutSignal` nonfatal to Sentry BEFORE `telemetry.reset()` (so the member is still attached), clears tokens, and flips `isAuthenticated` false. (b) hits `RefreshOutcome.TRANSIENT` and keeps the session, reporting `auth_refresh_failed{outcome:"transient_status"}`. (c) does NOT sign the member out either: a null storage read is unproven, exactly like a timeout or a 5xx, so the session is kept and the next request retries. It reports `auth_refresh_failed{outcome:"no_stored_refresh"}` carrying `storage_os_status`/`storage_op`. **A genuinely unrecoverable token therefore leaves an authed shell whose requests keep failing** rather than an unexplained logout; the member's exit is the Profile tab's own Sign out (AUTH-11). That trade is deliberate (Cole, 2026-08-16): only an affirmative rejection may end a session.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt (`refreshTokens`, `forceLogout`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt (`RefreshFailureOutcome`), sharedLogic/src/iosMain/kotlin/org/arcana/mobile/auth/SecureStorage.ios.kt (`save` writes in place, so a rejected write cannot destroy the stored token)
- **Platforms:** shared

### AUTH-13 — Hidden Developer Settings gesture (10 taps on wordmark)
- **Steps:** On AuthScreen, tap the wordmark logo 10 times in a row.
- **Expected:** On the 10th tap, `DeveloperSettingsScreen` replaces AuthScreen (`showDeveloperSettings = true`, `devTapCount` resets to 0). The gesture has no visible affordance (ripple/indication is null) and is undiscoverable without knowing to tap it. Fewer than 10 taps has no visible effect.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/AuthScreen.kt
- **Platforms:** shared

### AUTH-14 — Re-entering Password Reset gives a fresh form, not the previous "Sent"
- **Steps:** From AuthScreen tap "Forgot your password?", submit a valid email and reach the "Sent" confirmation. Tap "Back to sign in", then tap "Forgot your password?" again in the SAME app session (no kill/relaunch). Repeat once more after a FAILED submit.
- **Expected:** The screen is back to its Editing state each time: the email field and "SEND RESET EMAIL" CTA are shown, not the "Sent" confirmation, so a member who never received the first email can request another. The field is repopulated with whatever email AuthScreen carried in (blank if none), not whatever was typed before leaving. A previous failure is cleared the same way. The ViewModel is keyed on that prefilled email and outlives the screen, so this depends on `resetState()` firing on entry — the same reset-on-entry AuthScreen itself does (AUTH-05).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/PasswordResetRequestScreen.kt (`LaunchedEffect(Unit) { viewModel.resetState() }`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/PasswordResetRequestViewModel.kt (`resetState`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`showPasswordReset`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt
- **Platforms:** shared

### AUTH-15 — Soft keyboard never traps the sign-in CTA
- **Steps:** On a short viewport (Android: `adb shell wm size 1080x1920`; iOS: an SE-class simulator), open AuthScreen, tap the password field and type, so the keyboard is up. Without dismissing the keyboard, scroll the form.
- **Expected:** Focusing a field auto-scrolls it clear of the keyboard. If SIGN IN starts behind the keyboard, the form scrolls and both SIGN IN and "Forgot your password?" can be brought fully above it without dismissing the keyboard. The form is a `verticalScroll` column with `imePadding()` inside a `BoxWithConstraints` whose pre-IME `maxHeight` is the inner column's `heightIn(min=)`, so the scrollable range always equals the IME height and the bottom 24dp spacer guarantees the CTA clears it. On a full-size device (Pixel 9 Pro, 1280x2856) the CTA is already clear with no scroll needed.
- **Note for drivers:** neither `uiautomator dump` nor `android layout` reports the keyboard, so both list the CTA at coordinates the IME may be covering. A blind tap there hits the keyboard and looks like a dead button. See the Android traps in `docs/regression/driver-playbook.md`.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/AuthScreen.kt (`BoxWithConstraints`, `viewportMinHeight`, `verticalScroll`, `imePadding`), sharedUI/src/androidMain/kotlin/org/arcana/mobile/MainActivity.kt (`enableEdgeToEdge`)
- **Platforms:** shared

### AUTH-16 — App-review accounts sign in against staging; everyone else prod
- **Steps:** **Read the hazard below before driving this — it is the one entry in the inventory that can leave the device pointed at production.** (1) Record the current Developer Settings override and confirm it is the run's local URL. (2) Sign in as `apple-reviewer@test.com` (or `google-reviewer@test.com`; passwords in seed_staging REVIEWER_ACCOUNTS) and confirm the base URL flipped to staging — the login itself will fail against a local run, which is fine and does not affect the assertion. (3) Sign out. (4) **Immediately re-set the Developer Settings override to the run's local URL and prove it took** (a real request landing in the run's own server log — not just the "CURRENTLY IN USE" label). (5) Only then sign in as the device's normal member and confirm it does NOT retarget. Step 5 is the actual property under test; proving only "reviewer → staging" has not tested "ONLY reviewer → staging".
- **Hazard — driving this DESTROYS the Developer Settings override:** `applyFor()` writes the staging URL through `BaseUrlProvider.set()`, the same persisted key the override uses, so the local URL is overwritten. On sign-out `clear()` calls `BaseUrlProvider.reset()`, which DELETES that key and falls back to `defaultUrl` — `https://api.arcana.fit`, production. This is correct app behavior (a real member has no override, so resetting to prod is right) and must not be "fixed" in the app; it is the suite's job to restore the override at step 4. Skipping step 4 silently points the device at production for every later entry. _Added after run 2026-08-27, where all three devices recorded this BLOCKED rather than risk exactly that._
- **Expected:** The reviewer sign-in silently retargets the base URL to `https://api.staging.arcana.fit` before the login request (schedule shows only the two sandbox studios; bookings land in staging). The redirect survives process death mid-session (persisted marker). Signing out, OR a later sign-in attempt with any non-reviewer email, resets the base URL to the default — a normal member can never be left pointed at staging. A Developer Settings override set without the marker is never touched. Email match is exact (case-insensitive, trimmed).
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/ReviewerRedirect.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/AuthViewModel.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/di/AppModule.kt (session teardown hook)
- **Platforms:** shared

## SIGNUP

### SIGNUP-01 — Cold deep link routes straight to the onboarding survey
- **Steps:** Cold-launch the app via a welcome link (`https://arcana.fit/welcome?token=XXX` or the dev `arcana://welcome?token=XXX` scheme) while signed out.
- **Expected:** `DeepLinkHandler.extractWelcomeToken` parses the token; `AppSessionController.onDeepLinkToken` seeds `welcomeToken` synchronously (same-frame, not via a LaunchedEffect, to avoid a spurious Auth screen flash/telemetry). Since the token has never been marked done (`isSurveyDone` false), `SignupSurveyScreen` renders first — not AuthScreen and not the claim screen.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/navigation/DeepLinkHandler.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/session/AppSessionController.kt, sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt
- **Platforms:** shared

### SIGNUP-02 — Warm deep link (app already open) also routes to survey/claim
- **Steps:** With the app already open and signed out (on AuthScreen or mid password-reset), receive the welcome link again (tap the email link a second time, or re-deliver the same URL to the platform bridge).
- **Expected:** `AppSessionController.onDeepLinkToken` updates `welcomeToken` from the newly-delivered token; the composition re-renders into SignupSurveyScreen (if `isSurveyDone(token)` is false) or straight to SignupCompletionScreen (if the survey was already completed/skipped for that exact token) — same routing logic as cold start, driven by the same StateFlow.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/session/AppSessionController.kt, sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt
- **Platforms:** shared

### SIGNUP-03 — Survey: required questions block Continue
- **Steps:** On SignupSurveyScreen, leave one or more required questions (Q1-Q11) unanswered and observe the "Continue" CTA.
- **Expected:** `missingRequired(answers)` is non-empty so `SignupSurveyViewModel.canSubmit` is false and the "Continue" `PrimaryCta` renders disabled; the "X of 14 answered" progress stamp above it reflects the count via `answeredCount`. **Q8 `membershipTypes` ("How do you pay for classes right now?") was added 2026-08-25** as a required multi-select, taking the survey from 13 questions to 14 and the required count from 11 to 12. Its "Other" is a plain option with no specify field, matching `modalities`/`neighborhoods` — only `howHeard` reveals a specify field (SIGNUP-05).
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyQuestions.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyScreen.kt
- **Platforms:** shared

### SIGNUP-04 — Survey: Q13/Q14 (open-floor) are optional and never block Continue
- **Steps:** Answer every required question (Q1-Q12) but leave "Anything else you want us to know?" (Q13) and "Did someone refer you to Arcana?" (Q14) blank.
- **Expected:** Continue becomes enabled — `missingRequired` only iterates `required = true` questions, and both Q13/Q14 have `required = false`. Their Overline label shows "· optional" next to the question number.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyQuestions.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyScreen.kt
- **Platforms:** shared

### SIGNUP-05 — Survey: "How did you hear about Arcana?" Other reveals required specify field
- **Steps:** On Q12 ("How did you hear about Arcana?"), select "Other". Leave the newly-revealed "Please specify" text field blank and check Continue's enabled state; then fill it in.
- **Expected:** Selecting "Other" (the question's `otherOption`) reveals an `ArcanaTextField` bound to `howHeard__other`. While that text is blank, `missingRequired` still lists `howHeard` (Continue stays disabled) even though a single option is selected; once text is entered, the question clears from `missingRequired` and Continue can enable (assuming all else answered).
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyQuestions.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyScreen.kt
- **Platforms:** shared

### SIGNUP-06 — Survey: multi-select questions toggle independently
- **Steps:** On a Multi-type question (e.g. Q1 "Which modalities do you train in regularly?"), tap several option chips, then tap one of the already-selected chips again.
- **Expected:** Each tap toggles that single option in/out of `answers.multis[id]` via `SignupSurveyViewModel.toggleMulti` — other selected options in the same question are unaffected. Selected chips render **Moss** filled with Stone text; unselected chips render a Mist-outlined box with Ink text.
_Corrected after run 2026-08-27: the Expected said Burnt Nectar. `SurveyOptionChip` fills the selected state with `Moss` (matching `BookingSheet.kt`'s `VisitChip`), and its own KDoc — which still says "Burnt Nectar when selected" — is the stale thing, not the code._
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyScreen.kt
- **Platforms:** shared

### SIGNUP-07 — Survey: submit failure keeps answers and offers retry, then "Continue anyway"
- **Steps:** Answer all required questions, tap Continue while the submit endpoint fails (network error or non-410 server error). Observe the screen, then tap Continue again to fail a second time.
- **Expected:** On first failure: `SubmitErrorBanner` shows the mapped message (network/server/generic), all typed answers are preserved, and a "Continue anyway" `TextLink` appears below the CTA once `failedAttempts >= 1`. Tapping "Continue anyway" calls `continueAnyway()`, which fires `signupSurveySkipped("submit_failed")` telemetry and completes the survey (advances past it) without a successful submit — the survey must never block a paid member's signup.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyScreen.kt
- **Platforms:** shared

### SIGNUP-08 — Survey: expired/consumed token (410) advances silently to claim screen
- **Steps:** Submit the survey using a token that the server reports as expired or already consumed (410).
- **Expected:** `SignupSurveyResult.TokenExpiredOrConsumed` fires `telemetry.signupSurveyFailed("token_expired", 410)` and calls `complete()` immediately — no error banner is shown on the survey itself; the flow advances to SignupCompletionScreen, which is responsible for rendering the actual token-expired UX (SIGNUP-13).
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyViewModel.kt
- **Platforms:** shared

### SIGNUP-09 — Survey: completion persists per-token so re-tapping the link skips it
- **Steps:** Complete (or "Continue anyway" past) the survey for a given welcome token, then background/kill and relaunch the app via the same welcome link.
- **Expected:** `AppSessionController.markSurveyDone(token)` persisted `signup_survey_done:<token> = "1"` in SecureStorage. On the next launch with that same token, `isSurveyDone(token)` is true and the flow renders SignupCompletionScreen directly — the survey does not reappear for that link.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/session/AppSessionController.kt, sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt
- **Platforms:** shared

### SIGNUP-10 — Survey: "Already a member? Log in" abandons the token
- **Steps:** On SignupSurveyScreen, tap "Log in" in the footer link.
- **Expected:** `session.consumeWelcomeToken()` clears the pending token and `onNavigateToLogin`/`onWelcomeTokenConsumed` fires, returning the member to AuthScreen. The abandoned survey progress is not persisted (no `markSurveyDone` call on this path).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyScreen.kt, sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/session/AppSessionController.kt
- **Platforms:** shared

### SIGNUP-11 — Claim form: birthday auto-mask inserts slashes as digits are typed
- **Steps:** On SignupCompletionScreen (claim-your-name), tap into the Birthday field and type `04121995`.
- **Expected:** `DateMaskVisualTransformation` renders the display as `04/12/1995` while the underlying stored value stays the raw digit string (`updateBirthday` strips non-digits and caps at 8 chars). Typing beyond 8 digits is ignored (no more characters accepted).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionViewModel.kt
- **Platforms:** shared

### SIGNUP-12 — Claim form: birthday validates real date + minimum age inline
- **Steps:** Type a birthday with an impossible date (e.g. `02301995` for Feb 30) or an underage date (a date less than 18 years before today), completing all 8 digits.
- **Expected:** Once 8 digits are entered, `birthdayErrorFor` computes: an impossible calendar date shows "Enter a valid date as MM/DD/YYYY.", an under-18 date shows "You must be 18 or older to use Arcana." Both render as the Birthday field's inline error. While fewer than 8 digits are typed, no error shows (no mid-type nagging). Continue ("Create account") stays disabled until a valid 18+ date is present alongside all other required fields.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionScreen.kt
- **Platforms:** shared

### SIGNUP-13 — Claim form: gender dropdown offers Male/Female/Other
- **Steps:** On SignupCompletionScreen, tap the Gender field.
- **Expected:** `ArcanaDropdownField` opens with exactly three options — "Male", "Female", "Other" (server codes `male`/`female`/`other`). Selecting one sets `editing.gender` and closes the dropdown. Gender is required: `isValidEditing` rejects submission while `gender.isBlank()`.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionViewModel.kt
- **Platforms:** shared

### SIGNUP-14 — Claim form: address fields required except Apt/unit
- **Steps:** Fill every claim-form field except leave "Apt / unit (optional)" blank, then attempt Create account; separately, leave Street address, City, State, or ZIP blank and check the CTA.
- **Expected:** Apt/unit blank never blocks submission (not checked by `isValidEditing`). Blank Street address, City, State, or Postal code each independently keep `canSubmit` false — validation is lenient on shape (no regex/format check on state/ZIP) but strict on non-blank presence for those four.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionScreen.kt
- **Platforms:** shared

### SIGNUP-15 — Claim form: password rules (min length, confirm match)
- **Steps:** Type a password under 8 characters, or type a password of 8+ characters where "Confirm password" doesn't match it, and check the Create account CTA.
- **Expected:** `isValidEditing` requires `password.length >= MIN_PASSWORD_LENGTH (8)` and `password == confirmPassword`; either violation keeps Create account disabled. A server-side password rejection (e.g. `password_invalid`) after submit surfaces as an inline `passwordError` on the Password field via `parseServerErrors`.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionScreen.kt
- **Platforms:** shared

### SIGNUP-16 — Claim form: first/last name and phone required
- **Steps:** Leave First name, Last name blank, or type a phone number with fewer than 10 digits, and check the CTA.
- **Expected:** `isValidEditing` rejects blank first/last name and phone numbers under `MIN_PHONE_DIGITS` (10) once non-digit characters are stripped for the count; phone input itself is capped at `PHONE_MAX_LENGTH` (20 chars) as typed so an over-long number can never reach the server.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionViewModel.kt
- **Platforms:** shared

### SIGNUP-17 — Claim form: locked email row shown when carried from checkout
- **Steps:** Reach SignupCompletionScreen with a non-null `lockedEmail` (email already confirmed via web checkout).
- **Expected:** A non-editable "Email" row renders above First name: a Moss circle check chip, the email value, and a faint "From checkout" stamp over a 1px Mist hairline. When `lockedEmail` is null this row is omitted entirely.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionScreen.kt
- **Platforms:** shared

### SIGNUP-18 — Claim form: successful submit shows a brand loader, no explicit navigation
- **Steps:** Fill the claim form validly and submit while the server accepts it.
- **Expected:** `SignupCompletionState.Success` renders `SuccessLoader` (centered Moss circle + Lime spinner on Stone) — a deliberate brief loading frame with no navigation call from this screen; the app-wide `isAuthenticated` flip (from `completeSignup()`) is what actually swaps the whole flow into the authenticated shell.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionViewModel.kt
- **Platforms:** shared

### SIGNUP-19 — Claim form: already-consumed/expired token routes to "Log in instead"
- **Steps:** Submit the claim form using `accounts.<device>.claim_used.welcome_token`, which the seed issues and then consumes, so the server answers 410 `token_invalid_or_expired` (`CompleteSignupResult.TokenExpiredOrConsumed`).
- **Expected:** `SignupCompletionState.Error(SignupErrorKind.TokenExpired)` renders a terminal ErrorState: "Already signed up" / "Log in instead." with body "Looks like this link's already been used." and a "Log in" `PrimaryCta`. The editing form is fully replaced — there is no way back to re-edit under this token.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionScreen.kt
- **Platforms:** shared

### SIGNUP-20 — Claim form: account-already-exists (409) also routes to "Log in instead"
- **Steps:** Submit the claim form using `accounts.<device>.claim_conflict.welcome_token`. Its token is valid but a User already exists on that email, and `complete_signup` checks the collision before the token, so the server answers 409 `account_exists`.
- **Expected:** `SignupCompletionState.Error(SignupErrorKind.AlreadyHasAccount)` renders the same terminal ErrorState layout but with body "You already have an account with this email." — distinct copy from the expired-token case, same "Log in" CTA routing to AuthScreen.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionScreen.kt
- **Platforms:** shared

### SIGNUP-21 — Claim form: network/server failure keeps form editable with a banner
- **Steps:** Submit a valid claim form while the network is unreachable, or the server 5xxs with no field-specific error.
- **Expected:** Stays on `Editing` state (not a terminal Error) — `formError` is set to "Couldn't reach the server..." (network) or "Something went wrong on our end..." (5xx with no field error), rendered as a Danger banner above the fields. All typed values are preserved so the member can retry without re-entering the form.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionScreen.kt
- **Platforms:** shared

### SIGNUP-22 — Claim form: Tab key advances exactly one field (keyboard/hardware input)
- **Steps:** Using a hardware keyboard (or simulator keyboard shortcuts) on the claim form, press Tab, then Shift+Tab.
- **Expected:** Focus moves exactly one field forward on Tab / one field backward on Shift+Tab. The screen's `onPreviewKeyEvent` consumes the Tab keydown itself (returns true) specifically to prevent double-advancing on iOS, where the platform would otherwise also traverse focus in addition to each field's own `onImeAction`-driven `moveFocus`.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionScreen.kt
- **Platforms:** iOS-only

### SIGNUP-23 — Survey/claim screens fire dedicated $screen telemetry
- **Steps:** Land on SignupSurveyScreen, then advance to SignupCompletionScreen; watch Debug-build telemetry console echo (or PostHog Activity).
- **Expected:** `Telemetry.Screens.SIGNUP_SURVEY` fires once on entering the survey; `Telemetry.Screens.SIGNUP` fires once on entering the claim screen — each via a `LaunchedEffect(Unit)` scoped to that branch of AuthFlowRoot/App.kt, so re-composition without a real navigation does not re-fire them.
- **Source:** sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt
- **Platforms:** shared

### SIGNUP-24 — Survey copy is never truncated, and carries no AI-tell dashes
- **Steps:** Open the onboarding survey (SIGNUP-01) on the NARROWEST device available (an iPhone at 402pt reproduces; the Pixel emulator is wide enough to mask it) and read every question label, hint, option chip and section heading top to bottom, including Q7's and Q12's long hints.
- **Expected:** No string is cut off and none ends in an ellipsis: labels, hints, options and stamps all wrap onto as many lines as they need. Watch Q12's hint in particular ("Open floor. What would make Arcana actually worth it to you?"), which wraps to two lines and previously ellipsised at "worth it to …". Separately, no member-facing string contains an em dash or a prose en dash; the only en dashes are numeric ranges ("1–2", "$200–$350", "Morning (8–11am)"), where they are correct typography. `SignupSurveyQuestionsTest` guards the dash rule; truncation is visual and needs a narrow device.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyScreen.kt (`SurveyQuestionBlock`, `SurveyOptionChip`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyQuestions.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/Text.kt (`Caption`/`Overline` single-line defaults)
- **Platforms:** shared

## HOME

### HOME-01 — Cold load shows shimmer, not blank screen
- **Steps:** Navigate to the Home tab immediately after login/app start, before the `/memberships/me` + `/bookings/me/` calls resolve.
- **Expected:** `HomeUiState.Loading` renders: static "Good {greeting}," headline with a shimmer box standing in for the name, a "Next up" section rule over a shimmer card, three shimmer rows under "Upcoming", and a shimmer manifesto card. No crash, no empty layout.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (lines 130-183), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/home/HomeViewModel.kt (`HomeUiState.Loading`)
- **Platforms:** shared

### HOME-02 — Greeting renders member's first name only
- **Steps:** Load Home as a member whose account `displayName` is a full name (e.g. "Cole Tomlinson").
- **Expected:** Headline reads "Good {morning/afternoon/evening}, Cole." — only the first token before the first space is shown, even though the stored display name has two words.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/home/FirstName.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (line 119, `firstName(s.displayName)`)
- **Platforms:** shared

### HOME-03 — Greeting display-name fallback (server-fed)
- **Steps:** Load Home as a member whose first/last name are blank server-side (blank them via `manage.py shell` on the regression member, then refresh Home).
- **Expected:** The greeting renders whatever `display_name` the server sends. The server never sends null: with blank names its serializer falls back to the member's full email address, so the greeting shows the raw email (e.g. "GOOD EVENING, REGRESSION-ANDROID-MEMBER@EXAMPLE.COM."). The client-side `email.substringBefore("@")` fallback in `HomeViewModel` is dead code on this path — it only fires if the server ever sent null. (Corrected 2026-08-11 after the first live run: the original entry assumed a reachable null-displayName precondition; the raw-email greeting is current intended behavior, with a UX follow-up flagged for a friendlier server-side fallback.)
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/home/HomeViewModel.kt (line 64)
- **Platforms:** shared

### HOME-04 — Greeting salutation matches local time-of-day
- **Steps:** Load Home at a device-local hour before 5am, between 5am-12pm, 12pm-5pm, and after 5pm (or mock the clock across those buckets).
- **Expected:** Headline second line reads "evening," before 5am, "morning," from 5am up to noon, "afternoon," from noon up to 5pm, and "evening," from 5pm onward.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (`timeOfDay`, lines 348-354, and its use at lines 98/383/392/396)
- **Platforms:** shared

### HOME-05 — Date overline shows today's date
- **Steps:** Load Home on any date.
- **Expected:** A small overline above the greeting reads "{3-letter weekday} · {3-letter month} {day}" (e.g. "TUE · AUG 11") computed from the device's current date/timezone.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (lines 94-97, `TopBar`/`HeroHeader` usage)
- **Platforms:** shared

### HOME-06 — Error state shows a full-screen error instead of crashing or blanking
- **Steps:** Force `/memberships/me` to fail (e.g. server unreachable, non-2xx) with no prior successful load cached in this session.
- **Expected:** **Behavior changed.** `HomeUiState.Error(type: ErrorType)` is set; the shared `FullScreenError` replaces the whole tab content (not the static greeting chrome plus a caption) — see ERR-05 for the full CONNECTION/SERVER copy and the working "TRY AGAIN" retry. No crash, no infinite shimmer.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/home/HomeViewModel.kt (`fetch`, `retry`, `retrying`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt (`FullScreenError`)
- **Retry feedback (2026-08-18):** the retry control shows the dot-matrix loader in place of its label while the re-fetch is in flight, and the error stays on screen throughout. A retry must NOT drop to the loading state: doing so flashed the skeleton and snapped back to the same error. Repeat taps while one retry is in flight are ignored rather than queueing another fetch.
- **Platforms:** shared

### HOME-07 — Next Up hero card renders the soonest upcoming booking
- **Steps:** Load Home as a member with at least one upcoming booking.
- **Expected:** A "Next · {relative time}" section rule appears (e.g. "Next · in 18min", "Next · in 3h", or "Next · Mon 6:00am" depending on how far out the class is) followed by a dark Moss card showing the studio (and spot label if assigned), a booking-status pill, the studio-local start time with am/pm (never the device timezone), the class name, and a studio/location/duration meta line.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (`NextUpCard`, lines 198-221 and 410-528; `relativeTime`, lines 313-337)
- **Platforms:** shared

### HOME-08 — Next Up card shows member-facing booking info when present
- **Steps:** Load Home where the soonest booking carries a member-facing note (e.g. a door code) via `bookingInfoOrNull`.
- **Expected:** The Next Up card shows an extra "Booking info" overline plus the note text (max 2 lines, ellipsized); when no note exists on the booking, that block is omitted entirely.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (lines 514-525), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingNotes.kt (`bookingInfoOrNull`)
- **Platforms:** shared

### HOME-09 — No-upcoming-classes empty state on the Next Up section
- **Steps:** Load Home as a member with zero upcoming bookings.
- **Expected:** The "Next up" section rule still renders, but in place of the hero card an Ash caption reads exactly "No upcoming classes." instead of a broken/empty card. (Further down, the separate upcoming-preview block adds "Nothing booked yet." when there is no hero and no rest — a different caption, not this one.)
_Corrected after run 2026-08-27: the Expected carried a trailing "— browse the schedule." clause the code has never rendered; `HomeScreen.kt`'s hero-else branch emits the bare "No upcoming classes." only._
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (`HomeUiState.Success`, the `hero == null` branch)
- **Platforms:** shared

### HOME-10 — Nothing-booked-yet empty state below the hero
- **Steps:** Load Home as a member with zero total upcoming bookings (hero also null).
- **Expected:** Below the Next Up empty state, a second caption reads "Nothing booked yet." in place of the upcoming rows list (no "See all" confusion, no empty list flash).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (lines 246-254)
- **Platforms:** shared

### HOME-11 — Upcoming preview list shows up to 4 further bookings, grouped by day
- **Steps:** Load Home as a member with 6+ upcoming bookings spanning multiple days. **`seed_regression` provides one**, and a shift can add roughly one more by booking in-app, so the honest ceiling today is the day-header divider between two bookings on different days; the 4-row cap and the hairline-suppression rules are unreachable until the seed grows. Say which of the three you actually saw.
- **Expected:** Up to `UPCOMING_PREVIEW_COUNT` = 4 bookings (after the hero) render as rows under "Upcoming"; each new day introduces a day-header divider ("{Weekday} · {Month} {day}") with a hairline, consecutive same-day rows share a bottom hairline, and the day's last row (or the list's final row) drops its own hairline so dividers don't double up.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (`UPCOMING_PREVIEW_COUNT` line 75, itemsIndexed block lines 256-273, `UpcomingRow` lines 530-643)
- **Platforms:** shared

### HOME-12 — Upcoming row shows time, duration, status pill, studio/location/spot
- **Steps:** Load Home as `accounts.<device>.member` from the manifest — its seeded confirmed reservation is the upcoming booking this entry reads (it carries a location; the regression templates are `spot_selection_mode='none'`, so the spot label is absent unless you first book a spot studio, which no seeded fixture is).
- **Expected:** Each row shows a fitted booking-status pill above the studio-local start time (never the device timezone), the class duration below it, and on the right the studio name, a dot-separated location (ellipsized if long) and spot label, plus the class name.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (lines 584-635)
- **Platforms:** shared

### HOME-13 — "See all" link opens the full bookings list
- **Steps:** From Home, tap the "See all" text link below the upcoming rows.
- **Expected:** `onSeeAllBookings` fires, navigating to the My Bookings screen (regardless of whether there are 0, few, or many upcoming bookings — the link always renders in the Success state).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (lines 276-285)
- **Platforms:** shared

### HOME-14 — Tapping the Next Up card or an upcoming row opens that class's detail
- **Steps:** From Home, tap the Next Up hero card; separately, tap any row in the Upcoming list.
- **Expected:** `onOpenClass(session.id)` fires with that specific booking's session id, navigating into Class Detail for that class.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (lines 216-220, 264-271)
- **Platforms:** shared

### HOME-15 — Manifesto card shows remaining credits and streak
- **Steps:** Load Home as a member with an active current-period wallet and a nonzero week streak.
- **Expected:** The dark card at the bottom reads "{N} classes remaining." plus a line "{N}-week streak. Keep it going." beneath it. **The second line is the whole entry** — the credits line alone is HOME-16's zero-streak branch, and no seeded account has a nonzero streak (it is server-computed from real attendance), so this entry stays BLOCKED on that fixture gap until one exists. Do not PASS it on the credits line.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (`ManifestoCard`, lines 645-689)
- **Platforms:** shared
_Annotated after run 2026-08-27: one lane recorded PASS quoting only "7 CLASSES REMAINING.", which is HOME-16. The Expected now says so out loud._

### HOME-16 — Manifesto card shows "Build your streak." when streak is zero
- **Steps:** Load Home as a member with an active wallet but `weekStreak == 0`.
- **Expected:** The manifesto card's sub-line reads "Build your streak." instead of a "0-week streak" phrasing.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (line 680)
- **Platforms:** shared

### HOME-17 — Manifesto card shows "No active membership." empty state
- **Steps:** Load Home as a member with no current-period wallet (`creditsRemaining == null`, e.g. lapsed or between cohorts).
- **Expected:** The manifesto card shows only "No active membership." — the streak sub-line and any "Next:" chip are suppressed entirely, not shown as zero/blank values.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (lines 663-668)
- **Platforms:** shared

### HOME-18 — Manifesto card shows "Next: {month} · {N} credits" when a next-period wallet exists
- **Steps:** Load Home as a member who has purchased next month's credits while still inside the current month.
- **Expected:** Below "{N} classes remaining." an extra line reads "Next: {upcomingMonth} · {upcomingCredits} credits"; for members without a next-period wallet this line is absent.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (lines 672-679), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/home/HomeViewModel.kt (lines 66-67, `upcomingMonth`/`upcomingCredits`)
- **Platforms:** shared

### HOME-19 — Pull-to-refresh re-fetches without flashing the shimmer
- **Steps:** On Home in the Success state, pull down from the top of the list to trigger the refresh gesture; release.
- **Expected:** A refresh spinner shows via `PullToRefreshBox` while `isRefreshing` is true; the currently-displayed content (greeting, cards, rows) stays visible throughout — no shimmer flash — and updates in place once the re-fetch completes.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (lines 100-104), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/home/HomeViewModel.kt (`refresh()`, lines 48-57)
- **Platforms:** shared

### HOME-20 — A failed pull-to-refresh keeps existing content instead of showing an error
- **Steps:** On Home in the Success state with content already loaded, pull to refresh while the server is unreachable or returns an error.
- **Expected:** The refresh spinner stops; the screen keeps showing the previously-loaded greeting/cards/rows unchanged (no error caption, no state wipe) because `fetch()` only writes `HomeUiState.Error` when the current state is not already `Success`.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/home/HomeViewModel.kt (lines 71-77)
- **Platforms:** shared

## SCHED

### SCHED-01 — Cold-start schedule load (favorites-default scope)
- **Steps:** Sign in and land on the Book tab (the middle tab, labelled BOOK) for the first time in the session.
- **Expected:** `ScheduleViewModel.init` fetches favorites first; if the member has any, scope defaults to `ScopeMode.Favorites`, otherwise `AllStudios`. In parallel it fetches the overview (day chips, studio catalog, categories) and page 1 of today's sessions. A centered `DotMatrixLoader` shows under the "Month." header while `ScheduleUiState.Loading`; on success the day rail, filter bar, and today's class list render.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt
- **Platforms:** shared

### SCHED-02 — Schedule load failure → full-screen error with retry; retry also restores Favorites scope
- **Steps:** As a member with saved favorites (so Schedule cold-starts in Favorites scope), force BOTH the favorites fetch and the initial overview/page-1 fetch to fail together (e.g. server unreachable) on cold start, then tap "TRY AGAIN" once the server is reachable again.
- **Expected:** Cold-start failure renders the shared `FullScreenError` (CONNECTION "CAN'T REACH ARCANA." / SERVER "SOMETHING'S OFF ON OUR END.", full copy/UI contract at ERR-01). Tapping "TRY AGAIN" calls `viewModel.reload()`, which refetches **without** resetting to `Loading` — the error stays on screen and the button shows the dot-matrix loader while the retry is in flight (a failed retry used to flash the month header and day rail before returning to the same error). **`reload()` now also re-derives Favorites scope when favorites are still unknown** (`favoritesRepository.favorites.value == null`, via the shared `applyFavoritesScope` helper): since the outage that failed the schedule fetch almost always failed the favorites fetch too, a naive retry would otherwise silently strand the member on `AllStudios` even though they have favorites. A member who deliberately switched to All Studios before the outage (favorites already known, non-null) is never overridden by this. The old local `ErrorBlock` composable is gone.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`applyRefetchFailure`, `reload`, `applyFavoritesScope`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt (`FullScreenError`)
- **Platforms:** shared

### SCHED-03 — Day picker: tap a day chip
- **Steps:** On Schedule, tap a day chip in the horizontal day rail (not the currently selected day).
- **Expected:** The selected date updates immediately (Display header month can change), a `scheduleDayChanged` telemetry event fires with direction forward/backward and day offset from today, and if that day's page 1 isn't cached under the current filter set, a scoped loader shows only in the list area (rail/chips stay interactive) while it fetches.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`selectDay`, `ensureSelectedDayLoaded`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`DayChip`, day-loading item)
- **Platforms:** shared

### SCHED-04 — Day picker: horizontal swipe over the class list
- **Steps:** On the class list body (not the day rail or filter chips), swipe left or right past the 56dp threshold.
- **Expected:** A left swipe (forward) advances to the next day; a right swipe (backward) goes to the previous day, matching `dayAfterSwipe`. A swipe below threshold or at the window edge (no day in that direction) does nothing. The list fades from 0.4 alpha to full over 200ms on any day change.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`daySwipe`, `SuccessContent`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleDisplayLogic.kt (`dayAfterSwipe`)
- **Platforms:** shared

### SCHED-05 — Infinite scroll pagination (load more)
- **Steps:** Scroll down through a day with more than one page of sessions until within 10 items of the bottom.
- **Expected:** `ScheduleViewModel.loadMore()` fires automatically, guarded so it only runs when page 1 is loaded, a `nextCursor` exists, and no page is already in flight. New sessions append (deduped by session id), a compact loader shows at the list footer while fetching, and once `nextCursor` is null the list shows the `EndOfDayMarker` ("That's everything for <Weekday>") instead.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`loadMore`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`LOAD_MORE_LOOKAHEAD` LaunchedEffect, `EndOfDayMarker`)
- **Platforms:** shared

### SCHED-06 — Empty state: no classes match filters for the day
- **Steps:** Select a day/filter combination that returns zero sessions (e.g. narrow to a studio with no classes that day).
- **Expected:** Once the day's page 1 has loaded (`dayLoaded == true` and no `dayError`), the list shows "No classes match your filters for this day." in place of any band headers or rows. Reach this ONLY via a genuinely empty result — do not use a forced server failure to test it: since `bodyOrThrow`, a 5xx on the day fetch now correctly reaches ERR-03's `InlineError` card instead. (Before `bodyOrThrow`, a 5xx with a JSON error body silently deserialized into an empty page and landed here instead of an error state — a server outage looked identical to a genuinely empty day.)
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`SuccessContent`, "empty" item), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt (`fetchSessionsPage`, `bodyOrThrow`)
- **Platforms:** shared

### SCHED-07 — Manual refresh: pull-to-refresh
- **Steps:** On the Book tab, pull down from the top of the list to trigger the platform pull-to-refresh gesture.
- **Expected:** `isRefreshing` drives the `PullToRefreshBox` spinner; `ScheduleViewModel.refresh()` re-fetches booked-session pills plus the overview + selected day's page 1 without flashing the full-screen loader, keeping current content visible. Other cached days are dropped and the fetch generation bumps so any stale in-flight page load is discarded.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`refresh`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`PullToRefreshBox`)
- **Platforms:** shared

### SCHED-08 — Resume refresh: booked pills refresh on tab return
- **Steps:** Book or cancel a class from Class Detail, then navigate back to the Book tab (or background/foreground the app while on Book).
- **Expected:** `LifecycleResumeEffect` calls `viewModel.refreshBookings()` on every resume, best-effort re-fetching `/bookings/me/` and republishing over the existing Success state so a just-booked/-cancelled status pill appears/clears without a manual pull-to-refresh; a failed fetch leaves the prior pill map untouched.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`refreshBookings`, `refreshBookedSessions`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`LifecycleResumeEffect`)
- **Platforms:** shared

### SCHED-09 — Curated category (modality) filter: apply and remove
- **Steps:** Open the "MODALITIES" filter pill, tap one or more curated categories in the flat list, tap DONE (or tap the ✕ on a resulting chip).
- **Expected:** Each toggle updates `selectedModalitySlugs` immediately and marks `refreshingFilters = true` (dims the list, shows a compact loader between chips and list); after a 250ms debounce the server-side refetch settles with sessions narrowed to the selected categories. Picks render as removable chips below the filter pills; the pill itself only appears when `availableModalities` is non-empty for the current window.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`toggleModality`, `removeModality`, `onFiltersChanged`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`ScheduleFilterSection`, modality panel + `FilterChip`)
- **Platforms:** shared

### SCHED-10 — Time-of-day filter: preset and custom range
- **Steps:** Open the "TIME" filter pill; tap a Morning/Afternoon/Evening preset, or drag the dual-handle range slider to a custom span then tap DONE.
- **Expected:** A preset applies immediately (`onApply`); a custom range only commits a `TimeFilter` if it's narrower than the full span (a full-span selection clears the filter instead). The active filter renders as a removable chip and narrows sessions server-side via `startTimeGte`/`startTimeLte` on the next debounced refetch.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`TimeFilterPanel`, `TimeRangeSlider`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`setTimeFilter`, `clearTimeFilter`)
- **Platforms:** shared

### SCHED-11 — Scope toggle: Favorites ⟷ All Studios
- **Steps:** With the member having at least one favorite, tap the "ALL STUDIOS" side of the scope toggle (or drag the thumb across), then tap "FAVORITES" to switch back.
- **Expected:** Exactly one side is active at a time; switching resets the studio/location subset (`ScheduleFilters()`) while preserving the Time + Modality overlays, and triggers a debounced refetch scoped to the member's expanded favorite locations (Favorites) or the manual studio/location selection (All Studios). When the member has no favorites, only an "ALL STUDIOS" bar renders (no toggle).
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`useMyFavorites`, `showAllStudios`, `effectiveLocationIds`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`ScopeToggle`)
- **Platforms:** shared

### SCHED-12 — Favorites nudge banner (no favorites yet)
- **Steps:** Log in as `accounts.<device>.member_no_favorites` (seeded with zero favorites) and land on Schedule.
- **Expected:** A dismissable Paper-card banner reads "Make it yours. Save your favorite Studios." with a "CHOOSE FAVORITES" link that calls `onManageFavorites`; the ✕ dismisses it for the session only (`nudgeDismissed`, resets on process restart). The banner never shows if the favorites fetch failed (`favoritesKnown == false`), to avoid nudging a member who may already have favorites. **Silent-success note:** this suppression depends on `bodyOrThrow` (see FAV-05) — before it, a 5xx with a JSON error body on the member's first-ever favorites fetch silently reported an empty `FavoritesDto` as success, making `favoritesKnown == true` with zero favorites, which would have WRONGLY shown this nudge to a member whose real favorites status the server never actually confirmed.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`SuccessContent`, "favorites-nudge" item), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`hasFavorites`, `favoritesKnown`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/favorites/FavoritesRepository.kt (`refresh`)
- **Platforms:** shared

### SCHED-13 — Studio/location accordion filter (All Studios subset)
- **Steps:** With scope on "ALL STUDIOS", tap the bar to expand the accordion; tap a studio row to select/deselect the whole studio, or expand a studio's chevron and toggle individual locations.
- **Expected:** Selecting a whole studio clears any of its individually-picked locations (redundant); selecting every location under a studio individually promotes the pick to a whole-studio selection. Tapping DONE collapses the panel. Every change narrows the schedule server-side via the debounced pipeline.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`toggleStudioWhole`, `toggleLocation`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (studio accordion panel)
- **Platforms:** shared

### SCHED-14 — CLASS FULL rendering on the schedule row
- **Steps:** Use the session id from the manifest's `classes.full` (Regression Test Studio, +2 days at 12:00 ET, seeded 20/20 booked). Select that day on the Schedule day rail and find its row in the list.
- **Expected:** The row's title dims to Ash, the studio color bar fades to 35% alpha, and the capacity overline reads "FULL" in Ash2 (from `computeCapacityTier`). The row remains tappable into Class Detail. **Changed 2026-08-25:** the trailing 36dp CTA well is gone. It previously showed a muted "+" here and an Ink arrow otherwise, but neither carried information the row did not already state, and the fixed 36dp well plus its 16dp gap were truncating the `BRAND · LOCATION` meta line. Fullness now reads only from the dimmed title, the faded color bar and the FULL overline.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`ClassRow`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleDisplayLogic.kt (`computeCapacityTier`, `CapacityTier.Full`)
- **Platforms:** shared

### SCHED-15 — Booking-window-gated ("NOT OPEN") rendering on the schedule row
- **Steps:** Use the session id from the manifest's `classes.window_gated` — seeded with `bookable_at` = now + 2 days, on Regression Test Studio at +6 days 07:00 ET. Select that day on the Schedule day rail and find its row. **Do not go looking for a real Mariana Tek class**: `bookable_at` is populated by several platforms, the fixture is a synthetic `platform='fake'` regression studio, and the seeded member opens Schedule scoped to Favorites (the two regression studios), so real Mariana Tek rows are filtered out of view anyway.
- **Expected:** `isNotOpenYet` takes precedence over Full — the row shows the "NOT OPEN" overline (Ash2) and suppresses the fill progress bar and scarce shading. The row stays viewable and tappable into Class Detail. **Changed 2026-08-25:** no trailing CTA well is rendered on any row, so the old arrow-vs-"+" distinction that separated this state from SCHED-14 no longer exists; the overline is now the only thing that distinguishes them.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`ClassRow`, `notOpen` derivation), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleDisplayLogic.kt (`computeCapacityTier`, `CapacityTier.NotOpen`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/data/ScheduleDto.kt (`isNotOpenYet`)
- **Platforms:** shared

### SCHED-16 — Already-booked status pill on schedule rows
- **Steps:** No booking needed to start — `accounts.<device>.member` from the manifest is seeded with one **confirmed** upcoming reservation (a session dedicated to this device, +3 days). Find it via My Bookings, note its day, and view that day on Schedule. Then also book a class from Class Detail and return to Schedule to see the `REQUESTED` variant appear.
- **Expected:** The row shows a `StatusPillFitted` (e.g. REQUESTED/CONFIRMED) above the time column, sourced from `bookedSessions[session.id]`. It clears automatically after a resume-triggered `refreshBookings()` if the booking is later cancelled.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`ClassRow` `bookedStatus`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`bookedSessions`, `refreshBookedSessions`)
- **Platforms:** shared

### SCHED-17 — Hidden-capacity studio rendering (no fill bar / no scarce shading)
- **Steps:** View `classes.hidden_capacity`, at the seeded Regression Hidden Capacity Studio (`publishes_capacity=false`), which is how a capacity-hidden Mindbody studio presents.
- **Expected:** No fill progress bar renders; the overline reads binary "AVAILABLE" or "FULL" only (never "FILLING UP"/"ALMOST FULL"), per `computeCapacityTier`'s `!publishesCapacity` branch.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`ClassRow`, `showsCapacityVisuals`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleDisplayLogic.kt (`computeCapacityTier`)
- **Platforms:** shared

### SCHED-18 — Day list groups sessions into Morning/Afternoon/Evening time bands
- **Steps:** View a day with sessions spanning morning, afternoon, and evening start times.
- **Expected:** Sessions bucket into `TimeBand.MORNING` (hour < 12), `AFTERNOON` (hour < 17), or `EVENING` (else); each non-empty band renders a `SectionRule(band.label)` header ("MORNING"/"AFTERNOON"/"EVENING"), with 24dp of extra spacing separating one band's header from the previous band's last row. A day with sessions in only one or two bands shows only those bands' headers — empty bands render nothing.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (lines 184-191, `TimeBand`/`timeBand()`; lines 518-531, band-header + 24dp spacer)
- **Platforms:** shared

### SCHED-19 — Blocked/hidden session never surfaces and is not drillable
- **Steps:** Read the `classes.blocked` session id from the manifest (seeded `availability='blocked'` on Regression Test Studio / Regression Flatiron, **+2 days at 13:00 ET** — the same day as the `classes.full` fixture, which sits at 12:00 ET on the same studio). Select that day on the Schedule day rail with the seeded member's default Favorites scope, let page 1 settle, then scroll the whole day to the `EndOfDayMarker` so pagination cannot be hiding it. Compare what rendered against the day's fixtures.
- **Expected:** The blocked session **never appears anywhere in the schedule list** — no row, no band entry, on any day, under any scope/filter combination — and there is therefore nothing to tap that reaches its Class Detail. The 12:00 `classes.full` row on the same day and studio **does** render (that is the control proving the day, studio and Favorites scope are all working, so an absent blocked row is the invariant holding rather than an empty day). Blocked visibility is enforced server-side at the single queryset chokepoint behind list **and** detail, so the id is unreachable by drill-down too: if the driver forces a detail fetch for it, `ClassDetailViewModel` gets a 404 (`ApiHttpError(404)`, which classifies `ErrorType.SERVER` — the server did answer) and renders the shared `FullScreenError` ("SOMETHING'S OFF ON OUR END.", see ERR-04) rather than a class — no `"server error"` string or status code appears in the copy. Recording FAIL here means a hidden class became bookable, which is the whole reason the invariant exists.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`fetchSessionsPage`/`loadMore` render exactly what the server returns; there is no client-side availability filter), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ScheduleApi.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`ClassRow` — only ever built from a returned session), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailViewModel.kt (`fetch` — the 404 path if the id is forced) (the exclusion itself is enforced server-side and has no mobile counterpart: `base_class_session_queryset` in arcana-server's `classes/views.py` excludes `availability='blocked'` from list AND detail)
- **Platforms:** shared

## CLASS

### CLASS-01 — Class detail loads from a schedule row tap
- **Steps:** Tap any class row on Schedule.
- **Expected:** Navigates to Class Detail; `ClassDetailViewModel.reload()` fetches `GET /api/v1/classes/<id>/` and fires the `classViewed` telemetry event once loaded (studio/location/modality/spots/full/load-ms). While loading, a centered `DotMatrixLoader` shows under the close button.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`LoadingBlock`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailViewModel.kt (`reload`, `fetch`)
- **Platforms:** shared

### CLASS-02 — Class detail load failure → full-screen error with retry
- **Steps:** Force the class-detail fetch to fail on first load.
- **Expected:** The shared `FullScreenError` fills the screen and is **vertically centred**, with the close button OVERLAID on top rather than stacked above it — stacking gave the error only the space below the bar, so it centred lower than the identical error on Home and Schedule. The X stays usable. "TRY AGAIN" calls `viewModel::retry` (NOT `reload`, which resets to `Loading` and is first-load only): the error stays on screen and the button shows the dot-matrix loader while the re-fetch is in flight, instead of flashing the shimmer and returning to the same error. Repeat taps mid-flight are ignored. `classViewFailed` telemetry fires with a reason from `Throwable.telemetryReasonFor()` — `server_<code>` when the throwable carried an HTTP status, else the same CONNECTION/SERVER split spelled `network`/`server`. A refresh (pull-to-refresh) failure instead keeps the prior content on screen rather than showing this error block.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailViewModel.kt (`fetch`, `retry`, `retrying`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`ErrorBlock`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt (`FullScreenError`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ErrorType.kt (`telemetryReasonFor`)
- **Platforms:** shared

### CLASS-03 — Class detail pull-to-refresh
- **Steps:** On Class Detail, pull down to refresh.
- **Expected:** `onRefresh` re-fetches the session (`isView = false`, no duplicate `classViewed` event) and also calls `bookingVm.load()` to re-resolve booking eligibility; capacity numbers update since the server refreshes upstream data if its cached row is >30s old.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`SuccessBlock`, `PullToRefreshBox`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailViewModel.kt (`refresh`)
- **Platforms:** shared

### CLASS-04 — Cancelled-by-studio class detail
- **Steps:** Open the detail of `classes.cancelled` (seeded with `status='cancelled_by_studio'`). **BLOCKED today for the same reason as CLASS-07, and it is a navigation gap rather than a fixture gap:** the row exists (verified 2026-08-28 — the seeded id returns `status: "cancelled_by_studio"` from the detail endpoint), but Schedule's list queryset filters `status='scheduled'` and the seeded member holds no booking on it, so nothing in the app routes there. Seeding a booking on that session (left cancelled) would give My Bookings a path.
- **Expected:** In place of the Availability block, a "Cancelled" section rule + "This class has been cancelled by the studio." (Warning color) renders; the sticky reserve CTA is hidden entirely.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`SuccessBlock` `isCancelled` branch)
- **Platforms:** shared

### CLASS-05 — Booking-window-gated ("NOT OPEN") class detail
- **Steps:** Drill into the `classes.window_gated` session from the manifest (same fixture SCHED-15 uses: `bookable_at` = now + 2 days, +6 days 07:00 ET) by tapping its schedule row; the seeded member holds no booking on it. **Not a Mariana Tek class** — the fixture is a synthetic `platform='fake'` regression studio, so navigate by the manifest id rather than hunting a real Mariana Tek row (which the Favorites-scoped Schedule hides anyway).
- **Expected:** The Availability block replaces the spot-count headline/pips with "NOT OPEN" + "Booking opens <Day>, <Mon> <D> · <time> ET" (always Eastern Time regardless of device zone). The sticky CTA shows "OPENS <DAY> <TIME> ET" and is disabled (not tappable) until the window opens.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`opensAtAvailabilityLine`, `opensAtCtaLabel`, `AvailabilityBlock`, `StickyReserveCta`)
- **Platforms:** shared

### CLASS-06 — CLASS FULL rendering in the availability block
- **Steps:** Drill into the `classes.full` session from the manifest (the same fixture SCHED-14 uses; its studio is seeded `publishes_capacity=True`) by tapping its schedule row.
- **Expected:** Headline reads "FULLY BOOKED"; the segmented pip strip shows all pips as taken (Mist@70%); the sticky CTA reads "CLASS FULL" in a Graphite pill with a clock icon instead of an arrow, and is disabled.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`AvailabilityBlock`, `CapacityPips`, `StickyReserveCta`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailLogic.kt (`computeDetailCapacity`)
- **Platforms:** shared

### CLASS-07 — Past class rendering ("CLASS ENDED")
- **Steps:** Open detail for a session whose `endAt` is in the past. **BLOCKED, and not a fixture gap:** Schedule never surfaces an already-past session and `DeepLinkHandler` handles only the welcome-token scheme, so a seeded past class would exist with no way to navigate to it. Reaching this needs a class deep link in the app.
- **Expected:** The Availability block is hidden entirely (isPast); the sticky CTA reads "CLASS ENDED" and is disabled/no-op regardless of any other state.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`SuccessBlock` `isPast`, `StickyReserveCta` label precedence)
- **Platforms:** shared

### CLASS-08 — Booking flow: open confirmation sheet and book with credits
- **Steps:** Open detail for a bookable class (has credits, spots available, window open, no existing booking) and tap the sticky "RESERVE THIS SPOT" CTA.
- **Expected:** `BookingSheet` opens as a `ModalBottomSheet` showing class name/studio, a credit-usage line ("This uses 1 of N credits"), and the late-cancel cutoff copy (bold-highlighted window from `bookingCancelCopy`). Tapping CONFIRM calls `createBooking`; on success the sheet closes, `bookingSucceeded` telemetry fires, and the CTA follows the status the server returned — "REQUESTED ✓" at a manual-fulfilment studio, "CONFIRMED ✓" at a direct-integration one (which confirms inside the create call).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/BookingSheet.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt (`openSheet`, `confirmBooking`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingEligibility.kt (`bookCtaState`)
- **Platforms:** shared

### CLASS-09 — Booking flow: spot selection required
- **Steps:** Open the booking sheet for a class whose `template.spotSelectionMode != "none"`, tap CONFIRM without picking a spot.
- **Expected:** CONFIRM stays disabled (`canConfirm` requires `_selectedSpot.value != null` when `requiresSpot`). Picking a spot from `SpotSelector` (or the expanded full-screen `SpotMapFullScreen` for grid studios with coordinates) enables CONFIRM; `spotSelected` telemetry fires on pick. The inline map draws on its own white plate (`Plate`), not on the sheet's Stone, so taken dots read as taken; its dots never touch each other.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/BookingSheet.kt (`requiresSpot` branch), sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/SpotSelector.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/SpotMap.kt (map-vs-chips chooser), sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/SpotPicker.kt (chip-row fallback when spots lack coordinates), sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/SpotMapFullScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt (`canConfirm`, `selectSpot`)
- **Platforms:** shared

### CLASS-10 — Booking flow: "have you been here before?" one-time prompt
- **Steps:** Open the booking sheet for a class where `session.shouldAskStudioVisit == true`.
- **Expected:** A YES/NO prompt renders ("Have you been to <Studio> before?"); CONFIRM stays disabled until answered (`canConfirm` requires `_visitedBefore.value != null` when the prompt is shown). `studioVisitPromptShown` fires when the sheet opens with the prompt, `studioVisitAnswered` fires on tap.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/BookingSheet.kt (`StudioVisitPrompt`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt (`setShouldAskStudioVisit`, `answerStudioVisit`, `canConfirm`)
- **Platforms:** shared

### CLASS-11 — Booking flow: spot-preference dropdown (non-spot classes)
- **Steps:** Open the booking sheet for `classes.spot_preference`, whose template carries `spot_preference_options` with `spot_selection_mode='none'`, so `requiresSpot` is false.
- **Expected:** An `ArcanaDropdownField` renders with the template's options; the picked value rides along as free text on the booking but never gates CONFIRM (always optional). Suppressed entirely when `requiresSpot == true` (real spot selection wins).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/BookingSheet.kt (spot-preference dropdown), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt (`spotPreferenceActive`, `setSpotPreferenceOptions`)
- **Platforms:** shared

### CLASS-12 — Booking failure renders inside the sheet
- **Steps:** Trigger a booking submit that the server rejects (e.g. class just filled, out of credits, time conflict, already booked).
- **Expected:** The sheet replaces the confirm UI with "Can't book this class" + the class name/studio + a code-specific message from `bookingErrorCopy` (e.g. "This class just filled up." for `session_full`, "You're out of credits for this period." for `credits_exhausted`, "You already have a class booked at this time." for `time_conflict`) + a single "GOT IT" dismiss button. `bookingFailed` telemetry fires with the code.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingCopy.kt (`bookingErrorCopy`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`bookingError` computation), sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/BookingSheet.kt (`errorMessage` branch), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt (`confirmBooking` catch)
- **Platforms:** shared

### CLASS-13 — Out-of-credits CTA state
- **Steps:** Log in as `accounts.<device>.member_no_credits` (a covering wallet granting 0 credits) and open detail for any bookable, open class.
- **Expected:** `bookCtaState` resolves `BookCta.OutOfCredits` ("OUT OF CREDITS", disabled); the sticky CTA shows that label and is not tappable.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingEligibility.kt (`bookCtaState`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt (`load`)
- **Platforms:** shared

### CLASS-14 — No-active-membership CTA, and the unknown state it must not be confused with
- **Steps:** (a) Open detail as `accounts.<device>.member_no_membership`, whose wallet window has fully elapsed. (b) Open detail as `accounts.<device>.member`, whose membership IS fine, but with the FIRST `/memberships/me` fetch failing — arm a fault on `/api/v1/memberships/me` with `times: 1` (driver-playbook.md "Fault injection") so only that one fetch fails, the session and class detail still load, and reopening finds the endpoint healthy (a full outage shows FullScreenError instead and does not exercise this). Then restore the endpoint and reopen.
- **Expected:** (a) `bookCtaState` resolves `BookCta.NotBookable` ("NO ACTIVE MEMBERSHIP"). (b) the CTA reads `BookCta.Unknown` ("BOOKING UNAVAILABLE"), NOT "NO ACTIVE MEMBERSHIP": a failed fetch establishes nothing about the account, so the button must not claim the member has no membership. The `ErrorSnackbar` ("Couldn't refresh" + Retry) appears alongside it, and reopening with the endpoint healthy resolves to a real evaluated state. Both render as a single centered line with no time/day sub-stamp (`showCtaSubStamp = false`) and are disabled. Superseded 2026-08-23: `_ctaState` previously defaulted to `NotBookable`, so a failed cold fetch fell through to it and stated something false about a paying member's account.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingEligibility.kt (`BookCta.NotBookable`, `BookCta.Unknown`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt (`_ctaState` initial value, `membershipLoadFailed`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`showCtaSubStamp`)
- **Platforms:** shared

### CLASS-15 — Outside-membership-window CTA state
- **Steps:** Log in as `accounts.<device>.member_outside_window`, whose wallet ends 5 days out, and open detail for `classes.outside_window` (13 days out). Check a near class too: the same member CAN book inside the window, which is the boundary this entry is about. The sub-line names covered MONTHS, so it only reads naturally when those 13 days cross a month boundary.
- **Expected:** The CTA overrides to "OUTSIDE YOUR MEMBERSHIP" with a sub-line naming the gap, e.g. "July credits don't cover August."; this outranks the booking-window-not-open state. Attempting to book anyway (stale UI/race) surfaces `outsideWindowCopy(coveredMonths)` in the sheet, naming no price and pointing to concierge.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt (`_outsideWindow`, `OutsideWindowInfo`, `load`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingCopy.kt (`outsideWindowCopy`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`ctaSubOverride`)
- **Platforms:** shared

### CLASS-16 — Already-booked CTA reflects live status on return
- **Steps:** For the `CONFIRMED ✓` half, open `accounts.<device>.member`'s seeded reservation from the manifest (already `confirmed`, so no ops action is needed) via My Bookings → the row, leave, and return. For the `REQUESTED` half, book any ordinary regression-studio class (CLASS-08), leave Class Detail, and return to it.
- **Expected:** The CTA reads "CONFIRMED ✓" if ops confirmed the booking, or "REQUESTED" (no checkmark) if still pending — sourced from the live `existingBooking.status`, not just "already booked". Tapping the CTA (still enabled) opens the cancel sheet instead of the booking sheet.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`ctaLabel` when-block, `onClick`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt (`load`, `_existingBooking`)
- **Platforms:** shared

### CLASS-17 — Booking-info callout for an existing booking
- **Steps:** Open detail for a class the member already has a live booking on, where the booking carries a spot/status-specific note.
- **Expected:** A "Booking info" Paper card renders between the summary strip and the instructor row, sourced from `bookingInfoOrNull(existing)`; absent when there's no note.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingNotes.kt (`bookingInfoOrNull`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/BookingInfo.kt (`BookingInfoCallout`)
- **Platforms:** shared

### CLASS-18 — Cancellation flow: cancel sheet and confirm
- **Steps:** Use the `classes.late_cancel_active` session from the manifest (the same fixture CLASS-20 uses), booked and then fulfilled per the runbook's Phase 3 fulfilment step so the member holds a live `confirmed` booking on it. Tap the sticky CTA to open the cancel sheet, then tap "CANCEL BOOKING". (The seeded per-device reserved booking is the fallback fixture if that session is unavailable, but it is outside its cutoff and so exercises only the refund branch.)
- **Expected:** `CancelBookingSheet` shows the class name, spot label (if any), and a forfeit/refund line driven by `cancelPolicy.willForfeitCredit`. Confirming calls `cancelBooking`; on success the sheet closes, `existingBooking` clears, the CTA reverts to bookable/full/etc, and `bookingCancelled` telemetry fires with `creditRefunded`/`lateCancel`.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`CancelBookingSheet`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt (`openCancelSheet`, `confirmCancel`)
- **Platforms:** shared

### CLASS-19 — Late-cancel bolded pre-booking copy
- **Steps:** Use the `classes.late_cancel` session id from the manifest — Regression Late Cancel Studio, seeded with `late_cancel_cutoff_minutes = 1440`, at +3 days 18:00 ET (far enough out to still be freely cancellable). Drill in from its schedule row and open the booking confirmation sheet.
- **Expected:** The cancel-cutoff line reads "Free to cancel up to **24 hours** before class. After that, cancelling still costs the credit." with the window duration rendered bold + Wood-colored (`bookingCancelCopy`/`lateCancelWindowLabel`). When the server sends no window (older studios), it falls back to the generic "Free to cancel until the studio cutoff..." line.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/LateCancelWindow.kt (`bookingCancelCopy`, `lateCancelWindowLabel`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/BookingSheet.kt (cancel-copy line)
- **Platforms:** shared

### CLASS-20 — Late-cancel forfeit warning on the cancel sheet
- **Steps:** Use the `classes.late_cancel_active` session id from the manifest — the **only** fixture from which `cancelPolicy.willForfeitCredit == true` is reachable (same 24h studio, seeded ~12h out, i.e. inside the cutoff). Book it in-app, then apply the runbook's Phase 3 fulfilment step (a member-initiated booking on a `manual` studio lands `requested` with no `external_booking_id`, and all three of confirmed + external id + past-cutoff must hold), re-open the booking, and open the cancel sheet.
- **Expected:** The sheet shows "Cancelling now forfeits this class's credit. You're past the studio cutoff." in Warning color, instead of the refund-affirming "You'll get your credit back." (Moss) shown when the credit will be refunded.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`CancelBookingSheet`, `willForfeitCredit` branch)
- **Platforms:** shared

### CLASS-21 — Cancellation failure keeps the sheet open with a retry message
- **Steps:** Trigger a cancel-booking request that fails on the server.
- **Expected:** `CancelState.Failed(code)` renders `cancelErrorCopy(code)` in Burnt Nectar inside the sheet; the booking remains live and `bookingCancelFailed` telemetry fires with the same `connection_failed`/`server_failed` `reason_code` as the UI state. The two codes `confirmCancel` can actually emit both route through `transportErrorCopy`, so the visible line is "Couldn't reach Arcana. Check your connection and try again." (connection) or "Something went wrong on our end. Try again in a moment." (server) — not the same string for both.
_Corrected after run 2026-08-27: the Expected claimed "Couldn't cancel. Try again." for both codes. `BookingCopy.kt` reaches that string only via the `cancel_failed` code, which its own comment marks as having no current producer (back-compat net), and via `cancelErrorCopy`'s unrecognized-code fallback — neither of which `confirmCancel` produces._
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt (`confirmCancel` catch), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingCopy.kt (`cancelErrorCopy`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`CancelBookingSheet`, `CancelState.Failed`)
- **Platforms:** shared

### CLASS-22 — My Bookings: list, cancel confirmation dialog, and forfeit copy
- **Steps:** From Home, open "My Bookings" (My Bookings non-tab destination). The list should already hold the seeded confirmed reservation from `accounts.<device>.member`. Tap "Cancel" on an upcoming booking — to see the forfeit copy, cancel the `classes.late_cancel_active` booking prepared for CLASS-20 (fulfilment step applied); to see the refund copy, cancel any other upcoming row.
- **Expected:** Upcoming and Past sections render separately (Past has no Cancel link); the cancel confirmation `AlertDialog` shows "It's inside the cancellation window, so your credit won't come back." when `cancelPolicy.willForfeitCredit`, else "Your credit will be refunded."; confirming calls `vm.cancel(b.id)`.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/MyBookingsScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/MyBookingsViewModel.kt
- **Platforms:** shared

### CLASS-23 — My Bookings: loading caption, empty state, and no pull-to-refresh
- **Steps:** Open My Bookings (a) the instant it loads, before the fetch resolves, and (b) as a member with zero upcoming and zero past bookings. Separately, attempt a pull-down gesture on the list.
- **Expected:** (a) `MyBookingsUiState.Loading` renders a bare "Loading…" `Caption` — no shimmer skeleton. (b) Both the Upcoming and Past sections are gated on `s.upcoming.isNotEmpty()`/`s.past.isNotEmpty()`, so a member with no bookings at all sees a header with a completely empty `LazyColumn` below it — no "Nothing booked yet." or similar empty-state copy renders. There is no `PullToRefreshBox` on this screen (unlike Home/Schedule/ClassDetail/Profile) — a pull gesture does nothing.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/MyBookingsScreen.kt (lines 59, 61-87), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/MyBookingsViewModel.kt
- **Platforms:** shared

### CLASS-24 — Booking sheet dismissed via swipe-down/scrim-tap fires abandonment telemetry
- **Steps:** Open the booking confirmation sheet (CLASS-08) and dismiss it by swiping down or tapping the scrim, without tapping CONFIRM or GOT IT.
- **Expected:** `ModalBottomSheet`'s `onDismissRequest` fires `onDismiss`, which calls `telemetry.bookingSheetAbandoned(sessionId, reachedSpotSelection, hadSelectedSpot)` — distinct from a successful booking or an in-sheet error dismissal.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/BookingSheet.kt (line 76, `onDismissRequest`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt (line 213, `bookingSheetAbandoned`)
- **Platforms:** shared

### CLASS-25 — Credit count decrements on booking and restores on a refunded cancel
- **Steps:** Note the credits-remaining count on Home/Profile/the booking sheet, book a class (CLASS-08), then re-check the count; separately, cancel that booking where `cancelPolicy.willForfeitCredit == false` and re-check again.
- **Expected:** The booking sheet shows "This uses 1 of N credits" before confirming; after a successful booking, `BookingViewModel.load()` re-reads `/me` and the credits-remaining count visible on Home's manifesto card ("{N} classes remaining.") and Profile's stats cell both drop by 1. After a non-forfeiting cancel, the same re-fetch shows the credit restored to its prior count. Returning to Home shows the new count WITHOUT a manual pull-to-refresh: HomeScreen refetches from a `LifecycleResumeEffect`, so the Next-Up card and manifesto count are current on every return to the foreground. The path that matters is the TAB SWITCH (book from Schedule, tap back to Home), not an in-tab pop: measured 2026-08-23, an in-tab push/pop rebuilds Home's composition on both platforms and already refetched, but an iOS tab switch back to Home fetched ZERO times before the resume effect and once after, because iOS tab compositions persist. Android is one fetch either way, since the resume effect replaced the old `LaunchedEffect` rather than joining it. Resume refreshes are also conflated and rate-limited, so they cannot be spammed: only one fetch is ever in flight (a newer one cancels its predecessor, so responses cannot land out of order and show older data), and a resume within 2s of the previous fetch is skipped outright. Measured on the emulator: six rapid tab flips produce ONE `/memberships/me`, a deliberate return after settling produces one. Pull-to-refresh and the error-state retry are deliberately exempt, since the member asked for those.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/BookingSheet.kt (credit-usage line), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt (`load()` re-read after booking/cancel), sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt (`ManifestoCard`, `LifecycleResumeEffect`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/home/HomeViewModel.kt (`load`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt (stats row)
- **Platforms:** shared

### CLASS-26 — Full-screen spot map: pinch-zoom, pan, and close
- **Steps:** Open the booking sheet for a grid studio with spot coordinates (CLASS-09) and expand to the full-screen room map. Pinch to zoom in/out past the clamped range, drag to pan near an edge, and tap "Close room map".
- **Expected:** `SpotMapFullScreen` opens as a `Dialog` with pinch-zoom (`detectTransformGestures`) clamped to `minScale..maxScale`, drag-to-pan clamped at the content edges (`clampAxis`), and a `SpotMapLegend`. On first open the map performs a deferred one-frame initial fit-zoom (guarded by a `scale.isNaN()` check) rather than snapping instantly. **No two circles touch or overlap at any studio** — `spotContentSize` sizes the canvas off the closest pair so they sit `SPOT_GAP_FRACTION` of a diameter apart, on every room, including shallow two-row rooms (NRTHRN Strong) and dense floors (Barry's Noho, AARMY). **Every station label renders in full, never ellipsized** — a name too wide for its circle at the design size (The Pack's "10,BENCH", Barry's "Instructor") shrinks to fit, and one that already fits is untouched. The "Close room map" `IconCircle` returns to the booking sheet's inline `SpotSelector`.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/SpotMapFullScreen.kt (259 lines; `detectTransformGestures`, `clampAxis`, line 142 `scale.isNaN()` guard, `SpotMapLegend`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/SpotLayout.kt (`spotContentSize`, `spotCenters`, `maxSpotDot` — the shared no-overlap math), sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/SpotMap.kt (`SpotDot` auto-shrinking label)
- **Platforms:** shared

## FAV

### FAV-01 — Favorites-scoped schedule defaults on cold start when favorites exist
- **Steps:** Sign in as a member with saved favorites and land on Schedule.
- **Expected:** `ScheduleViewModel.init` fetches favorites before the first schedule fetch; `scope` defaults to `ScopeMode.Favorites`, and the schedule is narrowed to `favoritesRepository.favorites.value.expandedLocationIds()`.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`init`, `effectiveLocationIds`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/favorites/FavoritesRepository.kt
- **Platforms:** shared

### FAV-02 — Favorites panel: read-only list + manage link
- **Steps:** With Favorites scope active and favorites present, tap the active FAVORITES segment again to expand its panel.
- **Expected:** Each favorited studio/location renders as a read-only row (a Moss dot + name + "All locations" or the specific location detail — never a tappable checkbox); a "MANAGE IN PROFILE" link calls `onManageFavoritesTapped()` telemetry then `onManageFavorites` navigation callback. `favoritesDropdownOpened` telemetry fires once when the panel is revealed.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`ScheduleFilterSection` fav panel, `FavoriteEntryRow`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`onFavoritesDropdownShown`, `onManageFavoritesTapped`, `favoriteEntries`)
- **Platforms:** shared

### FAV-03 — Favorites add/remove reflected live on Schedule
- **Steps:** While the Book tab is on the back stack, add or remove a favorite in the Profile favorites manager, then return to Book.
- **Expected:** `favoritesRepository.favorites` collector in `ScheduleViewModel.init` picks up the change; if the member wasn't actively narrowing a manual studio subset, scope re-evaluates to `Favorites` (if favorites now non-empty) or `AllStudios` (if cleared to empty), filters reset, and a refetch runs — all without disrupting an active Custom (All Studios subset) filter session.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`init`, `favoritesRepository.favorites.collect`)
- **Platforms:** shared

### FAV-04 — Favorites toggle unavailable with zero favorites
- **Steps:** Log in as `accounts.<device>.member_no_favorites` (seeded with zero favorites) and open Schedule.
- **Expected:** No Favorites/All-Studios toggle renders — only a single "ALL STUDIOS" Ink bar that opens/closes the studio accordion on tap; the `hasFavorites` flag gates the toggle's existence.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`ScopeToggle`, `!hasFavorites` branch)
- **Platforms:** shared

### FAV-05 — Favorites fetch failure keeps prior cache, never empties silently
- **Steps:** Fail ONLY the favorites endpoint, leaving the rest of the API up (`curl -X POST localhost:8000/api/v1/_faults/ -H 'Content-Type: application/json' -d '{"path": "/api/v1/users/me/favorites/", "status": 500}'`; see driver-playbook.md "Fault injection"). Drive both cases: a member's very first favorites load, and a later refresh where a real favorites set is already cached. Clear with `curl -X DELETE localhost:8000/api/v1/_faults/`.
- **Expected:** The repository logs a warning and returns/keeps its previously cached `FavoritesDto` (or null if never loaded) rather than clearing it; `favoritesKnown` stays false only when truly never loaded, which suppresses the "choose favorites" nudge (SCHED-12) so a member who may already have favorites isn't wrongly nudged. **Silent-success note:** this guarantee depends on `bodyOrThrow`. `fetchFavorites()` returns `FavoritesDto`, whose fields all default to empty. Before `bodyOrThrow`, a 5xx with a JSON error body did not throw here at all — it silently deserialized into an empty `FavoritesDto`, and `refresh()` wrote it straight into `_favorites.value`, **overwriting a real cached favorites set with nothing** instead of ever reaching this entry's catch block. `bodyOrThrow` is what makes "keeps prior cache" true for every 5xx shape, not just network-level failures.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/favorites/FavoritesRepository.kt (`refresh`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt (`fetchFavorites`, `bodyOrThrow`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`favoritesKnown`)
- **Platforms:** shared

### FAV-06 — Favorites cleared on logout
- **Steps:** Log in as `accounts.<device>.member` (two seeded favorites) and open Schedule so they cache, log out, then log in as `accounts.<device>.member_no_favorites`.
- **Expected:** `FavoritesRepository.clear()` is wired as an `onSessionCleared` hook (via `AppSessionController`), setting `favorites.value = null` so the next member never briefly sees the prior member's favorites.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/favorites/FavoritesRepository.kt (`clear`)
- **Platforms:** shared

### FAV-07 — Studio Selection screen: hero copy and accordion expand
- **Steps:** Open Studio Selection (Profile → Manage, or Schedule's "Manage Favorites"). Tap a studio row's chevron to expand it.
- **Expected:** A hero renders "Make it\nyours." (Display), "Save the places you keep coming back to." (body), and "Change anytime." (Moss accent). Tapping a chevron expands/collapses that studio's location list via `toggleExpanded`.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/studios/StudioSelectionScreen.kt (lines 163-170, hero; `toggleExpanded` panel), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/studios/StudioSelectionViewModel.kt (`toggleExpanded`)
- **Platforms:** shared

### FAV-08 — Studio Selection: toggling a whole studio vs. an individual location
- **Steps:** With a studio's individual locations partially selected, tap the studio row itself (not the chevron) to select the whole studio. Separately, expand a studio and tap every one of its individual locations one by one.
- **Expected:** Selecting the whole studio (`toggleStudio`) clears that studio's individually-picked locations (redundant once the whole studio is picked). Toggling on every individual location under a studio (`toggleLocation`) promotes the pick to a whole-studio selection — the mirror of SCHED-13's All-Studios accordion behavior, but on this screen.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/studios/StudioSelectionViewModel.kt (`toggleStudio`, `toggleLocation`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/studios/StudioSelectionScreen.kt
- **Platforms:** shared

### FAV-09 — Studio Selection: Save favorites CTA and delta telemetry
- **Steps:** Change favorite selections (add and remove a mix of whole-studio and location-grain picks), tap the sticky "Save favorites" CTA.
- **Expected:** The CTA label switches to "Saving…" while the save is in flight, then the screen closes on success. `emitFavoriteDeltas` fires `favorite_added`/`favorite_removed` per changed studio/location versus the previously-saved set, plus a summary `favorites_saved`, and `setFavoriteProfile` updates the telemetry profile. (See ERR-15 for the save-failure path.)
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/studios/StudioSelectionScreen.kt (line 139, CTA label), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/studios/StudioSelectionViewModel.kt (`emitFavoriteDeltas`, `setFavoriteProfile`)
- **Platforms:** shared

## PROFILE

### PROFILE-01 — Hero loads member info (name, initials, member number)
- **Steps:** Sign in and land on the Profile/You tab (or switch to it). Observe the Ink hero while `ProfileViewModel.load()` is in flight, then after it resolves.
- **Expected:** Hero shimmers (avatar circle, name block, member-number line) while `ProfileUiState` is `Loading`. Once `Success`, it shows the member's full display name (or email if no display name), a Lime-on-Moss avatar circle with `initials`, and `"Member · No. <memberNumber>"` (or plain `"Member"` when `memberNumber` is null).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileViewModel.kt
- **Platforms:** shared

### PROFILE-02 — Stats row: sessions, week streak, credits
- **Steps:** On Profile, view the three-cell stats row below the avatar/name once loaded. Repeat as a member with no live wallet (lapsed or between cohorts, `creditsRemaining == null`).
- **Expected:** Each cell shimmers individually while loading, then shows `lifetimeSessions`, `weekStreak`, and `creditsRemaining` as large Lime numeral text with an Overline label underneath. Divider hairlines separate the three cells. With no live wallet the Credits cell resolves to a plain hyphen `-` (never an em dash), NOT a shimmer: null is `StatCell`'s loading placeholder, so leaving it unresolved left that one cell pulsing forever beside two settled numbers and read as a hung screen. A shimmer in the Credits cell after `/me` has answered is a defect.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileViewModel.kt
- **Platforms:** shared

### PROFILE-03 — Next-period ("upcoming") wallet caption
- **Steps:** As a beta member who holds next month's wallet while still inside the current month, view Profile.
- **Expected:** Below the stats row, a caption reads `"Next: <upcomingMonth or 'upcoming'> · <upcomingCredits> credits"`. Absent entirely for members without an upcoming period (`upcomingCredits == null`).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileViewModel.kt
- **Platforms:** shared

### PROFILE-04 — Membership row shows tier name or "Inactive"
- **Steps:** View the Account section's "Membership" row for a member with an active current-period wallet, then for one with none.
- **Expected:** Active member: row's right-hand text shows the membership tier name (e.g. "Alpha Tester"). Lapsed/no current period (`creditsRemaining == null`): shows "Inactive". The row itself has no `onClick` (not tappable — no chevron rendered).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt
- **Platforms:** shared

### PROFILE-05 — Profile load error keeps chrome, shows a bottom snackbar
- **Steps:** Force `/memberships/me` to fail (e.g. via Developer Settings pointing at an unreachable base URL) and load Profile fresh (not a refresh — first load).
- **Expected:** **Behavior changed 2026-08-18.** A COLD-LOAD failure (`ProfileUiState.Error`) now replaces the whole tab with the shared `FullScreenError`, matching Home and Schedule — same CONNECTION/SERVER copy, vertically centred, "TRY AGAIN" wired to `ProfileViewModel.retry()` with the dot-matrix loader in the button while in flight. It previously showed an `ErrorSnackbar` over a hero that shimmered indefinitely, which read as "still loading" while an error was on screen, and contradicted the component rule (takeover when there is nothing to show). The snackbar is now reserved for a failed REFRESH while `Success` content is on screen: `ErrorCopy.REFRESH_FAILED` with a Lime "Retry" and an `X`, driven by `refreshFailed` exactly as on Home. **A "Sign out" control stays overlaid on the takeover** (same principle as CLASS-02's close button): Profile's list is the only place sign-out lives, and Developer Settings is reachable only from the signed-out screen, so a full takeover would otherwise strand a member with an unreachable server — no sign out, no way to change the base URL, and on iOS a reinstall does not clear the Keychain.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileViewModel.kt (`fetch`, `load`, `retry`, `refreshFailed`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt (`FullScreenError`, `ErrorSnackbar`)
- **Platforms:** shared

### PROFILE-06 — Refresh failure preserves prior content
- **Steps:** Load Profile successfully, then pull-to-refresh (or background the app and resume) while the network is down.
- **Expected:** The existing `Success` state remains on screen unchanged (no flash to error or shimmer) — `fetch()` only downgrades to `Error` when the current state is not already `Success`.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileViewModel.kt
- **Platforms:** shared

### PROFILE-07 — Pull-to-refresh spinner
- **Steps:** On Profile, pull down from the top of the list.
- **Expected:** `PullToRefreshBox`'s spinner shows while `vm.isRefreshing` is true; releases once the membership + favorites fetch completes; content does not shimmer during a refresh (only on first load).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileViewModel.kt
- **Platforms:** shared

### PROFILE-08 — "Your favorites" section: loading, empty, and populated states
- **Steps:** View the favorites section (a) immediately on load (favorites not yet fetched), (b) for a member with zero favorites, (c) for a member with whole-studio and location-grain favorites, (d) with the favorites fetch failing on a FIRST-EVER load and nothing cached (force `/users/me/favorites/` to error while `/me` stays healthy), then tap its Retry with the endpoint restored, (e) with the fetch failing on a LATER refresh, after favorites have already loaded once.
- **Expected:** (a) a single shimmer row placeholder. (b) "No favorites yet" body text. (c) a numbered list (01, 02, …) of rows — whole-studio favorites first by name, then location-grain favorites formatted "STUDIO · LOCATION" (middot, not a dash) with the brand prefix stripped from the location name; rows are flat (no chevron/tap affordance). (d) an `InlineError` card in place of the section — the same treatment a failed schedule day gets — reading "THIS DIDN'T LOAD." with a Retry link; the rest of the profile (hero, stats, account rows) stays live, and Retry restores the section in place. (e) NO error: the previously loaded favorites stay on screen, which is why `FavoritesRepository.refresh` swallows failures at all. Superseded 2026-08-23: this entry previously said a failed first fetch shows the (a) shimmer indefinitely and warned testers away from it, which documented the defect as intended behavior and is why the run scored it a pass.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt (`rowLabel`, `favoritesError`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileViewModel.kt (`favoritesError`, `retryFavorites`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/favorites/FavoritesRepository.kt (`refresh`, `refreshCatching`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt (`InlineError`)
- **Platforms:** shared

### PROFILE-09 — "Manage" link opens Studio Selection
- **Steps:** Tap "Manage" next to the "Your favorites" header.
- **Expected:** Navigates to the Studio Selection screen (`onManageStudios` callback) for editing favorites; Profile tab bar disappears (non-tab destination).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt
- **Platforms:** shared

### PROFILE-10 — Concierge row navigates to concierge request
- **Steps:** Tap the "Concierge" row in the Account section.
- **Expected:** Opens the Concierge Request screen (`onOpenConcierge` callback); chevron is shown since the row has an `onClick`.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt
- **Platforms:** shared

### PROFILE-11 — Settings gear opens Edit Profile
- **Steps:** Tap the gear icon (`IconCircle`, content description "Settings") in the top-right of the Profile hero.
- **Expected:** Navigates to Edit Profile (`onOpenSettings`); the icon is accessibly labeled "Settings" (confirmed via the Android CLI accessibility pass — was previously the one unlabeled control on this screen).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt
- **Platforms:** shared

### PROFILE-12 — Sign out logs out immediately (no confirmation) (duplicate — see AUTH-11)
- **Steps:** See AUTH-11 — same row, same file. Do not run this entry separately.
- **Expected:** See AUTH-11.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt
- **Platforms:** shared

### PROFILE-13 — Delete account confirmation dialog
- **Steps:** Tap "Delete account" at the bottom of Profile.
- **Expected:** An `AlertDialog` appears: "Delete account?" with body copy warning of permanent removal, a Danger-colored "Delete" confirm button and a "Cancel" dismiss button. Cancel or scrim-dismiss closes it with no request sent.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt
- **Platforms:** shared

### PROFILE-14 — Delete account request success dialog
- **Steps:** Confirm "Delete" in the delete-account dialog with a reachable server.
- **Expected:** `DeleteAccountViewModel.submit()` posts a concierge request (`ACCOUNT DELETION REQUEST…`); on success a second `AlertDialog` "Request received" appears stating the account will be permanently deleted within 30 days; "OK" dismisses and resets state to `Idle`. Account is NOT deleted immediately (async/manual founder completion).
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/DeleteAccountViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt
- **Platforms:** shared

### PROFILE-15 — Delete account request failure dialog
- **Steps:** Confirm "Delete" while the concierge endpoint fails (network error or `ConciergeError`).
- **Expected:** `DeleteAccountState.Failed` renders an AlertDialog "Couldn't submit" / "Something went wrong submitting your request. Please try again."; "OK" resets to `Idle` so the member can retry from "Delete account" again.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/DeleteAccountViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt
- **Platforms:** shared

### PROFILE-16 — Account footer version string
- **Steps:** Scroll to the bottom of Profile past "Delete account".
- **Expected:** Manifesto footer shows the fixed line "Not for the casual." followed by an Overline reading `"Arcana · v<appVersionName()>"` where the version is the real platform app version (Android `versionName` / iOS bundle short version), not a hardcoded string.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/Platform.kt
- **Platforms:** shared

### PROFILE-17 — Edit Profile pre-fills current values
- **Steps:** From Profile, tap the settings gear to open Edit Profile.
- **Expected:** Screen shows a centered loader while `EditProfileViewModel.load()` runs `GET` profile, then the form renders with first/last name, phone number, gender, birthday (masked MM/DD/YYYY), street address, apt/unit, city, state, and ZIP all pre-filled from the server response; Save is disabled (muted Mist/Ash pill) since nothing has changed yet.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileViewModel.kt
- **Platforms:** shared

### PROFILE-18 — Edit Profile load error with retry
- **Steps:** Open Edit Profile while the profile-fetch endpoint fails.
- **Expected:** Renders `LoadErrorState`: "Couldn't load." headline, an error message body, and a "Retry" primary CTA that re-invokes `viewModel::load`; a Close (X) is still available to bail out without saving. See ERR-16 for the silent-success history: before `bodyOrThrow`, a 5xx with a JSON error body reached a blank editable form instead of this error state.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileScreen.kt (`LoadErrorState`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileViewModel.kt (`load`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt (`fetchProfile`)
- **Platforms:** shared

### PROFILE-19 — Save button gates on dirty AND valid
- **Steps:** Open Edit Profile; (a) change nothing, (b) change a field then revert it to the original value, (c) change a field to a new valid value, (d) blank out a required field (e.g. clear First name).
- **Expected:** Save stays disabled/muted for (a) and (b) (`isDirty` false). Save becomes enabled (Moss pill, tappable) for (c). For (d), Save is disabled again even though the field is dirty, because `isValid` fails on a blank required field.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileScreen.kt
- **Platforms:** shared

### PROFILE-20 — Birthday field auto-mask and inline validation error
- **Steps:** Focus the Birthday field and type digits `04121995` continuously (no slashes).
- **Expected:** Field visually renders `04/12/1995` as you type (slashes auto-inserted after positions 2 and 4 via `DateMaskVisualTransformation`); underlying stored value stays digit-only (`04121995`, capped at 8 digits). Typing a date that is malformed or under the minimum age shows an inline `birthdayError` beneath the field and blocks Save; the error clears the moment a valid 18+ date is entered, and no error shows while the date is still partially typed.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileViewModel.kt
- **Platforms:** shared

### PROFILE-21 — Gender dropdown selection
- **Steps:** Tap the Gender field and pick "Male" / "Female" / "Other".
- **Expected:** `ArcanaDropdownField` shows the three `GENDER_OPTIONS` (value codes `male`/`female`/`other`, Title-Case labels); selecting one updates the field and marks the form dirty; leaving Gender blank fails `isValid` and blocks Save.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileViewModel.kt
- **Platforms:** shared

### PROFILE-22 — Every field except Apt / unit blocks Save when blank
- **Steps:** Clear each field independently while the rest are valid and dirty, and attempt Save: First name, Last name, Phone number, Gender, Birthday, Street address, City, State, ZIP code. Then separately, sign in as a member whose phone, gender or birthday was never captured (an account predating that field) and edit only an unrelated field such as City.
- **Expected:** Save stays disabled in every case. "Apt / unit" is the single optional field and does NOT block Save when blank. This is deliberate and matches the claim-your-name form, which requires the same set: every profile created since that screen shipped has all of it, and nothing already captured may be unset. A member on a pre-requirement account must fill the missing field in before any edit of theirs saves; that forced backfill is the intent, not a defect (adjudicated 2026-08-22, Cole — see the PROFILE-22 card). The disabled Save is currently silent about which field is missing.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileViewModel.kt (`isValid`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionViewModel.kt (`isValid`, the mirrored signup rules)
- **Platforms:** shared

### PROFILE-23 — Save persists and closes back to Profile
- **Steps:** Make a valid edit (e.g. change last name), tap "Save" with a reachable server.
- **Expected:** Save button shows a spinner (`isSaving`) while the PATCH is in flight; on success `State.Saved` triggers `onClose()` automatically (a brief centered loader shows during the pop) and the screen returns to Profile with a "Manage" affordance disabled. Re-opening Edit Profile afterward re-fetches and shows the newly persisted values (persistence confirmed via a fresh load, not just local state).
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileScreen.kt
- **Platforms:** shared

### PROFILE-24 — Save failure shows inline form error, stays editable
- **Steps:** Make a valid edit, tap "Save" while the PATCH endpoint fails (network/server error).
- **Expected:** A `FormErrorBanner` ("Couldn't save your changes. Check your connection and try again.") appears above the fields; `isSaving` resets to false so Save is tappable again; entered field values are preserved (not reset to original). See ERR-17 for the silent-success history: before `bodyOrThrow`, a 5xx with a JSON error body reported this save as successful (popped back to Profile) without persisting anything.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileViewModel.kt (`save`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileScreen.kt (`FormErrorBanner`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt (`updateProfile`)
- **Platforms:** shared

### PROFILE-25 — Close (X) discards unsaved changes
- **Steps:** Make edits to one or more fields without saving, tap the Close (X) icon in the top-left header.
- **Expected:** Screen pops immediately back to Profile with no PATCH sent and no confirmation prompt; re-opening Edit Profile shows the original (unmodified) server values.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileScreen.kt
- **Platforms:** shared

### PROFILE-26 — Tab-key field traversal in Edit Profile
- **Steps:** With a hardware keyboard (or Tab key via simulator), focus "First name" and press Tab repeatedly, including Shift+Tab.
- **Expected:** Focus advances exactly one field per Tab (`onPreviewKeyEvent` intercepts hardware Tab to call `focusManager.moveFocus` once), and Shift+Tab moves focus backward one field — no double-traversal.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileScreen.kt
- **Platforms:** shared

## CONCIERGE

### CONCIERGE-01 — Concierge Request screen renders and gates Send on a non-blank message
- **Steps:** From Profile, tap the "Concierge" row to open the Concierge Request screen (reached via PROFILE-10 / TEL-06's `$screen`).
- **Expected:** Header reads "Reach the founders" with intro body copy, followed by a "Your message" `ArcanaTextField`. The Send CTA (`canSubmit`) is disabled while the message is blank and enables once any non-blank text is entered.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/concierge/ConciergeRequestScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/concierge/ConciergeRequestViewModel.kt (`canSubmit`)
- **Platforms:** shared

### CONCIERGE-02 — Message field truncates at 1000 characters instead of rejecting the paste
- **Steps:** Paste or type a message longer than 1000 characters into "Your message".
- **Expected:** The stored value is truncated to `MESSAGE_MAX_LENGTH` (1000) characters (`value.take(MESSAGE_MAX_LENGTH)`) rather than rejecting the input or showing a validation error — the field silently caps at 1000 chars.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/concierge/ConciergeRequestViewModel.kt (`MESSAGE_MAX_LENGTH`, `updateMessage`)
- **Platforms:** shared

### CONCIERGE-03 — Submit failure distinguishes connection vs. server vs. a typed server reason, CTA re-enables and clears on edit
- **Steps:** Enter a message and tap Send while the submit call fails three ways: (a) a network/timeout exception with no HTTP response, (b) a 5xx response with no typed reason code, (c) a typed `ConciergeError(code)` from the server (e.g. `concierge_failed`).
- **Expected:** In all three cases `ConciergeSubmit.Failed(code)` re-enables the Send CTA for another attempt, and editing the message afterward clears the error back to `Idle`. (a) The connection failure renders `code = "connection_failed"` → "Couldn't reach Arcana. Check your connection and try again." (b) The 5xx renders `code = "server_failed"` → "Something went wrong on our end. Try again in a moment." (c) The typed reason still wins over the transport classification: `ConciergeSubmit.Failed` carries the server's own code rather than collapsing it to connection/server. The screen has no per-code copy table (unlike Booking's `bookingErrorCopy`), so any code other than the two transport ones renders the same fallback line, "Couldn't send your message. Try again." (`transportErrorCopy(code) ?: "Couldn't send your message. Try again."`).
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/concierge/ConciergeRequestViewModel.kt (`submit`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/TransportErrorCopy.kt (`transportErrorCopy`, `transportFailureCode`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/concierge/ConciergeRequestScreen.kt (failure `Caption`)
- **Platforms:** shared

### CONCIERGE-04 — Successful submit shows a terminal "Sent" screen and fires telemetry
- **Steps:** Enter a message and tap Send with a reachable server.
- **Expected:** On success the screen replaces the form with a terminal state: "Message sent." followed by "We've got it. The founders will reach out to you directly." and a "Done" `PrimaryCta` that closes the screen; `concierge_request_submitted` telemetry fires on success (`concierge_request_failed` on the CONCIERGE-03 failure path).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/concierge/ConciergeRequestScreen.kt (lines 163-182), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/concierge/ConciergeRequestViewModel.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt (`Events.CONCIERGE_SUBMITTED`, `Events.CONCIERGE_FAILED`)
- **Platforms:** shared

## DEVSET

### DEVSET-01 — Hidden entry: 10 taps on the auth-screen wordmark (duplicate — see AUTH-13)
- **Steps:** See AUTH-13 — same gesture, same file. Do not run this entry separately.
- **Expected:** See AUTH-13.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/AuthScreen.kt
- **Platforms:** shared

### DEVSET-02 — Developer Settings shows the currently-active base URL
- **Steps:** Open Developer Settings (via DEVSET-01) on a fresh install with no override set.
- **Expected:** "Currently in use" shows the resolved value from `BaseUrlProvider.current` — `https://api.arcana.fit` on a fresh install — with an "USING DEFAULT" overline (Ash color). The "Base URL" input field is pre-filled with the same current value (`draft` initializes from `baseUrlProvider.get()`).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsViewModel.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/BaseUrlProvider.kt
- **Platforms:** shared

### DEVSET-03 — Save button enabled only on a non-blank, changed draft
- **Steps:** Open Developer Settings; observe Save with the field untouched, then type a value identical to the current URL, then type a different valid URL, then clear the field entirely.
- **Expected:** Save (`PrimaryCta`) is disabled when `draft` is blank or equal to `current`; becomes enabled only once the draft is both non-blank and different from the current value.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsScreen.kt
- **Platforms:** shared

### DEVSET-04 — Save with a valid URL applies the override and closes
- **Steps:** Type a valid URL (e.g. `https://foo.trycloudflare.com` or `http://localhost:8000`) into "Base URL" and tap Save (or press Done on the keyboard).
- **Expected:** `BaseUrlProvider.set()` normalizes trailing slashes off, persists it to `SecureStorage` under key `base_url`, and updates `current`; `DeveloperSettingsViewModel.status` becomes `Saved`, which immediately closes the screen (`onClose()`) back to Auth. The override takes effect on the very next outbound API call with no app restart.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsViewModel.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/BaseUrlProvider.kt
- **Platforms:** shared

### DEVSET-05 — Save with an invalid URL shows inline error, stays open
- **Steps:** Type a value that doesn't start with `http://` or `https://` (e.g. `foo.example.com`) and tap Save.
- **Expected:** `BaseUrlProvider.set()` throws `IllegalArgumentException` ("Base URL must start with http:// or https://"); `DeveloperSettingsViewModel.save()` catches it and sets `Status.Error(message)`; the screen does NOT close (the `onClose()` call is conditional on `Status.Saved`), and an uppercased Danger-colored error line renders below the Reset/Save controls with the exception message.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsViewModel.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/BaseUrlProvider.kt
- **Platforms:** shared

### DEVSET-06 — Editing the draft after a Save error clears the error
- **Steps:** Trigger DEVSET-05's error state, then type any additional character into "Base URL".
- **Expected:** `onDraftChange` resets `status` back to `Idle` on the very next keystroke, so the error line disappears immediately (not only on a subsequent Save attempt).
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsViewModel.kt
- **Platforms:** shared

### DEVSET-07 — "Reset to default" only appears when overridden
- **Steps:** Open Developer Settings with no override set; then set an override (DEVSET-04) and reopen Developer Settings.
- **Expected:** With no override (`isOverridden == false`, i.e. `current == defaultUrl`), no "Reset to default" row is rendered at all. Once an override is active, a Danger-colored "Reset to default" row appears below Save, and the "Currently in use" block additionally shows `"OVERRIDE · default is <defaultUrl>"` in Moss.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/BaseUrlProvider.kt
- **Platforms:** shared

### DEVSET-08 — Reset to default clears the override and closes
- **Steps:** With an override active, tap "Reset to default".
- **Expected:** `BaseUrlProvider.reset()` deletes the `base_url` key from `SecureStorage` and reverts `current` to `defaultUrl` (`https://api.arcana.fit`); the draft field updates to match; `DeveloperSettingsViewModel.reset()` sets status to `Saved` and the screen closes back to Auth (`onClose()` fires unconditionally in the row's click handler).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsScreen.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsViewModel.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/BaseUrlProvider.kt
- **Platforms:** shared

### DEVSET-09 — Base URL override persists across app restarts
- **Steps:** Set an override (DEVSET-04), fully kill and relaunch the app, then reopen Developer Settings via the 10-tap gesture.
- **Expected:** The override survives the restart — `BaseUrlProvider.load()` reads the persisted `SecureStorage` value on construction, so "Currently in use" and the pre-filled draft both show the previously-saved override, not the default, and API calls continue targeting it.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/BaseUrlProvider.kt
- **Platforms:** shared

### DEVSET-10 — Close (X) discards an unsaved draft edit, including on reopen
- **Steps:** Open Developer Settings, type a new URL into the field without tapping Save, then tap the Close (X) icon in the header. Reopen Developer Settings (10-tap wordmark) in the SAME app session and read the Base URL field.
- **Expected:** Screen closes back to Auth with no change persisted; `BaseUrlProvider.current`/the underlying stored override are untouched by the unsaved draft. On reopen the field shows the value actually in use, NOT the discarded draft: the screen is a composition toggle rather than a nav destination, so its ViewModel survives the close and the draft is re-synced from the provider on entry. A saved value does persist across the same close/reopen.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsScreen.kt (`LaunchedEffect(Unit) { viewModel.resetState() }`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsViewModel.kt (`resetState`)
- **Platforms:** shared

### DEVSET-11 — Environment super-property re-tags on override change
- **Steps:** Set a `localhost`/`10.0.2.2` override, then a `*.trycloudflare.com` override, then Reset to default, observing PostHog's `environment` super-property in a Debug-build telemetry echo (`▶ Telemetry` log tag) after each change.
- **Expected:** Each `set()`/`reset()` call re-derives and registers `environment` via `classifyEnvironment` on the new URL — `local` for localhost/10.0.2.2, `tunnel` for `*.trycloudflare.com`, `prod` for `api.arcana.fit` — so subsequent telemetry events are attributed correctly rather than polluting prod metrics.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/BaseUrlProvider.kt
- **Platforms:** shared

## NAV

### NAV-01 — Tab bar switches between Home / Book / You
- **Steps:** From any tab root, tap each of the other two tab bar items in turn.
- **Expected:** The displayed screen changes to match the tapped tab; the tapped item's icon/label highlights (Moss + Lime indicator dot on Android; system selection styling on iOS), and each tab's own scroll position/back stack is preserved when returning to it later in the session. The middle tab reads **BOOK** (renamed from SCHEDULE 2026-08-25). Only the two label strings changed: `ArcanaTab.Schedule`'s label and the Swift `Tab(...)` title. The enum constant, the `ArcanaDestination.Schedule` route and every telemetry name are deliberately still `Schedule`/`schedule` (see TEL-14) — do not "fix" that mismatch, it is what keeps the event stream continuous across the rename.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/TabBar.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`navigateToTab`, `popUpTo(...){saveState=true}`/`restoreState`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/TabRoots.kt (per-tab `ComposeUIViewController`s), iosApp/iosApp/ArcanaShell.swift (`TabView`)
- **Platforms:** shared

### NAV-02 — Per-tab state preservation across tab switches (Android)
- **Steps:** On Android, scroll down the Book tab, switch to Home, then switch back to Book.
- **Expected:** Schedule's scroll position and navigation back stack are restored exactly as left (via `saveState`/`restoreState` on the shared `NavHost`), not reset to the top.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`navigateToTab`)
- **Platforms:** Android-only

### NAV-03 — Per-tab state preservation across tab switches (iOS shell)
- **Steps:** On iOS, push a class detail from Schedule (Book), then use the ClassDetail close control to pop back to the Schedule root — **the native tab bar is hidden while a non-tab destination is on top, so there is no tab to tap until you pop** (that hiding is NAV-04's assertion). From the Schedule root, scroll the list and select a non-default day, switch to Home, then switch back to Book.
- **Expected:** Schedule comes back exactly as it was left — same selected day, same scroll offset, no reload shimmer — because each tab hosts its own `ComposeUIViewController` + `NavHost` and the composition persists across `TabView` switches rather than being torn down. Switching tabs does not reset the other tabs' state either.
- **Source:** sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/TabRoots.kt, iosApp/iosApp/ArcanaShell.swift (`refreshTabBarVisibility`, `perTabAtRoot`)
- **Platforms:** iOS-only
_Corrected after run 2026-08-27: the Steps said "push a class detail, switch to Home, switch back" and asserted the pushed screen was still on top. That gesture does not exist on iOS — `ArcanaShell.swift` hides the tab bar for any tab that is off its root, so a driver has no affordance to switch tabs mid-push. One lane blocked the entry for exactly that reason and was right to; the invariant is retested here through state that survives at the root._

### NAV-04 — Tab bar hides on pushed (non-tab) destinations
- **Steps:** From any tab root, navigate into a non-tab screen (ClassDetail, StudioSelection, MyBookings, EditProfile, ConciergeRequest).
- **Expected:** The bottom tab bar disappears while the pushed screen is on top, so a stray tab tap can't silently pop the in-progress flow off the stack. Returning to the tab root (back/close) brings the tab bar back.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`selectedTab` derived from `currentDestination?.hasRoute<...>()`, `bottomBar = { if (selectedTab != null) ... }`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/TabRoots.kt (`atRoot`, `onRootChanged`), iosApp/iosApp/ArcanaShell.swift (`.toolbar(shell.tabBarHidden ? .hidden : .visible, for: .tabBar)`)
- **Platforms:** shared

### NAV-05 — Android system back pops the current tab's stack, not the app
- **Steps:** On Android, push ClassDetail from Home, then press the system back button/gesture.
- **Expected:** Back pops ClassDetail and returns to the Home tab root (standard `NavHost` back-stack behavior via `popBackStack()`/system back), rather than exiting the app. Pressing back again from a tab root exits/backgrounds the app as normal (no custom `BackHandler` intercepts it).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (NavHost `composable<...>` `onClose = { navController.popBackStack() }` wiring), sharedUI/src/androidMain/kotlin/org/arcana/mobile/MainActivity.kt
- **Platforms:** Android-only

### NAV-06 — Deep link, cold start (welcome token)
- **Steps:** With the app not running and signed out, open a welcome link (`https://arcana.fit/welcome?token=XXX` or `arcana://welcome?token=XXX`).
- **Expected:** The app launches straight into the onboarding survey (14-question) for a new token, seeded synchronously on the very first composed frame — no flash of the Auth screen and no spurious `Auth` `$screen` event. On Android this is read from the launch `Intent`; on iOS from `onOpenURL`/`onContinueUserActivity` feeding `IosDeepLinkBridge.pendingDeepLink`.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/navigation/DeepLinkHandler.kt, sharedUI/src/androidMain/kotlin/org/arcana/mobile/MainActivity.kt (`extractToken`, `pendingDeepLinkToken`), sharedLogic/src/iosMain/kotlin/org/arcana/mobile/navigation/IosDeepLinkBridge.kt, iosApp/iosApp/iOSApp.swift (`onOpenURL`, `onContinueUserActivity`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`remember(initialWelcomeToken) { session.onDeepLinkToken(...) }`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt
- **Platforms:** shared

### NAV-07 — Deep link, warm start (app already open)
- **Steps:** With the app open and signed out (or mid-Auth), background it and open a welcome link a second time (different or same token).
- **Expected:** The app is brought to foreground and re-routes into the survey/claim flow with the newly-delivered token, without needing a relaunch. Android: `onNewIntent` re-sets `pendingDeepLinkToken` (resetting `deepLinkConsumed`). iOS: `onIosDeepLink` pushes a new value into the shared `pendingDeepLink` StateFlow, observed by the running `AuthFlowRoot` composition.
- **Source:** sharedUI/src/androidMain/kotlin/org/arcana/mobile/MainActivity.kt (`onNewIntent`), sharedLogic/src/iosMain/kotlin/org/arcana/mobile/navigation/IosDeepLinkBridge.kt (`onIosDeepLink`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt (`pending by IosDeepLinkBridge.pendingDeepLink.collectAsState()`)
- **Platforms:** shared

### NAV-08 — Deep link token consumed does not re-surface after logout
- **Steps:** Open a welcome link, complete signup (auth flips on), then later log out.
- **Expected:** Logging out returns to the Auth screen, not back into the signup/survey flow — the consumed token is cleared on the auth flip and Android additionally persists `deepLinkConsumed` across process recreation (`onSaveInstanceState`) so a config change doesn't resurrect the original launch intent's token.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/session/AppSessionController.kt (`onAuthenticated`, `consumeWelcomeToken`), sharedUI/src/androidMain/kotlin/org/arcana/mobile/MainActivity.kt (`STATE_DEEP_LINK_CONSUMED`, `onSaveInstanceState`)
- **Platforms:** shared

### NAV-09 — "Log in instead" escape hatch from the signup flow
- **Steps:** Open a welcome link into the onboarding survey (not the claim-your-name screen — its `Editing` state has no such affordance; only its terminal token-expired/account-exists error states do, see SIGNUP-19/20), then tap the "Already a member? Log in" footer link.
- **Expected:** The pending welcome token is cleared and the app falls back to the standard Auth (login) screen; the platform's pending-link reference is dropped so backgrounding/reopening doesn't re-route into signup.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyScreen.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`onNavigateToLogin = { session.consumeWelcomeToken(); onWelcomeTokenConsumed() }`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt
- **Platforms:** shared

### NAV-10 — Auth flip triggers session teardown / fresh ViewModelStore
- **Steps:** Log in, use the app (populating ViewModels/state), then log out. Log back in as a different (or the same) member.
- **Expected:** All session-scoped ViewModels are destroyed on logout (their `viewModelScope`s cancelled) and freshly recreated on the next login — no stale data (e.g. previous member's favorites/bookings) bleeds into the new session. Android clears a shared `ViewModelStore` in a `LaunchedEffect(isAuthenticated)`; iOS explicitly clears every shell controller's store via `clearSessionViewModelStores()` before Swift rebuilds fresh controllers.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`sessionStore.clear()`, `session.onSessionEnded()`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/IosShellBridge.kt (watcher: `session.onSessionEnded()`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/TabRoots.kt (`ShellSessionStores.clearAll()`), iosApp/iosApp/ArcanaShell.swift (`authChanged`, `clearSessionViewModelStores()`)
- **Platforms:** shared

### NAV-11 — $screen fires once per destination/tab change, no doubles
- **Steps:** Navigate: cold start to Home, tap into ClassDetail, back to Home, switch to Book tab, switch back to Home tab. Watch the debug telemetry echo for `$screen` events.
- **Expected:** Exactly one `$screen` event per real destination change (Home → ClassDetail → Home → Schedule → Home), with no duplicate emission on the very first composition of a tab (iOS specifically guards against double-firing the initial root screen since tab compositions persist across `TabView` switches rather than re-running `LaunchedEffect`s).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`MainScaffold`'s `LaunchedEffect(screenName)`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/TabRoots.kt (`initialRootConsumed`, `emitInitialRootScreen`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/IosShellBridge.kt (`tabRootShown`)
- **Platforms:** shared

### NAV-12 — Android system back on the state-driven screens that aren't NavHost destinations
- **Steps:** On Android, reach each of the following via its normal entry point, then press system back: (a) Password Reset Request (AUTH-06), (b) the onboarding survey or the claim-your-name screen reached via a welcome deep link (SIGNUP-01), (c) Developer Settings opened from the Auth screen (DEVSET-01/AUTH-13), (d) the Auth screen itself.
- **Expected:** None of these are NavHost destinations — they are plain `var`-driven branches, so NAV-05's back-stack behavior does not apply and each screen decides for itself. (a) returns to the Auth screen, matching the screen's own "Back to sign in" affordance, and does not background the app. (b) opens the confirm-to-leave dialog instead of acting immediately (see NAV-13). (c) still backgrounds/exits the app: `showDeveloperSettings` in AuthScreen.kt remains unintercepted, unlike `showPasswordReset` and the survey/claim branches in App.kt, which register a `BackHandler`. (d) still backgrounds the app, correctly — the Auth screen is the root of the signed-out flow, so there is nothing to pop to. Superseded 2026-08-19: this entry previously asserted that `grep -rn BackHandler` returns zero hits and that all four screens exit the app, which described the NAV-13 defect rather than intended behavior, so the 2026-08-11 run scored the bug as a PASS.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`showPasswordReset`, `confirmLeaveSignup`, `BackHandler`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/AuthScreen.kt (`showDeveloperSettings`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsScreen.kt
- **Platforms:** Android-only

### NAV-13 — Signup back asks before discarding what was typed (Android)
- **Steps:** On Android, open a welcome link into the onboarding survey (SIGNUP-01), answer at least one question, then press system back. Tap "Keep going". Press system back again and tap "Leave".
- **Expected:** Back opens an alert titled "Leave signup?" whose body names what that screen loses ("Your answers are not saved yet, so leaving now means starting the survey over." on the survey; "Your details are not saved yet, so leaving now means entering them again." on claim-your-name), with actions "Keep going" and "Leave". "Keep going" (or dismissing the dialog by back/scrim tap) returns to the screen with every answer still selected. "Leave" drops the pending welcome token and falls back to the Auth (sign in) screen, the same exit the screens' own "Log in instead" link takes, so the member stays in the app and re-tapping the email link returns them to the flow. The dialog never appears on iOS: the signed-out flow there is `AuthFlowRoot`, which composes these screens itself and has no system back to intercept.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`confirmLeaveSignup`, `BackHandler`, `session.consumeWelcomeToken()`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/LeaveSignupDialog.kt (`LeaveSignupCopy`, `SignupStep`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/session/AppSessionController.kt (`consumeWelcomeToken`)
- **Platforms:** Android-only

## ERR

Note on scope: a shared `ErrorType` classifier (`sharedLogic/.../networking/ErrorType.kt`)
and a shared `FullScreenError`/`InlineError`/`ErrorSnackbar` UI family
(`sharedUI/.../ui/ErrorState.kt`) now exist and are consumed by Schedule, Class
Detail, Home, My Bookings, Profile, and Booking — see ERR-20 for the full
migration list. `AuthViewModel` and the Signup ViewModels
(`SignupCompletionViewModel`, `SignupSurveyViewModel`) deliberately keep their
own pre-existing CONNECTION/SERVER-equivalent copy (ERR-07 through ERR-10,
ERR-18, ERR-19) — out of scope for this migration by design, not an
inconsistency. This section documents each surface as it actually behaves
today.

### ERR-01 — Schedule cold-start load failure shows full-screen error with RETRY
- **Steps:** With no cached schedule data (fresh app/session), make `ScheduleViewModel`'s initial overview+page-1 fetch fail (e.g. server unreachable or 5xx) and land on the Book tab.
- **Expected:** The tab renders the shared `FullScreenError`, keyed to `ErrorType`, with a small color dot beside the overline (Lime for CONNECTION, Burnt Nectar for SERVER). CONNECTION shows overline "Connection", headline "CAN'T REACH ARCANA.", body "Check your connection and try again."; SERVER shows overline "Server", headline "SOMETHING'S OFF ON OUR END.", body "Give it a moment and try again." A "TRY AGAIN" pill (Moss fill) calls `viewModel.reload()` and re-attempts the fetch. No `"server error"` string literal remains anywhere in the app.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`applyRefetchFailure`, `reload`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt (`FullScreenError`, `ErrorCopy`)
- **Platforms:** shared

### ERR-02 — Schedule refetch failure with content already on screen keeps content, no error takeover
- **Steps:** From a loaded Schedule (Success state with visible classes), trigger a filter/day refetch (change filter, tap a day chip) and have that request fail.
- **Expected:** Unchanged member-visible behavior: `applyRefetchFailure(type: ErrorType)` (now `ErrorType`-typed, was a raw string) does NOT replace the screen with an Error block when `_uiState` is already `Success` — it just clears the `refreshingFilters` dim and re-publishes the existing list; the member keeps seeing their last-good schedule with no error banner or toast. Only a cold-start/error-retry failure (no content yet) produces the full-screen `Error` state. **Silent-success note:** this guarantee now depends on `bodyOrThrow`. Before it, a 5xx with a JSON error body did not throw at all here — `fetchOverview`/`fetchSessionsPage` silently returned an empty-but-valid DTO, and the atomic-apply step would have written that emptiness over the member's real schedule instead of preserving it. `bodyOrThrow` is what makes "keeps content" true for every 5xx shape, not just network-level failures.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`applyRefetchFailure`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt (`bodyOrThrow`)
- **Platforms:** shared

### ERR-03 — Schedule single-day page fetch failure renders an inline error card with retry
- **Steps:** Tap a day chip whose page-1 fetch is not yet cached, and have that specific day's `fetchSessionsPage` call fail while other days/filters are unaffected.
- **Expected:** **Behavior inverted from before this branch.** The day area no longer sits silently on `DotMatrixLoader` forever. `ensureSelectedDayLoaded`'s catch block sets `Success.dayError` to the failure's `ErrorType`, and the day's list area (header/rail/filter chips stay live above it) renders an `InlineError` card with an underlined "Retry" wired to `retryDay()`. `dayError` clears on a real day switch, a filter change, and any refetch that actually (re)loads the day, so it can never outlive the failure it describes. **Silent-success note:** before `bodyOrThrow`, a 5xx with a JSON error body did not even reach this catch block — `fetchSessionsPage` silently returned an empty-but-valid `SchedulePageDto`, so the day was marked loaded with zero sessions and the list showed the unrelated "No classes match your filters for this day." message (SCHED-06) instead of either the old stuck-loading placeholder or today's `InlineError` — a server outage looked identical to a genuinely empty day.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt (`ensureSelectedDayLoaded`, `retryDay`, `dayError`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt (`dayError` branch), sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt (`InlineError`)
- **Platforms:** shared

### ERR-04 — Class detail load failure shows full-screen error with RETRY, close still works
- **Steps:** Open Class Detail for a session whose `GET /api/v1/classes/<id>/` fetch fails (network unreachable, or a non-2xx status).
- **Expected:** `ClassDetailUiState.Error` renders the top bar first (close `X` still functional, independent of whether the fetch succeeded), then the shared `FullScreenError` filling the rest of the screen, keyed to `ErrorType` exactly as ERR-01 (CONNECTION "CAN'T REACH ARCANA." / SERVER "SOMETHING'S OFF ON OUR END.", full copy at ERR-01). "TRY AGAIN" calls `viewModel::reload`. The old "Couldn't load class" copy and the literal `"server error"` string are gone.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailViewModel.kt (`fetch`, `retry`, `retrying`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`ErrorBlock`, `TopBar`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt (`FullScreenError`)
- **Platforms:** shared

### ERR-05 — Home tab load failure shows a full-screen error with retry; a background refresh failure keeps content and shows a dismissible toast
- **Steps:** Land on the Home tab when `HomeViewModel`'s `/memberships/me` + `/bookings/me/` load throws, with no prior content on screen. Separately: from a loaded Home (Success content visible), pull to refresh (or let a resume-triggered refresh run) while the same load throws.
- **Expected:** Cold-load failure (no prior content) renders the shared `FullScreenError` keyed to `ErrorType` (same CONNECTION/SERVER copy as ERR-01) with "TRY AGAIN" wired to `HomeViewModel.retry()`, replacing the whole list rather than appearing inside it. A refresh failure while Success content is already on screen leaves that content untouched and instead raises a dark-Ink `ErrorSnackbar` reading "Couldn't refresh. Showing your last update." with its own Lime "Retry" (dismisses it and re-runs `refresh()`) and a small `X` dismiss control (`HomeViewModel.dismissRefreshFailed()`, clears it without retrying). Per a code comment (not device-confirmed), tab re-entry is not a reliable recovery path on either platform — iOS: an in-tab push/pop re-fires the load, a bare tab switch does not; Android: a tab switch does, via `NavHost`'s `popUpTo`/`restoreState` — which is why `retry()` exists as an explicit, always-reliable control instead.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/home/HomeViewModel.kt (`fetch`, `retry`, `refreshFailed`, `dismissRefreshFailed`, `load`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt (`FullScreenError`, `ErrorSnackbar`)
- **Platforms:** shared

### ERR-06 — My Bookings load failure renders an inline error card with retry
- **Steps:** Open My Bookings (from Home's "See all") when `MyBookingsViewModel`'s fetch fails.
- **Expected:** **Behavior changed — no longer a bare caption.** `MyBookingsUiState.Error` renders the shared `InlineError` card (not full-screen) directly under the "YOUR BOOKINGS" header, keyed to `ErrorType` via the shared inline copy (`ErrorCopy.inline`: "Can't load this right now."/"Check your connection." for CONNECTION, "This didn't load."/"On our end. Try again." for SERVER), with an underlined "Retry" wired to `MyBookingsViewModel.reload()` — a new entry point that mirrors `HomeViewModel.retry()`, delegating to the existing `load()`. No empty-state illustration.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/MyBookingsViewModel.kt (`load`, `reload`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/MyBookingsScreen.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt (`InlineError`)
- **Platforms:** shared

### ERR-07 — Login CONNECTION failure shows a general (non-field) message, form stays editable
- **Steps:** On the Auth screen, submit valid-looking credentials while the login request throws a generic (non-`LoginError`) exception — e.g. the device is offline or the request times out before a response arrives.
- **Expected:** `AuthViewModel` catches the generic `Exception` branch (distinct from the `LoginError` branch below) and sets `AuthUiState.Error("Couldn't reach the server. Check your connection and try again.", isCredentialError = false)`. Because `isCredentialError` is false, `FormBlock` renders the message as a general `BodyText` line below the password field (not attached to any input), and both fields remain editable for a retry via the existing SIGN IN button.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/AuthViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/AuthScreen.kt
- **Platforms:** shared

### ERR-08 — Login SERVER (5xx) failure is distinguished from a connection failure and from bad credentials
- **Steps:** Arm a fault on the local server (`curl -X POST localhost:8000/api/v1/_faults/ -H 'Content-Type: application/json' -d '{"path": "/api/v1/auth/token/", "status": 500}'`), submit login credentials, then `curl -X DELETE localhost:8000/api/v1/_faults/`. See driver-playbook.md "Fault injection".
- **Expected:** The `e.statusCode in 500..599` branch fires distinctly from both the 401 branch and the generic-exception (network) branch, setting `AuthUiState.Error("Something went wrong on our end. Please try again in a moment.", isCredentialError = false)` — copy explicitly different from both the credential-mismatch and the connection-failure strings, still rendered as the general (non-field) message.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/AuthViewModel.kt
- **Platforms:** shared

### ERR-09 — Login credential error (401) renders inline under the password field, not as a general banner
- **Steps:** Submit a wrong email/password pair against a reachable server (401 response).
- **Expected:** `AuthUiState.Error(isCredentialError = true)` with copy "That email and password don't match. Double-check and try again." `FormBlock` routes this message to `ArcanaTextField`'s own `error` slot on the password field (not the general `BodyText` line used by ERR-07/ERR-08) — the same `AuthUiState.Error` shape renders in a visually distinct place depending on `isCredentialError`.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/AuthViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/AuthScreen.kt (`FormBlock`, `error = if (isCredentialError) errorMessage else null`)
- **Platforms:** shared

### ERR-10 — Login failure on an unrecognized non-5xx status code shows the raw status in the general message
- **Steps:** Arm a fault on the local server with a status that is neither 401 nor 500-599 (`curl -X POST localhost:8000/api/v1/_faults/ -H 'Content-Type: application/json' -d '{"path": "/api/v1/auth/token/", "status": 403}'`), submit login credentials, then `curl -X DELETE localhost:8000/api/v1/_faults/`. See driver-playbook.md "Fault injection".
- **Expected:** The `else` branch sets `AuthUiState.Error("Couldn't sign you in (error <code>).")`, rendered as the general (non-field) message per ERR-07.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/AuthViewModel.kt
- **Platforms:** shared

### ERR-11 — Booking submission failure distinguishes connection vs. server vs. a typed server reason, no retry button
- **Steps:** From Class Detail, open the booking sheet and confirm a booking that fails three ways: (a) a typed `BookingError(code)` from the server (e.g. `session_full`, `credits_exhausted`), (b) a network/timeout exception with no HTTP status, (c) a 5xx response with no typed reason code (e.g. an upstream/Django error page, not the API's own JSON error shape).
- **Expected:** **Behavior changed — a network exception no longer collapses into the flat `"booking_failed"` code.** `BookingSubmit.Failed(code)` swaps the sheet's content: heading "Can't book this class", the class name/studio, and `bookingErrorCopy(code)`. (a) A typed server reason code always wins, regardless of transport, and renders its own specific line (e.g. "This class just filled up." for `session_full`). (b) A connection failure (no HTTP response at all) renders `code = "connection_failed"` → "Couldn't reach Arcana. Check your connection and try again." (c) A 5xx response with no typed reason code renders `code = "server_failed"` → "Something went wrong on our end. Try again in a moment." The confirm control is still replaced entirely by a single "GOT IT" button that dismisses the sheet — **unchanged**, no in-place retry was added; the member must re-open the sheet from Class Detail to try again. **Regression note (device QA, fixed 2026-08-16):** case (c) was unreachable until this fix, even though `BookingViewModel`'s side of the mapping was already correct. The gap was one level lower: `ArcanaApiClient.createBooking`'s own non-2xx handling decides whether a response has a parseable reason code at all, and it used to fall back to `BookingError("booking_failed")` for EVERY non-2xx with no parseable code — a genuine 5xx included, since an HTML Django error page (or a bodyless proxy/infra error) isn't the API's `{"error": ...}` JSON shape and so parses to no code. That routed a real server outage through the exact same generic "We couldn't book that. Try again in a moment." fallback as an ordinary unrecognized 4xx — precisely the CONNECTION/SERVER conflation this entry exists to prevent, on the one branch no test had ever actually exercised with a real HTTP response. The decision now lives in `bookingFailureFor(status, parsedCode)` (`networking/BookingFailure.kt`): a parsed reason code still wins outright; otherwise `status >= 500` throws `ApiHttpError` (SERVER); otherwise the generic `booking_failed` code. It is unit-tested directly against real HTTP responses — an HTML error-page body, a valid JSON body missing the `error` key, a 409 carrying `{"error":"session_full"}`, and a bare 4xx — rather than only through a hand-thrown exception in a fake `BookingApi`, which is the gap that let the original defect ship with a green suite.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt (`confirmBooking`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingCopy.kt (`bookingErrorCopy`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt (`createBooking`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/BookingFailure.kt (`bookingFailureFor`, `parsedBookingErrorCode`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/BookingSheet.kt
- **Platforms:** shared

### ERR-12 — Booking cancellation failure shows an inline caption below the button; the button itself is the retry
- **Steps:** From an existing booking's Class Detail (or My Bookings), open the cancel sheet and confirm a cancel that fails (`BookingViewModel.confirmCancel`'s catch-all).
- **Expected:** `CancelState.Failed(code)` — `code` is `"connection_failed"` or `"server_failed"` per `toErrorType()` (`"cancel_failed"` is a back-compat copy branch with no current producer) — keeps the cancel sheet open with the CANCEL BOOKING button re-enabled (no longer showing the "CANCELLING…" spinner state) and adds a burnt-nectar `Caption` via `cancelErrorCopy(code)`: "Couldn't cancel. Try again." for either code today. There is no distinct RETRY control — tapping CANCEL BOOKING again is the retry path.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingCopy.kt (`cancelErrorCopy`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt (`CancelState.Failed` caption)
- **Platforms:** shared

### ERR-13 — Password reset request failure shows a generic connection message, resubmit via the same form
- **Steps:** On the "Forgot password" screen, enter a valid-looking email and submit while the reset-request call throws any exception.
- **Expected:** `PasswordResetSubmit.Failed` renders "Couldn't reach the server. Check your connection and try again." below the email field in `Danger` color; the field and SEND button stay live for another attempt, and editing the email clears the Failed state back to Idle (`updateEmail`'s explicit reset).
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/auth/PasswordResetRequestViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/auth/PasswordResetRequestScreen.kt
- **Platforms:** shared

### ERR-14 — Studio Selection (favorites manager) load failure shows full-screen error with RETRY
- **Steps:** Open the studio/location favorites manager (Profile → Manage, or Schedule's "Manage Favorites") when the initial studios-list fetch fails.
- **Expected:** `StudioSelectionUiState.Error` carries an `ErrorType` and renders the shared `FullScreenError`, so a 5xx reads "Something's off on our end." and a dropped connection reads "Can't reach Arcana." — they are no longer the same fixed "Couldn't load Studios." line. RETRY is wired to the VM's `retry()`, which **does not drop back to Loading**: the error stays on screen and the retry control carries the progress (`retrying`). The sticky Save bar is absent in this state (it renders only for `Ready`), so nothing can cover the retry.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/studios/StudioSelectionViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/studios/StudioSelectionScreen.kt
- **Platforms:** shared

### ERR-15 — Studio Selection save failure shows an inline caption above the sticky CTA, no dedicated retry
- **Steps:** In the Studio Selection screen (loaded successfully), change favorite selections and tap "Save favorites" while the save call fails.
- **Expected:** The `Ready` state's `error` field ("Couldn't save. Try again.") renders as a `Warning`-colored `Caption` directly above the sticky "Save favorites" `PrimaryCta`; the CTA itself stays enabled so re-tapping Save is the retry path (no separate RETRY affordance). **Silent-success note:** `updateFavorites()` returns `FavoritesDto`, whose fields all default to empty. Before `bodyOrThrow`, a 5xx with a JSON error body did not throw here at all — it silently deserialized into an empty `FavoritesDto`, which `FavoritesRepository.save()` wrote straight through as the member's new favorites (wiping any real saved favorites to none) while the screen reported a **successful** save and fired `favorite_removed` telemetry for every favorite that "vanished." `bodyOrThrow` closes this: a 5xx now reaches this error path instead of silently wiping and reporting success.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/studios/StudioSelectionViewModel.kt (`save`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/favorites/FavoritesRepository.kt (`save`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt (`updateFavorites`, `bodyOrThrow`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/studios/StudioSelectionScreen.kt
- **Platforms:** shared

### ERR-16 — Edit Profile load failure shows a dedicated error state with RETRY and a close affordance
- **Steps:** Open Edit Profile from the Profile tab when the initial member-data load throws.
- **Expected:** `EditProfileViewModel.State.LoadError` carries an `ErrorType` and renders the shared `FullScreenError` with the Close (X) `IconCircle` overlaid on top of it (the same shape Class Detail uses, ERR-04), so a 5xx reads "Something's off on our end." and a dropped connection reads "Can't reach Arcana." The old fixed "Couldn't\nload." headline and the "Couldn't load your profile. Pull to retry." body are gone, along with the pull gesture that copy named and this screen never had. Retry is wired to `viewModel::load`, which **does not drop back to Loading** from an error, and carries progress via `retrying`. **Silent-success note:** `fetchProfile()` returns `MeProfileDto`, whose fields all default to `""`/`0`/`null`. Before `bodyOrThrow`, a 5xx with a JSON error body did not throw here at all — it silently deserialized into an empty `MeProfileDto` and rendered `Editing` with every field blank, indistinguishable from a genuinely empty profile, with Save reachable from that blank state. `bodyOrThrow` closes this: a 5xx now reaches `LoadError`.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileViewModel.kt (`load`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt (`fetchProfile`, `bodyOrThrow`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileScreen.kt (`LoadErrorState`)
- **Platforms:** shared

### ERR-17 — Edit Profile save failure shows a form-level banner naming a connection problem, resubmit via SAVE
- **Steps:** In Edit Profile with the form loaded, change a field and tap Save while the save call throws.
- **Expected:** `EditProfileViewModel`'s catch-all sets `formError = "Couldn't save your changes. Check your connection and try again."`; the screen renders a `FormErrorBanner` above the fields, `isSaving` resets to false so the Save CTA is tappable again, and any field edit clears `formError` (per `setEditing(...formError = null)` in the field-mutation helpers). This screen was NOT migrated to `ErrorType` on this branch — the banner text is the same fixed string regardless of CONNECTION vs SERVER. **Silent-success note:** `updateProfile()` (the save PATCH) also returns `MeProfileDto`, fully defaulted. Before `bodyOrThrow`, a 5xx with a JSON error body did not throw here either — it silently deserialized into an empty `MeProfileDto` (discarded; the call site only checks that the call didn't throw), so `_state.value = State.Saved` fired and the screen popped back to Profile reporting success on a save that **never persisted**. This is the exact "successful-looking Edit Profile save" bug this branch's design doc names. `bodyOrThrow` is what makes this entry's `FormErrorBanner` reachable at all for a JSON 5xx body — previously it could not fire for that failure shape.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileViewModel.kt (`save`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt (`updateProfile`, `bodyOrThrow`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileScreen.kt (`FormErrorBanner`)
- **Platforms:** shared

### ERR-18 — Claim-your-name (signup completion) form distinguishes CONNECTION vs SERVER vs generic submit failure
- **Steps:** Reach the claim-your-name form via `accounts.<device>.claim_spare.welcome_token` (the SIGNUP block consumes `claim`, so use the spare). Submit a complete/valid form three times, forcing: (a) a network-level failure by killing the dev server (`CompleteSignupResult.NetworkError`), (b) a 5xx, (c) a non-5xx, non-field-specific error. For (b) and (c) arm a fault on `/api/v1/auth/complete-signup` with `times` set so the form stays reachable: see driver-playbook.md "Fault injection".
- **Expected:** Each maps to a distinct `formError` string rendered via `FormErrorBanner`: (a) `NETWORK_MESSAGE` = "Couldn't reach the server. Check your connection and try again.", (b) `SERVER_MESSAGE` = "Something went wrong on our end. Please try again in a moment.", (c) `GENERIC_MESSAGE` = "We couldn't complete your signup. Please review your details and try again." In all three the form stays populated (`Editing` state, not a terminal `Error` state) and the SUBMIT control is re-enabled for another attempt — this is separate from field-level `passwordError`/`phoneError`, and separate from the terminal `SignupCompletionState.Error` used only for token-expired/already-has-account (see SIGNUP area for those).
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupCompletionScreen.kt (`FormErrorBanner`)
- **Platforms:** shared

### ERR-19 — Onboarding survey submit failure distinguishes CONNECTION vs SERVER and reveals a "Continue anyway" escape after the first failure
- **Steps:** Reach the survey via `accounts.<device>.claim_spare.welcome_token` (the SIGNUP block consumes `claim`). Answer all required questions and submit while the submit call fails: first force a network error by killing the dev server, then on a second attempt arm a 5xx on `/api/v1/beta/signup-survey` (driver-playbook.md "Fault injection").
- **Expected:** Each failure sets a distinct `submitError` (`NETWORK_MESSAGE` vs `SERVER_MESSAGE`, same strings as ERR-18) rendered via `SubmitErrorBanner`, and increments `failedAttempts`; all previously-entered answers are preserved (no data loss on failure). Once `failedAttempts >= 1` and not currently submitting, a "Continue anyway" `TextLink` appears that calls `continueAnyway()` — completing the signup flow without a successful survey submit, honoring the "survey must never block a paid member's signup" rule.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyViewModel.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/signup/SignupSurveyScreen.kt
- **Platforms:** shared

### ERR-20 — A shared CONNECTION/SERVER classifier now exists; hand-rolled per-surface catch blocks are gone
- **Steps:** Search the codebase for a shared error-classification type (e.g. `ErrorType`) referenced by more than one ViewModel; separately, search for the literal string `"server error"`.
- **Expected:** **This entry was true on main and becomes false the moment this branch merges — the clearest illustration of why the inventory-update rule exists.** A shared classifier exists at `networking/ErrorType.kt`: `enum class ErrorType { CONNECTION, SERVER }` plus `fun Throwable.toErrorType(): ErrorType`, defined in terms of the pre-existing `apiRequestOutcome(statusCode)` (a guard test locks the two in agreement, so the UI category and the `api_request` telemetry outcome can never disagree). It is consumed by `HomeViewModel`, `ScheduleViewModel`, `ClassDetailViewModel`, `MyBookingsViewModel`, `ProfileViewModel`, and `BookingViewModel`. The matching shared UI family (`FullScreenError`, `InlineError`, `ErrorSnackbar`, `RetryButton`, `RetryLink`, `ErrorCopy`) lives at `sharedUI/.../ui/ErrorState.kt`. No `"server error"` string literal remains anywhere in either module (the only hits left are inside comments explaining the rule). `AuthViewModel` and the Signup ViewModels (`SignupCompletionViewModel`, `SignupSurveyViewModel`) deliberately keep their own pre-existing CONNECTION/SERVER-equivalent copy (see ERR-07 through ERR-10, ERR-18, ERR-19) — out of scope for this migration by design, not an oversight.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ErrorType.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/home/HomeViewModel.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailViewModel.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/MyBookingsViewModel.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileViewModel.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt
- **Platforms:** shared

### ERR-21 — Home refresh-failed toast on a failed background refresh
- **Steps:** Load Home successfully (Success content visible). Force a refresh to fail — `./tools/regression/error-state-harness.sh db-down` (SERVER path) or `kill-server` (CONNECTION path) — then pull to refresh. Separately, tap the toast's dismiss `X` without retrying; separately again, restore the server (`db-up`/`start`) and tap the toast's "Retry".
- **Expected:** The existing content stays on screen unchanged (no error takeover, no shimmer). A dark-Ink `ErrorSnackbar` appears pinned to the bottom, reading "Couldn't refresh. Showing your last update." with a Lime "Retry" link and a small `X` dismiss control (content description "Dismiss notice"). Tapping the dismiss `X` (`HomeViewModel.dismissRefreshFailed()`) clears the toast without retrying and without disturbing the Success content. Tapping "Retry" dismisses the toast and immediately calls `refresh()` again; once the server is reachable this both clears the toast and updates the content. The toast never latches — any later successful `fetch()` (pull-to-refresh or otherwise) also clears it on its own.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/home/HomeViewModel.kt (`refreshFailed`, `dismissRefreshFailed`, `fetch`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt (`ErrorSnackbar`)
- **Platforms:** shared

### ERR-22 — Client request timeout: a stalled server fails instead of hanging forever
- **Steps:** With the app on any loading screen making a live request (e.g. cold-launch onto Home or Schedule), stall the local dev server so bytes stop flowing but the TCP connection stays open: `./tools/regression/error-state-harness.sh stall` (SIGSTOPs `manage.py runserver`; raw equivalent is `kill -STOP` on its PIDs). Restore with `./tools/regression/error-state-harness.sh unstall` (SIGCONT) and confirm the surface recovers.
- **Expected:** The request does not hang indefinitely. `HttpTimeout` is installed once on `ArcanaApiClient`'s `HttpClient` and applies to every request, including bookings, with no per-endpoint override: `connectTimeoutMillis = 10_000`, `socketTimeoutMillis = 30_000`, `requestTimeoutMillis = 60_000`. (The previous `createBooking`/`cancelBooking` override to 60s/90s is gone — safe because this blanket 60s still leaves ~9x headroom over the slowest booking observed server-side, ~6.5s.) A stalled socket throws within this window instead of hanging forever (unbounded before this timeout existed — 89s seen in prod), classifies as `ErrorType.CONNECTION` (the throw carries no HTTP status), and the surface reaches its normal CONNECTION state (`FullScreenError`/`InlineError`/`ErrorSnackbar`, depending on surface and prior content). **Both platforms fail at ~30s** — `socketTimeoutMillis` is the effective bound on each. Android (the `ktor-client-android` engine) device-confirmed at `api_request total_ms=30010`; iOS (Darwin) measured at 30.6s through the real `ArcanaApiClient`, throwing `SocketTimeoutException`. Ktor 3.1.2's Darwin engine DOES implement `socketTimeoutMillis` (it sets `NSMutableURLRequest.timeoutInterval`), so no engine-level session config is needed. An iOS wait near 60s is NOT expected and means something has regressed — do not wave it through as a platform quirk. Restoring the server and retrying succeeds normally on both platforms.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt (`install(HttpTimeout)`), tools/regression/error-state-harness.sh (`stall`, `unstall`)
- **Platforms:** shared

## TEL

### TEL-01 — $screen Home (tab root)
- **Steps:** Log in (or resume an authenticated session) and land on the Home tab.
- **Expected:** A `$screen` event with name `Home` fires exactly once for this composition of the destination (debug console shows `▶ $screen Home`); it does not re-fire on unrelated recompositions of the same destination.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`currentScreenName`, `MainScaffold`'s `LaunchedEffect(screenName)`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt (`Screens.HOME`, `screen()`)
- **Platforms:** Android-only (Android's single NavHost path; see TEL-11/TEL-12 for the iOS bridge-driven equivalent)

### TEL-02 — $screen Schedule (tab root)
- **Steps:** From Home, tap the Book tab (middle, labelled BOOK).
- **Expected:** A `$screen` event with name `Schedule` fires on arrival at the Schedule destination.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`currentScreenName`, `MainScaffold`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt (`Screens.SCHEDULE`)
- **Platforms:** Android-only

### TEL-03 — $screen Profile (tab root)
- **Steps:** From Home, tap the Profile tab.
- **Expected:** A `$screen` event with name `Profile` fires on arrival at the Profile destination.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`currentScreenName`, `MainScaffold`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt (`Screens.PROFILE`)
- **Platforms:** Android-only

### TEL-04 — $screen StudioSelection
- **Steps:** From Profile, tap "Manage" on Your Favorites (or from Schedule's favorites dropdown) to open Studio Selection.
- **Expected:** A `$screen` event with name `StudioSelection` fires once on navigating to that non-tab destination.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`currentScreenName` → `Screens.STUDIO_SELECTION`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt
- **Platforms:** Android-only (iOS reaches the same destination via `ProfileTabViewController` in TabRoots.kt, whose shared `TabRoot` composable emits `$screen` the same way — see TEL-11)

### TEL-05 — $screen MyBookings
- **Steps:** From Home, tap "See all" on upcoming reservations to open My Bookings.
- **Expected:** A `$screen` event with name `MyBookings` fires once on navigating to that destination.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`currentScreenName` → `Screens.MY_BOOKINGS`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt
- **Platforms:** Android-only

### TEL-06 — $screen ConciergeRequest
- **Steps:** From Profile, open the concierge/support request screen.
- **Expected:** A `$screen` event with name `ConciergeRequest` fires once on navigating to that destination.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`currentScreenName` → `Screens.CONCIERGE_REQUEST`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt
- **Platforms:** Android-only

### TEL-07 — $screen ClassDetail
- **Steps:** From Schedule (or Home's upcoming list), tap a class row to open Class Detail.
- **Expected:** A `$screen` event with name `ClassDetail` fires once per distinct class-detail visit; the event carries a stable screen name even though the destination itself carries a session id argument (name resolution ignores the id).
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`currentScreenName` → `Screens.CLASS_DETAIL`, comment on `screenName` keying), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt
- **Platforms:** Android-only

### TEL-08 — $screen Auth (login screen)
- **Steps:** Cold start signed out (or log out), landing on the login screen.
- **Expected:** A `$screen` event with name `Auth` fires when the login screen composes.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`LaunchedEffect(Unit) { telemetry.screen(Telemetry.Screens.AUTH) }`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt (same call), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt
- **Platforms:** shared

### TEL-09 — $screen PasswordResetRequest
- **Steps:** From the login screen, tap "Forgot password" to open the reset-request screen.
- **Expected:** A `$screen` event with name `PasswordResetRequest` fires once on entering that screen.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`LaunchedEffect(Unit) { telemetry.screen(Telemetry.Screens.PASSWORD_RESET_REQUEST) }`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt (same call), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt
- **Platforms:** shared

### TEL-10 — $screen SignupCompletion (claim-your-name)
- **Steps:** Open a welcome deep link whose onboarding survey is already marked done (or complete the survey), reaching the claim-your-name screen.
- **Expected:** A `$screen` event with name `SignupCompletion` fires once on entering the claim screen.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`LaunchedEffect(Unit) { telemetry.screen(Telemetry.Screens.SIGNUP) }`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt (same call), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt
- **Platforms:** shared

### TEL-11 — $screen SignupSurvey
- **Steps:** Open a fresh welcome deep link (survey not yet completed for that token), reaching the 14-question onboarding survey.
- **Expected:** A `$screen` event with name `SignupSurvey` fires once on entering the survey.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`LaunchedEffect(Unit) { telemetry.screen(Telemetry.Screens.SIGNUP_SURVEY) }`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt (same call), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt
- **Platforms:** shared

### TEL-12 — $screen on iOS tab-root switch (bridge-driven)
- **Steps:** On iOS, from the Home tab, tap the Book tab in the native SwiftUI tab bar, then tap Profile, then tap back to Book (a re-visit of an already-composed tab).
- **Expected:** Each real tab switch (including the re-visit) fires exactly one `$screen` event named for the destination tab (`Schedule`, `Profile`, `Schedule` again) via `IosShellBridge.tabRootShown`, even though the tab's Compose content persists across switches and its own `LaunchedEffect` does not re-run. Same-tab re-taps (tapping the currently active tab) fire no `$screen`, only `tab_tapped` (see TEL-14).
- **Source:** sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/IosShellBridge.kt (`tabRootShown`, `tabScreenName`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/TabRoots.kt (`emitInitialRootScreen` skip logic in `TabRoot`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt
- **Platforms:** iOS-only

### TEL-13 — $screen Home on iOS cold start (composition-driven root)
- **Steps:** On iOS, cold-start authenticated so the Home tab composes for the first time.
- **Expected:** Home emits its own initial-root `$screen` (`Home`) from composition (unlike Schedule/Profile, which rely on the bridge switch event for their first visit) so cold start still reports exactly one `Home` $screen with no double.
- **Source:** sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/TabRoots.kt (`TabRoot`'s `emitInitialRootScreen` parameter and Home's call site), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt
- **Platforms:** iOS-only

### TEL-14 — tab_tapped event (Android bottom bar)
- **Steps:** On Android, from any tab root, tap a different tab in `ArcanaTabBar`.
- **Expected:** A `tab_tapped` event fires with `tab` = the destination tab name (lowercased: `home`/`schedule`/`profile`) and `from_screen` = the canonical screen name of the tab being left. **The middle tab still reports `schedule`, not `book`**, after the 2026-08-25 label rename: the payload is `tab.name.lowercase()`, the enum CONSTANT, not the visible label. `$screen` likewise stays `Schedule`. This is intentional so dashboards survive the rename; `Profile`/`You` is the same pattern.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`ArcanaTabBar(onSelect = { telemetry.tabTapped(...) })`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt (`tabTapped`, `Events.TAB_TAPPED`)
- **Platforms:** Android-only

### TEL-15 — tab_tapped event (iOS native tab bar)
- **Steps:** On iOS, tap any tab in the native SwiftUI `TabView` (including a same-tab re-tap on the already-active tab).
- **Expected:** A `tab_tapped` event fires with `tab` = the destination tab and `from_screen` = the canonical screen name of the previously active tab, mirroring the Android bar's semantics even though the bar itself is Swift, not Compose.
- **Source:** sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/IosShellBridge.kt (`tabSelected`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt (`tabTapped`)
- **Platforms:** iOS-only

### TEL-16 — identify on first successful /me
- **Steps:** Log in (or complete signup) as a member who has not yet been identified this session, reaching a screen that loads `ProfileViewModel` (Home's avatar-initials load, or the Profile tab itself).
- **Expected:** PostHog `identify` fires exactly once per session with the member's id, email, and display name; the debug console echoes `▶ identify <memberId>` with no PII in the log line. A second `ProfileViewModel` instance (e.g. the nav-bar avatar VM vs the Profile screen's own VM) loading `/me` again does NOT re-fire identify for the same member id.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/profile/ProfileViewModel.kt (`telemetry.identify(...)` call site), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt (`identify`, `lastIdentifiedId` dedup)
- **Platforms:** shared

### TEL-17 — telemetry reset on logout/forced logout
- **Steps:** From Profile, tap Log Out; separately, trigger a forced logout (e.g. an unrecoverable refresh failure).
- **Expected:** `Telemetry.reset()` runs on both the manual and forced logout paths, clearing the `lastIdentifiedId` dedup guard (so a subsequent login by a different or the same member re-fires `identify`) and clearing PostHog/Sentry user context; debug console echoes `▶ reset`.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt (two `telemetry.reset()` call sites — `logout()` and `forceLogout(cause)`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt (`reset`)
- **Platforms:** shared

### TEL-18 — app_start_completed, authenticated cold start
- **Steps:** Cold-start the app while already logged in (session token present), letting it land on Home.
- **Expected:** `app_start_completed` fires exactly once per process with `start_type=cold`, `authenticated=true`, and a `duration_ms` measured from the platform entry point (`markStart()`) to Home's first composed content (`onFirstContent`).
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/AppStartTracker.kt (`markStart`, `onFirstContent`, `fired` guard), sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`LaunchedEffect(Unit) { AppStartTracker.onFirstContent(telemetry, authenticated = true) }` under `if (isAuthenticated)`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/TabRoots.kt (`firstContent` branch calling `AppStartTracker.onFirstContent(telemetry, authenticated = true)`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt (`appStartCompleted`)
- **Platforms:** shared

### TEL-19 — app_start_completed, unauthenticated cold start
- **Steps:** Cold-start the app signed out, landing on the login screen.
- **Expected:** `app_start_completed` fires exactly once per process with `start_type=cold` and `authenticated=false` when the Auth screen renders its first content.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`LaunchedEffect(Unit) { AppStartTracker.onFirstContent(telemetry, authenticated = false) }` in the unauthenticated/AuthScreen branch), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/AuthFlowRoot.kt (line 143, same call), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/AppStartTracker.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt (`appStartCompleted`)
- **Platforms:** shared

### TEL-20 — Debug-build console echo of every telemetry call
- **Steps:** Run a Debug build on either platform and drive any telemetry-firing surface (e.g. tap through Home → Schedule → a class → back).
- **Expected:** Every `Telemetry` call (screen, event, identify, reset, error) is echoed to the platform console under a `▶ Telemetry` / `D/Telemetry` tag; release builds emit no such echo. In a Debug build the echo is the ONLY destination, since PostHog is not initialized there at all (TEL-22) — so this echo, not the dashboard, is how a run verifies events fired.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt (`debugLog`, `isDebugBuild` gate, `LOG_TAG`), sharedUI/src/androidMain/kotlin/org/arcana/mobile/analytics/AndroidTelemetry.kt
- **Platforms:** shared

### TEL-21 — Edit Profile fires $screen; Developer Settings deliberately does not
- **Steps:** From Profile, open Edit Profile (PROFILE-11) and watch the debug telemetry echo (TEL-20) for a `$screen` event, then close it and watch again. Separately open Developer Settings (DEVSET-01/AUTH-13) and watch.
- **Expected:** Opening Edit Profile emits `$screen EditProfile`, and closing it back to Profile re-emits `$screen Profile` (the name genuinely changed, same as returning from ClassDetail). Developer Settings emits nothing, on purpose: it is not a NavHost destination at all (a plain `var`, see NAV-12), it is a dev-only screen with no visible entry point, and it was deliberately left out of scope. The tab bar stays hidden on Edit Profile throughout (NAV-04): the iOS shell's at-root test compares `currentScreenName` against Home/Schedule/Profile only, so naming the screen does not reveal the bar.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`currentScreenName`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt (`Screens.EDIT_PROFILE`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/profile/EditProfileScreen.kt, sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/TabRoots.kt (`atRoot`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/settings/DeveloperSettingsScreen.kt
- **Platforms:** shared

### TEL-22 — PostHog initializes only in a release build talking to prod
- **Scope note (added after run 2026-08-27):** only the **negative legs are in scope for this suite**, and they are the ones that matter — they prove dev traffic never reaches the production project. The positive control (leg d) requires a release build pointed at `https://api.arcana.fit`, which the suite's safety rules prohibit outright, so it is **permanently out of scope here** and is verified manually at release time instead (see the `arcana-mobile-release` skill). Do not record this entry BLOCKED for the missing leg d: drive the negative legs and PASS on them, noting leg d as out of scope. The earlier "structurally unrunnable, retire it" reading was wrong — it discarded the half of the entry that actually protects production analytics.
- **Steps:** From a cold start after setting the base URL in Developer Settings (DEVSET-01/AUTH-13) and force-stopping the app, drive **(b) Debug build on the run's local URL** and, if a release build is available to the run, **(c) release build on the run's local URL**. Watch for outbound requests to the PostHog host, and watch the debug console echo (TEL-20) in parallel. Legs (a) Debug-on-prod and (d) release-on-prod are out of scope — both point the app at production.
- **Expected:** Legs (b) and (c) send **nothing at all** to PostHog: the SDK is never initialized and `NoopAnalytics` is bound, because `TelemetryGate.shouldReportAnalytics` requires `!isDebugBuild && environment == prod` and the environment is resolved from the persisted override BEFORE Koin starts. The Debug-build console echo still fires throughout, so events remain observable while developing — on a Debug build the first echoed line is literally `PostHog DISABLED (environment=local). Console echo is unaffected.`, which is the single cheapest confirmation of this entry. Session replay follows PostHog and is likewise absent. (For reference, the out-of-scope leg d is what contacts PostHog: a `/array/<key>/config` fetch, then `/flags` and batched `/batch` posts.)
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/TelemetryGate.kt, sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/AppEnvironment.kt (`classifyEnvironment`), sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/BaseUrlProvider.kt (`storedUrl`), sharedUI/src/androidMain/kotlin/org/arcana/mobile/analytics/AndroidTelemetry.kt, iosApp/iosApp/Analytics/TelemetryBootstrap.swift
- **Platforms:** shared

### TEL-23 — Sentry reports from every build, tagged with the environment
- **Scope note (added after run 2026-08-27):** the **release-build-against-prod leg is permanently out of scope** for this suite (it requires pointing the app at `https://api.arcana.fit`), and confirming a report actually *arrived* needs the Sentry dashboard, which a driving shift has no access to. Drive the local-debug leg only and PASS on what it can show; record the prod leg as out of scope, **not** BLOCKED. Verified at release time instead, alongside TEL-22's leg d.
- **Steps:** Run a Debug build against the run's local server and trigger a handled error (e.g. a failing request on Home, easiest via a fault injected on that endpoint). Confirm from the debug console that Sentry initialized and with which environment string.
- **Expected:** Sentry **is** initialized on a Debug/local build — unlike PostHog it is deliberately never gated, so dev and regression-run issues are still captured. The report carries the SDK's native `environment` from the same classifier, `local-debug` here (`sentryEnvironment` appends `-debug` for a debug build); a release build against prod would carry `prod`, which is what new-issue alert rules scope on. The contrast with TEL-22 is the point of this entry: PostHog gated off, Sentry deliberately left on.
- **Source:** sharedLogic/src/commonMain/kotlin/org/arcana/mobile/analytics/TelemetryGate.kt (`sentryEnvironment`), sharedUI/src/androidMain/kotlin/org/arcana/mobile/analytics/AndroidTelemetry.kt (`options.environment`), iosApp/iosApp/Analytics/TelemetryBootstrap.swift (`options.environment`)
- **Platforms:** shared

## PLAT

### PLAT-01 — iOS 26 Liquid Glass tab bar
- **Steps:** On the iOS 26 simulator (this suite is simulator/emulator only — never a physical device), sign in and observe the bottom tab bar over scrolling content.
- **Expected:** The tab bar renders the system Liquid Glass material (translucent, blurred, drawing its own backdrop) — the app does not override `standardAppearance`/`scrollEdgeAppearance` on iOS 26, leaving system defaults untouched.
- **Source:** iosApp/iosApp/ArcanaShell.swift (`if #unavailable(iOS 26.0) { ... }` guard — code inside only runs on iOS 18.x, so 26 gets the untouched system appearance)
- **Platforms:** iOS26-only

### PLAT-02 — iOS 18 pinned opaque tab bar
- **Steps:** On the iOS 18.5 simulator (no Liquid Glass; simulator only, never a physical device), sign in and observe the bottom tab bar over scrolling content.
- **Expected:** The tab bar shows a solid/blurred default background behind the items — not a transparent bar with items floating directly over content. This is because `ShellModel.init` explicitly pins both `standardAppearance` and `scrollEdgeAppearance` to `configureWithDefaultBackground()` on iOS 18.x, working around UIKit's inability to detect Compose content as a scrolling `UIScrollView`.
- **Source:** iosApp/iosApp/ArcanaShell.swift (`ShellModel.init`, `#unavailable(iOS 26.0)`)
- **Platforms:** iOS18-only

### PLAT-03 — You-tab avatar-initials chip (iOS)
- **Steps:** Sign in on iOS and look at the third tab bar item ("You").
- **Expected:** Once the member's `/me` fetch resolves, the tab icon shows a Moss-filled circle with the member's initials in Stone (not a generic person silhouette), rendered with `.alwaysOriginal` so the system doesn't template-tint it into a monochrome silhouette. Before the fetch resolves or on fetch failure, it falls back to the generic `person.crop.circle` SF Symbol.
- **Source:** iosApp/iosApp/ArcanaShell.swift (`AvatarChip`, `shell.memberInitials`), sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/IosShellBridge.kt (`observeMemberInitials`)
- **Platforms:** iOS-only

### PLAT-04 — Profile-tab avatar-initials chip (Android)
- **Steps:** Sign in on Android and look at the third tab bar item ("You").
- **Expected:** The tab shows a circular avatar with the member's initials (Moss/Lime-bordered when active, Mist2 when inactive) sourced from the same `ProfileViewModel` load that backs the Profile screen — not a static placeholder.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/TabBar.kt (`TabItem`, `isAvatar` branch), sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`avatarInitials`, `profileVm.load()`)
- **Platforms:** Android-only

### PLAT-05 — iOS locked to light appearance
- **Steps:** On iOS, set the system to Dark Mode, then open the app.
- **Expected:** The app renders in its Stone-light design regardless of system appearance — it does not switch to a dark theme (no dark theme exists; Moss-on-dark-glass would fail contrast).
- **Source:** iosApp/iosApp/ArcanaShell.swift (`moss` tint comment re: light-only design), iosApp/iosApp/Info.plist (`UIUserInterfaceStyle` per CLAUDE.md)
- **Platforms:** iOS-only

### PLAT-06 — iOS Home tab warm-loads Profile for early PostHog identify
- **Steps:** Cold-launch the app authenticated, land on Home, without ever visiting the Profile tab, and watch the debug telemetry echo.
- **Expected:** A PostHog `identify` call fires at session start (from the first `/me` load), not deferred until the member actually opens the Profile tab — the Home tab root eagerly resolves `ProfileViewModel` and calls `load()`.
- **Source:** sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/TabRoots.kt (`TabRoot`'s `firstContent` branch: `koinViewModel<ProfileViewModel>()`, `profileVm.load()`)
- **Platforms:** iOS-only

### PLAT-07 — Splash rendered as its own Compose controller, overlaying both auth and shell (iOS)
- **Steps:** Cold-launch iOS while authenticated and while unauthenticated; watch what the splash sits on top of during its display window.
- **Expected:** The splash overlays whichever content is underneath (TabView or the Auth flow) as a z-stacked `ComposeUIViewController`, matching Android's z-stacked `AnimatedVisibility` overlay in App.kt, and releases (`splashVC = nil`) only after its fade completes so it is never shown twice.
- **Source:** sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/SplashHost.kt, iosApp/iosApp/ArcanaShell.swift (`ArcanaShellView`, `ZStack`, `splashDidAppear`)
- **Platforms:** iOS-only

### PLAT-08 — Content flows edge-to-edge under the floating iOS glass bar
- **Steps:** On iOS, scroll each tab root (Home, Schedule, You) to its last item.
- **Expected:** The last row/card scrolls fully clear of the floating tab bar rather than being obscured by it — each tab-root scrollable adds `LocalFloatingBarInset` as bottom content padding (0 on Android, where the tab bar is a docked Scaffold `bottomBar` and content is padded via `innerPadding` instead).
- **Source:** sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/TabRoots.kt (`barInset`, `LocalFloatingBarInset`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/FloatingBarInset.kt
- **Platforms:** iOS-only

### PLAT-09 — Android edge-to-edge with docked tab bar
- **Steps:** On Android, scroll each tab root to its last item, and check the tab bar's bottom edge against the gesture-nav inset.
- **Expected:** The tab bar's Stone background fills its entire slot including the gesture-navigation safe-area inset (no transparent gap showing content behind it), and screen content is padded by the Scaffold's `innerPadding` so nothing sits under the docked bar.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt (`Scaffold(bottomBar = ...)`, `innerPadding`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/TabBar.kt (`safeBottomBarPadding()`)
- **Platforms:** Android-only

### PLAT-10 — Icon-only controls are tappable to 48dp regardless of visual size
- **Steps:** Tap an `IconCircle` control near its edge and just outside it — the Profile settings gear, a modal close (X), the Concierge close. Android drivers can measure it: `adb shell uiautomator dump` reports the clickable node's bounds, and a tap probe at increasing offsets from centre finds the real boundary.
- **Expected:** Every tappable icon well hits to at least 48x48dp even where the circle is drawn smaller (`diameter` 32-40 at most call sites). Compose expands any pointer-input node to `ViewConfiguration.minimumTouchTargetSize`, whose interface default is 48dp and which neither the Android nor the skiko/UIKit implementation overrides, so this holds on both platforms. Measured on the Pixel_9_Pro AVD 2026-08-24: a `diameter = 36` well registered taps out to a 24dp radius (48dp target) and missed at 26dp. **`diameter` is a visual property, not a touch-target one** — do not raise it to improve reach, and do not file a bug from a screenshot showing a small circle. The expansion is distributed evenly around the layout and cannot overlap a neighbouring pointer node, so densely packed icons are the only case worth checking by hand.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/Buttons.kt (`IconCircle`)
- **Platforms:** shared

### PLAT-11 — All-caps labels are optically centred, by two different corrections
- **Steps:** Screenshot any all-caps label and measure its ink against the thing it is centred in. For a filled control (`PrimaryCta`, `RetryButton`): `tools/regression/measure_centering.py <shot.png> <fill_hex> 3 <x0 y0 x1 y1>`, crop box required. For a `TextLink`, that tool does not apply (it locates the control by a solid fill and a TextLink has none) — compare the label's cap ink centre against its trailing arrow's ink centre instead.
- **Expected:** Under 0.5pt on both axes. League Spartan's capitals sit ~0.0914em high in the em box, so a label centred by `CenterVertically` alone lands visibly high; `ui/OpticalCentering.kt` corrects it. **The two helpers are not interchangeable and the choice is not cosmetic.** `opticallyCentredCaps(fontSize, letterSpacingEm)` also nudges right, cancelling the trailing letter-space that a CENTRED label splits evenly — correct inside a filled control. `opticallyCentredCapsVertical(fontSize)` omits that, for a label sitting BESIDE a sibling glyph: a start-aligned label never splits the trailing space, so the horizontal nudge corrects nothing and instead shifts the label out of its gutter. Measured on the Pixel_9_Pro AVD 2026-08-25: `TextLink` was 4.0px (1.33pt) high at every call site and is 0.00pt after the vertical nudge, underlined and not; applying the full two-axis helper instead moved Home's "See all" from x=73 to x=76 against a 74px gutter it shares with the rows above it. Each component passes its type size to the helper from the same constant its `TextStyle` uses, so the two cannot drift.
- **Source:** sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/OpticalCentering.kt, sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/Buttons.kt (`TextLink`, `PrimaryCta`), sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt (`RetryButton`)
- **Platforms:** shared
