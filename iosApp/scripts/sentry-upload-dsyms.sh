#!/bin/sh
# Uploads iOS dSYMs to Sentry so crash stack traces are symbolicated.
# Wired as an Xcode "Run Script" build phase on the iosApp target (see
# scripts/README_DSYM_UPLOAD.md). Safe to commit — holds no secrets.
#
# Auth/org/project come from a gitignored ~/.sentryclirc or iosApp/.sentryclirc
# (or SENTRY_AUTH_TOKEN / SENTRY_ORG / SENTRY_PROJECT env vars). If sentry-cli
# isn't installed or no token is configured, this no-ops with a warning rather
# than failing the build.

set -e

if ! command -v sentry-cli >/dev/null 2>&1; then
  echo "warning: sentry-cli not installed — skipping dSYM upload (brew install getsentry/tools/sentry-cli)"
  exit 0
fi

# Only meaningful when Xcode produced dSYMs (Release builds by default).
if [ -z "$DWARF_DSYM_FOLDER_PATH" ]; then
  echo "warning: DWARF_DSYM_FOLDER_PATH unset — skipping dSYM upload"
  exit 0
fi

echo "Uploading dSYMs to Sentry from $DWARF_DSYM_FOLDER_PATH"
sentry-cli debug-files upload --include-sources "$DWARF_DSYM_FOLDER_PATH" || \
  echo "warning: sentry-cli dSYM upload failed (continuing build)"
