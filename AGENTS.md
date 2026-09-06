# AGENTS.md

## Cursor Cloud specific instructions

EchoFlow is a **single-module Android app** (Kotlin + Jetpack Compose). There is no backend, Docker stack, or npm/pip dependency tree.

### Prerequisites

- **JDK 21** (CI uses Temurin 21; match the major version locally)
- **Android SDK** via `ANDROID_HOME` / `ANDROID_SDK_ROOT` (common default: `~/Android/Sdk`)
- `local.properties` with `sdk.dir=<your SDK path>` (gitignored; created by the update script when missing)

SDK packages are **not pinned to a single machine path or patch release**. Read `compileSdk` in `app/build.gradle.kts` (currently `37`) and install the matching platform with `sdkmanager`, picking the **package id** from `sdkmanager --list` (for example `platforms;android-36` on API 36 hosts). Do not assume the on-disk folder name (such as `platforms/android-37.0`) is the install command — API 37+ may only publish dotted package ids, and patch releases differ across machines. Also install `platform-tools` and a compatible `build-tools` package; Gradle/AGP will report anything still missing on the first build.

```bash
# List candidates, then install the one that matches compileSdk on this host:
sdkmanager --list | rg "platforms;android-<compileSdk>"
sdkmanager "platform-tools" "build-tools;36.0.0" "<platform-package-from-list>"
```

### Common commands

See [README.md](README.md) for product context. Standard Gradle tasks:

| Task | Command |
|------|---------|
| Build debug APK | `./gradlew :app:assembleDebug` |
| Unit tests (no device) | `./gradlew :app:testDebugUnitTest` |
| Lint | `./gradlew :app:lintDebug` |
| Instrumented tests | `./gradlew :app:connectedDebugAndroidTest` |

`./gradlew` must be executable (`chmod +x ./gradlew`).

### Secrets

No API keys are required to build or run tests. Runtime keys are configured in-app via Settings. Optional `.env` is supported by the Secrets Gradle Plugin (see `.env.example`).
