#!/usr/bin/env bash
#
# Error-state QA harness.
#
# Forces each failure mode the error-states work covers, against the LOCAL dev
# server only. Every recipe here is drawn from techniques that actually worked
# in the 2026-08-11 full-regression run; the hazards below are documented
# incidents, not preferences.
#
#   ./tools/regression/error-state-harness.sh <command>
#
# Run `… help` for the command list, `… preflight` before a QA session.
#
# ─────────────────────────────────────────────────────────────────────────────
# THREE HAZARDS THIS SCRIPT EXISTS TO PREVENT
#
# 1. PAGING THE FOUNDERS. arcana-server/.env points OPS_NOTIFIER_CLASS at the
#    real MultiOpsNotifier (Telegram + Pushover) and EMAIL_SENDER_CLASS at the
#    real LoopsEmailSender. PushoverOpsNotifier.notify_ops sends at hardcoded
#    EMERGENCY priority regardless of the caller's `urgent` flag: DND-breaking,
#    retried for up to 3 hours. Every driven booking and cancel would page Cole
#    and Felicia for real, and send live member email. `start` overrides both.
#
# 2. KILLING THE ANDROID EMULATOR. `lsof -ti :8000 | xargs kill` also matches
#    processes merely CONNECTED to that port. In the 2026-08-11 run it killed
#    the running emulator outright, costing a full AVD relaunch, quick-boot
#    recovery and re-sign-in. `kill-server` matches on the command line only.
#
# 3. SILENTLY TESTING AGAINST PRODUCTION. defaultBaseUrl() is
#    https://api.arcana.fit on BOTH platforms, including debug builds. Any
#    storage-clearing step (`pm clear`, Erase All Content and Settings,
#    deleting arcana_secure_prefs.xml) resets the Developer Settings override
#    back to prod with ZERO visible indication — a login failure against prod
#    looks identical to a bad password. `preflight` makes you re-confirm.
# ─────────────────────────────────────────────────────────────────────────────

set -uo pipefail

SERVER_DIR="/Users/coletomlinson/Desktop/arcana/arcana-server"
PG_CONTAINER="arcana_postgres"
SERVER_LOG="/tmp/arcana-qa-server.log"
# arcana-server runs out of a venv; bare `python` is not on PATH on this Mac
# (the older handoff recipes say `python manage.py runserver` and fail).
PY="$SERVER_DIR/.venv/bin/python"

c_red=$'\033[31m'; c_grn=$'\033[32m'; c_yel=$'\033[33m'; c_dim=$'\033[2m'; c_off=$'\033[0m'
ok()   { printf '%s  ok%s  %s\n' "$c_grn" "$c_off" "$1"; }
warn() { printf '%s warn%s %s\n' "$c_yel" "$c_off" "$1"; }
bad()  { printf '%s fail%s %s\n' "$c_red" "$c_off" "$1"; }
note() { printf '%s      %s%s\n' "$c_dim" "$1" "$c_off"; }

# Capture runserver PIDs by COMMAND LINE, never by port. See hazard 2.
server_pids() { pgrep -f '[m]anage.py runserver' || true; }

# Resolve the EMULATOR specifically. Cole's physical Pixel 9 Pro is often
# USB-attached at the same time, and a bare `adb shell svc wifi disable` with
# two devices attached either errors out or targets the wrong one — i.e. it
# would disable the radios on his real phone. Every adb call in this script
# goes through $(adb_t) so that cannot happen.
emulator_serial() { adb devices | awk '/^emulator-[0-9]+\tdevice$/{print $1; exit}'; }
adb_t() {
  local s; s=$(emulator_serial)
  [ -n "$s" ] && printf -- '-s %s' "$s"
}
have_emulator() { [ -n "$(emulator_serial)" ]; }

require_server_dir() {
  [ -d "$SERVER_DIR" ] || { bad "arcana-server not found at $SERVER_DIR"; exit 1; }
}

