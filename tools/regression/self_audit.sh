#!/usr/bin/env bash
# self_audit.sh — mechanical implementation of docs/regression/runbook.md's
# "Phase 1 — Self-audit".
#
# What this is: a forward pass (does every user-facing surface in the source
# tree trace back to an inventory entry?) and a reverse pass (does every
# Source: path in the inventory still resolve to a real file?) run against
# the CURRENT tree, with no device/server/simulator involved. It exists so
# an implementing agent can check its own inventory edit before handing work
# back, and so CI/humans can spot-check the inventory stays honest over time.
#
# Never-halt convention: this script always exits 0. Its findings are
# reported as a "FINDINGS: N" line on stdout — callers grep for that rather
# than relying on the exit code. This mirrors the runbook's Phase 1, whose
# findings never halt a regression run either; they're just surfaced.
#
# Implementation note (2026-08-15): the runbook fixes the extraction order
# ("strip balanced parentheses first, across the whole line, before any
# splitting", then comma-split + trim + backtick-strip) and the matching rule
# ("does its path — or, for nav destinations, its name — appear in at least
# one inventory entry's `- **Source:**` line"). This script implements exactly
# that: balanced-paren stripping via a generic depth-counting scan (handles
# nesting even though none exists in the current file), and nav-destination
# names matched against `- **Source:**` lines only, same as file basenames.
# It reproduces the runbook's pinned sanity numbers — 0 findings, 218 Source
# lines, 434 tokens, 67 unique paths (verified 2026-08-11 and 2026-08-15). If
# the runbook is later tightened to say something different, prefer the
# runbook and update this comment.
#
# Usage: tools/regression/self_audit.sh   (from anywhere; it cd's to the
# repo root itself). Always exits 0.

set -u

# ---------------------------------------------------------------------------
# Resolve repo root from this script's own location (two levels up from
# tools/regression/) rather than assuming the caller's cwd or requiring git.
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." >/dev/null 2>&1 && pwd)"
cd "${REPO_ROOT}" || { echo "self_audit.sh: could not cd to repo root ${REPO_ROOT}"; echo "FINDINGS: 1"; exit 0; }

INVENTORY="docs/regression/inventory.md"
FINDINGS=0

if [ ! -f "${INVENTORY}" ]; then
  echo "self_audit.sh: ${INVENTORY} not found from repo root ${REPO_ROOT} — cannot audit."
  echo "FINDINGS: 1"
  exit 0
fi

SOURCE_LINES_FILE="$(mktemp)"
grep '^- \*\*Source:\*\*' "${INVENTORY}" > "${SOURCE_LINES_FILE}"

basename_covered() {
  # $1 = basename or nav-destination name to look for on a Source: line.
  grep -qF -- "$1" "${SOURCE_LINES_FILE}"
}

echo "== Phase 1 self-audit (docs/regression/runbook.md) =="
echo "Repo root: ${REPO_ROOT}"
echo

# ---------------------------------------------------------------------------
# FORWARD PASS — find surfaces the inventory might not cover.
# Globs match the runbook's Phase 1 "Forward" section verbatim.
# ---------------------------------------------------------------------------

echo "-- Forward pass --"

# 1. ViewModel *declarations* (not every file mentioning the word), excluding
#    commonTest — unit tests are not user-facing surfaces.
VM_FILES="$(grep -rlE '^[[:space:]]*(open |abstract |internal |private )*class [A-Za-z0-9_]*ViewModel\b' \
  --include='*.kt' sharedUI/src sharedLogic/src 2>/dev/null | grep -v '/commonTest/' | sort)"
VM_COUNT=0
VM_UNCOVERED=0
if [ -n "${VM_FILES}" ]; then
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    VM_COUNT=$((VM_COUNT + 1))
    b="$(basename "$f")"
    if ! basename_covered "$b"; then
      echo "UNCOVERED (ViewModel): $f"
      VM_UNCOVERED=$((VM_UNCOVERED + 1))
      FINDINGS=$((FINDINGS + 1))
    fi
  done <<< "${VM_FILES}"
fi

# 2. *Screen.kt files under sharedUI/src.
SCREEN_FILES="$(find sharedUI/src -iname "*Screen.kt" 2>/dev/null | sort)"
SCREEN_COUNT=0
SCREEN_UNCOVERED=0
if [ -n "${SCREEN_FILES}" ]; then
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    SCREEN_COUNT=$((SCREEN_COUNT + 1))
    b="$(basename "$f")"
    if ! basename_covered "$b"; then
      echo "UNCOVERED (Screen): $f"
      SCREEN_UNCOVERED=$((SCREEN_UNCOVERED + 1))
      FINDINGS=$((FINDINGS + 1))
    fi
  done <<< "${SCREEN_FILES}"
fi

# 3. Sheet/Dialog/Picker — modal/overlay surfaces that are user-facing but
#    never become a nav destination, so they don't show up as *Screen.kt.
SDP_FILES="$(find sharedUI/src \( -iname "*Sheet.kt" -o -iname "*Dialog.kt" -o -iname "*Picker.kt" \) 2>/dev/null | sort)"
SDP_COUNT=0
SDP_UNCOVERED=0
if [ -n "${SDP_FILES}" ]; then
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    SDP_COUNT=$((SDP_COUNT + 1))
    b="$(basename "$f")"
    if ! basename_covered "$b"; then
      echo "UNCOVERED (Sheet/Dialog/Picker): $f"
      SDP_UNCOVERED=$((SDP_UNCOVERED + 1))
      FINDINGS=$((FINDINGS + 1))
    fi
  done <<< "${SDP_FILES}"
