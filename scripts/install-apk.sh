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
# Downgrade caveat: keeping app data across a downgrade is only safe when both
# builds use the same Room database schema. The app ships forward-only
# migrations, so opening a newer database with an older build crashes at
# startup; use --clean in that case (see docs/sideload-updates.md).
#
# Usage:
#   ./scripts/install-apk.sh [options] <apk-file>
#
#   --allow-downgrade      pass -d so an older APK can replace a newer one (default)
#   --no-allow-downgrade   refuse instead of downgrading
#   --clean                uninstall first instead of -d (destroys app data)
set -euo pipefail

ALLOW_DOWNGRADE=1
CLEAN=0
APK=""
for arg in "$@"; do
  case "$arg" in
    --allow-downgrade) ALLOW_DOWNGRADE=1 ;;
    --no-allow-downgrade) ALLOW_DOWNGRADE=0 ;;
    --clean) CLEAN=1 ;;
    --*) echo "error: unknown option '$arg'" >&2; exit 2 ;;
    *)
      if [[ -n "$APK" ]]; then echo "error: only one APK file expected" >&2; exit 2; fi
      APK="$arg" ;;
  esac
done

if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "usage: $0 [--allow-downgrade|--no-allow-downgrade] [--clean] <apk-file>" >&2
  exit 2
fi

PKG="com.echoflow"

# Discover aapt from any installed build-tools release (newest first), then PATH.
find_aapt() {
  if command -v aapt >/dev/null 2>&1; then command -v aapt; return 0; fi
  local roots=()
  [[ -n "${ANDROID_HOME:-}" ]] && roots+=("$ANDROID_HOME/build-tools")
  [[ -n "${ANDROID_SDK_ROOT:-}" ]] && roots+=("$ANDROID_SDK_ROOT/build-tools")
  roots+=("$HOME/Android/Sdk/build-tools")
  local dirs=()
  local d
  for d in "${roots[@]}"; do
    [[ -d "$d" ]] || continue
    while IFS= read -r sub; do dirs+=("$sub"); done < <(ls -1 "$d" 2>/dev/null | sort -Vr)
    local v
    for v in "${dirs[@]}"; do
      if [[ -x "$d/$v/aapt" ]]; then echo "$d/$v/aapt"; return 0; fi
    done
    dirs=()
  done
  return 1
}

AAPT="$(find_aapt || true)"
apk_code=""
if [[ -n "$AAPT" ]]; then
  apk_code="$("$AAPT" dump badging "$APK" 2>/dev/null | grep -o "versionCode='[0-9]*'" | head -1 | grep -o "[0-9]*" || true)"
fi
if [[ -n "$apk_code" ]]; then
  echo "APK versionCode: $apk_code (via $AAPT)"
else
  echo "APK versionCode: unknown (aapt unavailable or parse failed)"
fi

INSTALLED=0
installed_code=""
if adb shell pm path "$PKG" 2>/dev/null | grep -q "package:"; then
  INSTALLED=1
  installed_code="$(adb shell dumpsys package "$PKG" 2>/dev/null | grep -m1 "versionCode=" | grep -o "[0-9]*" | head -1 || true)"
  echo "Installed versionCode: ${installed_code:-unknown}"
else
  echo "Package $PKG not installed; fresh install."
fi

if [[ "$CLEAN" == "1" ]]; then
  if [[ "$INSTALLED" == "1" ]]; then
    echo "Uninstalling $PKG (clears app data)..."
    adb uninstall "$PKG"
  fi
  echo "Installing $APK..."
  exec adb install "$APK"
fi

if [[ "$INSTALLED" == "1" && -z "$apk_code" ]]; then
  echo "error: cannot determine APK versionCode and $PKG is already installed." >&2
  echo "Install an SDK build-tools package (for aapt) or run 'adb install -r -d \"$APK\"' manually." >&2
  exit 1
fi

is_downgrade=0
if [[ -n "$apk_code" && -n "$installed_code" && "$apk_code" -lt "$installed_code" ]]; then
  is_downgrade=1
  echo "Downgrade detected ($apk_code < $installed_code). Plain installs are blocked by Android (INSTALL_FAILED_VERSION_DOWNGRADE)."
fi

if [[ "$is_downgrade" == "1" ]]; then
  if [[ "$ALLOW_DOWNGRADE" == "1" ]]; then
    echo "WARNING: keeping app data across a downgrade is only safe when both builds share the same Room schema; otherwise the app will crash at startup and you must re-run with --clean."
    echo "Installing with downgrade allowed (adb install -r -d)..."
    exec adb install -r -d "$APK"
  else
    echo "Refusing downgrade. Re-run with --allow-downgrade or --clean." >&2
    exit 1
  fi
fi

echo "Installing $APK..."
exec adb install -r "$APK"