cmd_help() {
  cat <<'EOF'
Error-state QA harness — forces failure modes against the LOCAL server only.

  preflight        Check the whole environment before a QA session. Run this first.
  status           What is currently forced, and is the server up?

  start            Start the local server with ops paging + real email DISABLED.
  kill-server      SIGKILL runserver -> ECONNREFUSED. Forces every CONNECTION state.
  restore-server   Alias for `start`.

  db-down          docker stop arcana_postgres -> real 5xx with the server still
                   reachable. This is the ONLY way to force a SERVER state.
  db-up            docker start arcana_postgres.

  stall            SIGSTOP runserver -> socket stalls, no bytes flow. Forces the
                   HttpTimeout path. Before the timeout landed this hung forever.
  unstall          SIGCONT runserver.

  wifi-off         Android only: adb shell svc wifi/data disable. Preferred over a
                   server kill on Android (no emulator-process risk, instant recovery).
  wifi-on          Re-enable both radios.

  assert-local        Mark the server log. Drive the app, then:
  assert-local check  Confirm requests actually reached THIS server, not prod.

  telemetry-off    Blank the PostHog API key so QA does not pollute the prod project.
                   Sentry DSN untouched. Originals are backed up with checksums.
  telemetry-on     Restore the keys and VERIFY them byte-for-byte against the backup.
  telemetry-check  Exit non-zero if the keys are blanked or differ from the backup.
                   Run this before any release build or PR.

  reset            Put everything back: server up, postgres up, radios on, keys restored.

Typical session:
  preflight
  start
  kill-server          # drive the CONNECTION states
  start ; db-down      # drive the SERVER states
  db-up ; stall        # drive the timeout state
  unstall ; reset

Pointing a device at this server (Developer Settings: 10 taps on the signed-out
wordmark, then set BASE URL):
  iOS simulator      http://localhost:8000
  Android emulator   http://localhost:8000  + `adb reverse tcp:8000 tcp:8000`
  PHYSICAL Android   http://localhost:8000  + `adb reverse tcp:8000 tcp:8000` (USB)
Note 10.0.2.2 did NOT reach the host on this machine's emulator — requests went
silently to the prod default instead. Use adb reverse and verify with
`assert-local`. `wifi-off` only ever targets the emulator, never a real phone.
EOF
}

cmd_preflight() {
  echo "── Preflight ─────────────────────────────────────────────"
  require_server_dir

  # 0. Loudest first: are the telemetry keys currently blanked?
  if keys_blanked; then
    bad "TELEMETRY KEYS ARE BLANKED (since $(cat "$KEY_SENTINEL"))"
    note "Correct for a QA run. MUST be restored with \`telemetry-on\` before any"
    note "release build or PR — these files are gitignored so no diff will remind you."
  fi

  # 1. Ops-notifier + email hazard.
  local ops email
  ops=$(grep -E '^OPS_NOTIFIER_CLASS=' "$SERVER_DIR/.env" 2>/dev/null | tail -1 | cut -d= -f2-)
  email=$(grep -E '^EMAIL_SENDER_CLASS=' "$SERVER_DIR/.env" 2>/dev/null | tail -1 | cut -d= -f2-)
  if [ -n "$ops" ] && [[ "$ops" != *Null* ]]; then
    warn ".env OPS_NOTIFIER_CLASS=$ops  (would page Cole + Felicia at emergency priority)"
    note "\`start\` overrides it per-process. Never run the server without it."
  else
    ok "ops notifier is not the real one"
  fi
  if [ -n "$email" ] && [[ "$email" != *Console* ]]; then
    warn ".env EMAIL_SENDER_CLASS=$email  (would send REAL member email)"
    note "\`start\` overrides it per-process."
  else
    ok "email sender is not the real one"
  fi

  # 2. Postgres.
  if docker ps --filter "name=$PG_CONTAINER" --format '{{.Names}}' | grep -q "$PG_CONTAINER"; then
    ok "postgres container up ($PG_CONTAINER)"
  else
    bad "postgres container is NOT running — start it or db-down/db-up will not work"
  fi

  # 3. Server.
  if [ -n "$(server_pids)" ]; then
    ok "runserver up (pids: $(server_pids | tr '\n' ' '))"
  else
    warn "runserver is not running — use \`start\`"
  fi

  # 4. Devices.
  local n_ios
  n_ios=$(xcrun simctl list devices booted 2>/dev/null | grep -c "Booted")
  note "booted iOS simulators: $n_ios"
  if have_emulator; then
    ok "android target resolves to the emulator ($(emulator_serial))"
  else
    warn "no emulator attached — start the Pixel_9_Pro AVD"
  fi
  # A physical device alongside the emulator is not fatal (every adb call here
  # is pinned with -s), but it IS the condition that makes a stray bare `adb`
  # command dangerous, so say so out loud.
  local phys
  phys=$(adb devices | awk '/\tdevice$/{print $1}' | grep -v '^emulator-' | head -1)
  if [ -n "$phys" ]; then
    warn "a PHYSICAL Android device is also attached: $phys"
    note "Every adb call in this script is pinned to the emulator with -s."
    note "But a bare \`adb shell …\` you type yourself will be ambiguous, and on"
    note "this Mac the physical device is Cole's real phone on his real account."
  fi

  # 5. The one thing no script can verify for you.
  echo
  warn "CONFIRM ON EACH DEVICE before trusting any result:"
  note "Developer Settings (10 taps on the signed-out wordmark) -> \"CURRENTLY IN USE\""
  note "must read http://localhost:8000 (iOS sim) or http://10.0.2.2:8000 (Android)."
  note "If it reads https://api.arcana.fit you are driving PRODUCTION: every action"
  note "hits live data and every event lands in the prod PostHog project."
  note "Any storage clear (pm clear / Erase All Content) silently resets it to prod."
}