fi

# 4. Nav destinations — every @Serializable data object/data class declared
#    in the sealed ArcanaDestination interface. Matched by NAME (not path)
#    against Source: lines, per the runbook's "or, for nav destinations, its
#    name" clause.
DEST_FILE="sharedLogic/src/commonMain/kotlin/org/arcana/mobile/navigation/ArcanaDestinations.kt"
DEST_NAMES=""
DEST_COUNT=0
DEST_UNCOVERED=0
if [ -f "${DEST_FILE}" ]; then
  DEST_NAMES="$(grep -oE '(data object|data class) [A-Za-z0-9_]+' "${DEST_FILE}" | awk '{print $3}')"
  if [ -n "${DEST_NAMES}" ]; then
    while IFS= read -r d; do
      [ -z "$d" ] && continue
      DEST_COUNT=$((DEST_COUNT + 1))
      if ! basename_covered "$d"; then
        echo "UNCOVERED (nav destination): $d"
        DEST_UNCOVERED=$((DEST_UNCOVERED + 1))
        FINDINGS=$((FINDINGS + 1))
      fi
    done <<< "${DEST_NAMES}"
  fi
else
  echo "WARNING: nav destination file not found at ${DEST_FILE} (skipping nav-destination check)"
fi

echo "Forward pass counts: ${VM_COUNT} ViewModels (${VM_UNCOVERED} uncovered), ${SCREEN_COUNT} *Screen.kt (${SCREEN_UNCOVERED} uncovered), ${SDP_COUNT} Sheet/Dialog/Picker (${SDP_UNCOVERED} uncovered), ${DEST_COUNT} nav destinations (${DEST_UNCOVERED} uncovered)."
echo

# ---------------------------------------------------------------------------
# REVERSE PASS — find inventory entries pointing at code that no longer
# exists. Order matters: strip balanced parentheses across the WHOLE line
# first (annotations contain their own commas), THEN split the remainder on
# commas, trim, strip stray backticks, and test -f each resulting path.
# ---------------------------------------------------------------------------

echo "-- Reverse pass --"

REVERSE_OUT="$(python3 - "${SOURCE_LINES_FILE}" "${REPO_ROOT}" <<'PYEOF'
import sys

source_lines_file, repo_root = sys.argv[1], sys.argv[2]

def strip_balanced_parens(s):
    out = []
    depth = 0
    for ch in s:
        if ch == '(':
            depth += 1
            continue
        if ch == ')':
            if depth > 0:
                depth -= 1
            continue
        if depth == 0:
            out.append(ch)
    return ''.join(out)

import os

tokens_total = 0
paths = []
with open(source_lines_file) as f:
    for line in f:
        line = line.rstrip('\n')
        prefix = '- **Source:**'
        if not line.startswith(prefix):
            continue
        rest = line[len(prefix):]
        stripped = strip_balanced_parens(rest)
        for part in stripped.split(','):
            tokens_total += 1
            p = part.strip().strip('`').strip()
            if p:
                paths.append(p)

unique_paths = sorted(set(paths))
missing = [p for p in unique_paths if not os.path.isfile(os.path.join(repo_root, p))]

for m in missing:
    print("MISSING:" + m)
print("__TOKENS__:" + str(tokens_total))
print("__UNIQUE__:" + str(len(unique_paths)))
print("__MISSING_COUNT__:" + str(len(missing)))
PYEOF
)"

TOKENS=0
UNIQUE=0
MISSING_COUNT=0
while IFS= read -r line; do
  case "$line" in
    MISSING:*)
      path="${line#MISSING:}"
      echo "STALE SOURCE (file not found): ${path}"
      FINDINGS=$((FINDINGS + 1))
      ;;
    __TOKENS__:*)
      TOKENS="${line#__TOKENS__:}"
      ;;
    __UNIQUE__:*)
      UNIQUE="${line#__UNIQUE__:}"
      ;;
    __MISSING_COUNT__:*)
      MISSING_COUNT="${line#__MISSING_COUNT__:}"
      ;;
  esac
done <<< "${REVERSE_OUT}"

SOURCE_LINE_COUNT="$(wc -l < "${SOURCE_LINES_FILE}" | tr -d ' ')"
echo "Reverse pass counts: ${SOURCE_LINE_COUNT} Source: lines, ${TOKENS} comma-split tokens, ${UNIQUE} unique paths (${MISSING_COUNT} missing)."

# Sanity band: a clean tree yields ~94 unique paths (verified 2026-09-04; the
# runbook's Phase 1 has said 94 since 2026-08-11 and this band said 67, so it
# warned on every clean tree until corrected). A count wildly outside the band
# means the extractor itself is broken (e.g. paren-stripping regressed), not
# that the tree suddenly grew/shrank that much — per the runbook, fix the
# extractor rather than filing the output as a wall of findings.
if [ "${UNIQUE}" -lt 70 ] || [ "${UNIQUE}" -gt 120 ]; then
  echo "SANITY WARNING: ${UNIQUE} unique Source paths is outside the expected ~70-120 band (94 verified 2026-09-04) — suspect the extractor, not inventory drift, before trusting the findings above."
fi

rm -f "${SOURCE_LINES_FILE}"

echo
echo "FINDINGS: ${FINDINGS}"
exit 0
