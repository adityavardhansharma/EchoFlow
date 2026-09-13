# Sideload updates: upgrade vs downgrade

Android decides upgrade vs downgrade by **`versionCode`** (an integer), not by
`versionName` (`5.2.4`, `5.2.5`, ...).

## How EchoFlow versions APKs

* `app/build.gradle.kts`: `versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1`.
  Local builds without a numeric env var are `versionCode=1` (an unset *or*
  non-numeric `VERSION_CODE` both fall back to `1`).
* `.github/workflows/release-apk.yml`: `VERSION_CODE = 1000 + GITHUB_RUN_NUMBER`,
  strictly increasing per workflow run.

Verified with `aapt dump badging`:

| APK | `versionCode` | `versionName` |
| --- | --- | --- |
| `EchoFlow-5.2.4.apk` | 1138 (= 1000 + run 138) | 5.2.4 |
| `EchoFlow-5.2.5.apk` | 1139 (= 1000 + run 139) | 5.2.5 |

Both are signed by the same cert, so `5.2.4 -> 5.2.5` (1138 -> 1139) installs
as an upgrade, while `5.2.5 -> 5.2.4` (1139 -> 1138) is a downgrade and the OS
rejects it with `INSTALL_FAILED_VERSION_DOWNGRADE`.

Before commit `6cd62c4` (2026-06-12) `versionCode` was hardcoded to `1`, so
every build reinstalled over every other build (equal codes). That is why
downgrades "used to work".

## There is no in-app/Gradle bypass

The downgrade check is enforced by the OS `PackageManager` for every installer
(file manager tap-to-install, `adb`, `PackageInstaller`). No manifest attribute
or Gradle flag disables it for normal installs. (`adb -d` / device-owner flows
set a privileged allow-downgrade flag third-party apps cannot set.)

## Supported downgrade paths

1. **Keep data (dev/test):**
   ```bash
   ./scripts/install-apk.sh EchoFlow-5.2.4.apk --allow-downgrade
   # equivalent: adb install -r -d EchoFlow-5.2.4.apk
   ```
2. **Clean reinstall (wipes app data; Android backup may restore the DB):**
   ```bash
   adb uninstall com.echoflow
   adb install EchoFlow-5.2.4.apk
   ```

> **Database downgrade warning:** keeping app data across a downgrade is only
> safe when both builds use the same Room schema (currently v25 with
> forward-only migrations in `AppDatabase.kt`). Opening a newer database with
> an older build crashes at startup, so downgrade across releases that bumped
> the schema with `--clean` instead — and note Android backup can restore the
> newer DB after a clean reinstall, hitting the same crash.

If signatures ever differ (debug vs release keys), even upgrades fail with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`; compare with
`apksigner verify --print-certs old.apk new.apk`.