cmd_status() {
  require_server_dir
  local pids; pids=$(server_pids)
  if [ -z "$pids" ]; then
    echo "server:   DOWN            -> CONNECTION states are forced"
  else
    local stopped=0
    for p in $pids; do
      [ "$(ps -o stat= -p "$p" 2>/dev/null | cut -c1)" = "T" ] && stopped=1
    done
    if [ "$stopped" = 1 ]; then
      echo "server:   STOPPED (SIGSTOP) -> timeout path is forced"
    else
      echo "server:   up (pids: $(echo "$pids" | tr '\n' ' '))"
    fi
  fi

  if docker ps --filter "name=$PG_CONTAINER" --format '{{.Names}}' | grep -q "$PG_CONTAINER"; then
    echo "postgres: up"
  else
    echo "postgres: DOWN            -> SERVER (5xx) states are forced"
  fi

  if have_emulator; then
    local wifi
    # shellcheck disable=SC2046
    wifi=$(adb $(adb_t) shell settings get global wifi_on 2>/dev/null | tr -d '\r')
    echo "android:  emulator $(emulator_serial) wifi_on=$wifi"
  else
    echo "android:  no emulator attached"
  fi
}

cmd_start() {
  require_server_dir
  if [ -n "$(server_pids)" ]; then
    ok "server already running"; return 0
  fi
  # The two overrides are the whole point — see hazard 1.
  [ -x "$PY" ] || { bad "no venv python at $PY"; return 1; }
  # -u (unbuffered) is NOT optional. Without it Django's request log sits in a
  # pipe buffer for minutes, so "did my device hit the local server?" cannot be
  # answered by reading this file — and a device silently talking to PRODUCTION
  # looks identical to a quiet local server. See `assert-local`.
  ( cd "$SERVER_DIR" && \
    OPS_NOTIFIER_CLASS=notifications.telegram.NullOpsNotifier \
    EMAIL_SENDER_CLASS=notifications.email.ConsoleEmailSender \
    nohup "$PY" -u manage.py runserver 0.0.0.0:8000 > "$SERVER_LOG" 2>&1 & )
  sleep 3
  if [ -n "$(server_pids)" ]; then
    ok "server started with NullOpsNotifier + ConsoleEmailSender (log: $SERVER_LOG)"
  else
    bad "server failed to start — check $SERVER_LOG"; return 1
  fi
}

cmd_kill_server() {
  local pids; pids=$(server_pids)
  if [ -z "$pids" ]; then warn "server already down"; return 0; fi
  # By command line, never by port. See hazard 2.
  # shellcheck disable=SC2086
  kill -9 $pids 2>/dev/null
  sleep 1
  if [ -z "$(server_pids)" ]; then
    ok "server killed (was: $(echo "$pids" | tr '\n' ' ')) -> ECONNREFUSED"
  else
    bad "some pids survived: $(server_pids | tr '\n' ' ')"
  fi
}

cmd_db_down() {
  docker stop "$PG_CONTAINER" >/dev/null 2>&1 \
    && ok "postgres stopped -> endpoints now return real 5xx, server still reachable" \
    || bad "could not stop $PG_CONTAINER"
  note "This fails EVERY DB-touching endpoint at once; it cannot target one endpoint."
}

cmd_db_up() {
  docker start "$PG_CONTAINER" >/dev/null 2>&1 && sleep 2 \
    && ok "postgres started" || bad "could not start $PG_CONTAINER"
}

