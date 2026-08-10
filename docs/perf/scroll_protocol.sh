#!/bin/bash
# Reproducible Schedule-screen scroll protocol for CMP-upgrade A/B testing.
# See README.md in this directory for the full runbook.
#
# Usage: scroll_protocol.sh <udid> <windows_out_file>
#   IDB env var may point at the idb binary (default: `idb` on PATH).
# Assumes: app open on the Schedule tab, list at top, booted simulator.
set -e
UDID=$1
OUT=$2
IDB=${IDB:-idb}
ts() { python3 -c 'import time; print(f"{time.time():.3f}")'; }

: > "$OUT"

# Warm-up (shader caches, first pagination) — excluded from analysis
for i in 1 2 3 4; do
  $IDB ui swipe --udid "$UDID" 220 750 220 250 --duration 0.1
  sleep 1.2
done

for round in 1 2 3; do
  # FLICK pass: 8 fast swipes, fling inertia between
  for i in 1 2 3 4 5 6 7 8; do
    T0=$(ts)
    $IDB ui swipe --udid "$UDID" 220 750 220 250 --duration 0.1
    T1=$(ts)
    echo "flick$round $T0 $T1" >> "$OUT"
    sleep 1.2
  done
  # SLOW pass: 4 finger-tracking drags
  for i in 1 2 3 4; do
    T0=$(ts)
    $IDB ui swipe --udid "$UDID" 220 750 220 300 --duration 0.6
    T1=$(ts)
    echo "slow$round $T0 $T1" >> "$OUT"
    sleep 1.0
  done
done
echo "protocol done"
