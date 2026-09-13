#!/usr/bin/env bash
# Install an EchoFlow APK over adb, handling the Android downgrade block.
#
# Background: Android's PackageManager compares versionCode, not versionName.
# Release APKs use VERSION_CODE=1000+GITHUB_RUN_NUMBER (see release-apk.yml),
# so an older release always has a smaller versionCode than a newer one and a
# plain install is rejected with INSTALL_FAILED_VERSION_DOWNGRADE. There is no
# manifest/Gradle flag that bypasses this -- it is enforced by the OS for every
# installer (tap-to-install, adb, PackageInstaller). The supported paths are:
#   1. `adb install -r -d` (allows the downgrade, keeps app data), or
#   2. `adb uninstall` + fresh install (wipes app data; Android backup may restore DB).
#
# Usage:
#   ./scripts/install-apk.sh <apk-file> [--allow-downgrade] [--clean]
#
#   --allow-downgrade   pass -d so an older APK can replace a newer one (default: on
#                       when a downgrade is detected; kept as an explicit flag for clarity)
#   --clean             uninstall first instead of -d (destroys app data)
set -euo pipefail

APK="${1:-}"
ALLOW_DOWNGRADE=1
CLEAN=0
for arg in "$@"; do
  case "$arg" in
    --allow-downgrade) ALLOW_DOWNGRADE=1 ;;
    --no-allow-downgrade) ALLOW_DOWNGRADE=0 ;;
    --clean) CLEAN=1 ;;
  esac
done

if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "usage: $0 <apk-file> [--allow-downgrade] [--clean]" >&2
  exit 2
fi

PKG="com.echoflow"
AAPT=""
for candidate in \
  "${ANDROID_HOME:-}/build-tools/36.0.0/aapt" \
  "${ANDROID_SDK_ROOT:-}/build-tools/36.0.0/aapt" \
  "$HOME/Android/Sdk/build-tools/36.0.0/aapt"; do
  if [[ -x "$candidate" ]]; then AAPT="$candidate"; break; fi
done
if [[ -z "$AAPT" ]] && command -v aapt >/dev/null 2>&1; then AAPT="aapt"; fi

apk_code=""
if [[ -n "$AAPT" ]]; then
  apk_code="$("$AAPT" dump badging "$APK" 2>/dev/null | grep -o "versionCode='[0-9]*'" | head -1 | grep -o "[0-9]*" || true)"
  echo "APK versionCode: ${apk_code:-unknown}"
fi

installed_code=""
if adb shell pm path "$PKG" 2>/dev/null | grep -q "package:"; then
  installed_code="$(adb shell dumpsys package "$PKG" 2>/dev/null | grep -m1 "versionCode=" | grep -o "[0-9]*" | head -1 || true)"
  echo "Installed versionCode: ${installed_code:-unknown}"
else
  echo "Package $PKG not installed; fresh install."
fi

is_downgrade=0
if [[ -n "$apk_code" && -n "$installed_code" && "$apk_code" -lt "$installed_code" ]]; then
  is_downgrade=1
  echo "Downgrade detected ($apk_code < $installed_code). Plain installs are blocked by Android (INSTALL_FAILED_VERSION_DOWNGRADE)."
fi

if [[ "$CLEAN" == "1" && -n "$installed_code" ]]; then
  echo "Uninstalling $PKG (clears app data)..."
  adb uninstall "$PKG"
  echo "Installing $APK..."
  exec adb install "$APK"
fi

if [[ "$is_downgrade" == "1" ]]; then
  if [[ "$ALLOW_DOWNGRADE" == "1" ]]; then
    echo "Installing with downgrade allowed (adb install -r -d)..."
    exec adb install -r -d "$APK"
  else
    echo "Refusing downgrade. Re-run with --allow-downgrade or --clean." >&2
    exit 1
  fi
fi

echo "Installing $APK..."
exec adb install -r "$APK"