cmd_stall() {
  local pids; pids=$(server_pids)
  [ -z "$pids" ] && { bad "server is not running — nothing to stall"; return 1; }
  # shellcheck disable=SC2086
  kill -STOP $pids 2>/dev/null
  ok "server SIGSTOPped -> socket stalls, no bytes flow"
  note "Expect the CONNECTION state after ~30s (socketTimeoutMillis)."
  note "If it hangs indefinitely instead, HttpTimeout is not installed correctly."
}

cmd_unstall() {
  local pids; pids=$(server_pids)
  [ -z "$pids" ] && { warn "server is not running"; return 0; }
  # shellcheck disable=SC2086
  kill -CONT $pids 2>/dev/null
  ok "server resumed"
}

cmd_wifi_off() {
  have_emulator || { bad "no EMULATOR attached (a physical device does not count)"; return 1; }
  # shellcheck disable=SC2046
  adb $(adb_t) shell svc wifi disable && adb $(adb_t) shell svc data disable
  ok "emulator $(emulator_serial) radios disabled -> CONNECTION state, server untouched"
}

cmd_wifi_on() {
  have_emulator || { bad "no EMULATOR attached (a physical device does not count)"; return 1; }
  # shellcheck disable=SC2046
  adb $(adb_t) shell svc wifi enable && adb $(adb_t) shell svc data enable
  ok "emulator $(emulator_serial) radios enabled"
}

# ── Telemetry keys ───────────────────────────────────────────────────────────
# Blanking the PostHog key drops analytics to the documented Noop path so local
# QA does not pollute the production project. Sentry's DSN is left alone.
#
# THE RISK THIS GUARDS: analytics.properties and Secrets.xcconfig are BOTH
# GITIGNORED. A blanked key never appears in a PR diff, so code review cannot
# catch it — a forgotten restore would silently ship a release with no product
# analytics. Hence: originals are backed up with checksums outside the repo, a
# sentinel file marks the blanked state, and preflight/status refuse to be quiet
# about it.
KEY_BACKUP="/Users/coletomlinson/.arcana-tools/telemetry-key-backup"
KEY_SENTINEL="$KEY_BACKUP/BLANKED"
ANDROID_KEYS="sharedUI/analytics.properties"
IOS_KEYS="iosApp/Configuration/Secrets.xcconfig"

keys_blanked() { [ -f "$KEY_SENTINEL" ]; }

cmd_telemetry_off() {
  local repo; repo=$(cd "$(dirname "$0")/../.." && pwd)
  [ -f "$KEY_BACKUP/analytics.properties.orig" ] || { bad "no backup found — refusing to blank"; return 1; }
  # Blank ONLY the PostHog API key; leave POSTHOG_HOST and the Sentry DSN intact.
  sed -i '' 's/^\(ARCANA_POSTHOG_API_KEY=\).*/\1/' "$repo/$ANDROID_KEYS"
  [ -f "$repo/$IOS_KEYS" ] && sed -i '' 's/^\(POSTHOG_API_KEY[[:space:]]*=\).*/\1/' "$repo/$IOS_KEYS"
  date > "$KEY_SENTINEL"
  ok "PostHog key blanked on both platforms — analytics falls back to NoopAnalytics"
  note "Sentry DSN untouched; the debug \`▶ Telemetry\` console echo still works."
  warn "YOU MUST RUN \`telemetry-on\` BEFORE ANY RELEASE BUILD OR PR."
  note "These files are gitignored, so nothing will remind you in the diff."
}

cmd_telemetry_on() {
  local repo; repo=$(cd "$(dirname "$0")/../.." && pwd)
  [ -f "$KEY_BACKUP/analytics.properties.orig" ] || { bad "no backup found — cannot restore"; return 1; }
  cp "$KEY_BACKUP/analytics.properties.orig" "$repo/$ANDROID_KEYS"
  [ -f "$KEY_BACKUP/Secrets.xcconfig.orig" ] && cp "$KEY_BACKUP/Secrets.xcconfig.orig" "$repo/$IOS_KEYS"
  rm -f "$KEY_SENTINEL"

  # Verify byte-for-byte against the checksum taken before blanking. This is the
  # actual guarantee — not the copy, the verification of the copy.
  local fail=0 want got
  want=$(cat "$KEY_BACKUP/analytics.properties.sha256")
  got=$(shasum -a 256 "$repo/$ANDROID_KEYS" | awk '{print $1}')
  [ "$want" = "$got" ] && ok "android keys restored and checksum-verified" || { bad "android keys DO NOT match backup"; fail=1; }
  if [ -f "$KEY_BACKUP/Secrets.xcconfig.sha256" ]; then
    want=$(cat "$KEY_BACKUP/Secrets.xcconfig.sha256")
    got=$(shasum -a 256 "$repo/$IOS_KEYS" | awk '{print $1}')
    [ "$want" = "$got" ] && ok "ios keys restored and checksum-verified" || { bad "ios keys DO NOT match backup"; fail=1; }
  fi
  [ "$fail" = 0 ] || return 1
}

cmd_telemetry_check() {
  local repo; repo=$(cd "$(dirname "$0")/../.." && pwd)
  if keys_blanked; then
    bad "TELEMETRY KEYS ARE BLANKED (since $(cat "$KEY_SENTINEL"))"
    note "Run \`telemetry-on\` before any release build, PR, or handoff."
    return 1
  fi
  local ok_all=1
  if [ -f "$KEY_BACKUP/analytics.properties.sha256" ]; then
    local want got
    want=$(cat "$KEY_BACKUP/analytics.properties.sha256")
    got=$(shasum -a 256 "$repo/$ANDROID_KEYS" 2>/dev/null | awk '{print $1}')
    [ "$want" = "$got" ] || { bad "android analytics.properties differs from the backup"; ok_all=0; }
  fi
  if [ -f "$KEY_BACKUP/Secrets.xcconfig.sha256" ]; then
    local want got
    want=$(cat "$KEY_BACKUP/Secrets.xcconfig.sha256")
    got=$(shasum -a 256 "$repo/$IOS_KEYS" 2>/dev/null | awk '{print $1}')
    [ "$want" = "$got" ] || { bad "ios Secrets.xcconfig differs from the backup"; ok_all=0; }
  fi
  [ "$ok_all" = 1 ] && ok "telemetry keys match their pre-QA backup" || return 1
}

# Prove a device is talking to THIS server and not production.
#
# Why this exists: on 2026-08-16 the Android emulator's `10.0.2.2:8000` override
# silently did not reach the host, so the app fell back to the prod default and
# several test logins went to production before anyone noticed. Developer
# Settings displayed the override the whole time. The only trustworthy evidence
# is a request arriving HERE.
#
#   assert-local <label>   — snapshot, then re-run after driving the app
cmd_assert_local() {
  local before after
  before=$(wc -l < "$SERVER_LOG" 2>/dev/null || echo 0)
  echo "$before" > /tmp/arcana-qa-logmark
  note "log mark set at line $before — drive the app, then run: assert-local check"
}

cmd_assert_local_check() {
  local mark now
  mark=$(cat /tmp/arcana-qa-logmark 2>/dev/null || echo 0)
  now=$(wc -l < "$SERVER_LOG" 2>/dev/null || echo 0)
  if [ "$now" -gt "$mark" ]; then
    ok "$(( now - mark )) new request(s) reached the LOCAL server"
    tail -n $(( now - mark )) "$SERVER_LOG" | grep -E '"(GET|POST|PUT|DELETE)' | tail -3
  else
    bad "NO requests reached the local server since the mark."
    note "The device is probably talking to PRODUCTION. Check Developer Settings,"
    note "and on Android prefer: adb reverse tcp:8000 tcp:8000 + http://localhost:8000"
    note "(10.0.2.2 did not reach the host on this emulator)."
    return 1
  fi
}

cmd_reset() {
  cmd_unstall >/dev/null 2>&1
  cmd_db_up
  cmd_start
  have_emulator && cmd_wifi_on
  # Restoring telemetry keys is part of "put everything back".
  keys_blanked && { echo; cmd_telemetry_on; }
  echo; cmd_status
}

case "${1:-help}" in
  help|-h|--help) cmd_help ;;
  preflight)      cmd_preflight ;;
  status)         cmd_status ;;
  start|restore-server) cmd_start ;;
  kill-server)    cmd_kill_server ;;
  db-down)        cmd_db_down ;;
  db-up)          cmd_db_up ;;
  stall)          cmd_stall ;;
  unstall)        cmd_unstall ;;
  wifi-off)       cmd_wifi_off ;;
  wifi-on)        cmd_wifi_on ;;
  telemetry-off)  cmd_telemetry_off ;;
  telemetry-on)   cmd_telemetry_on ;;
  telemetry-check) cmd_telemetry_check ;;
  assert-local)   [ "${2:-mark}" = "check" ] && cmd_assert_local_check || cmd_assert_local ;;
  reset)          cmd_reset ;;
  *) bad "unknown command: $1"; echo; cmd_help; exit 1 ;;
esac
