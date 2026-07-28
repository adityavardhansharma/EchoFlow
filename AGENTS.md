# AGENTS.md

## Cursor Cloud specific instructions

EchoFlow is a **single-module Android app** (Kotlin + Jetpack Compose). There is no backend, Docker stack, or npm/pip dependency tree.

### Prerequisites (VM image)

- **JDK 21** (OpenJDK 21 is preinstalled)
- **Android SDK** at `/home/ubuntu/Android/Sdk` (platform `android-37.0`, build-tools `36.0.0`, platform-tools)
- `local.properties` with `sdk.dir=/home/ubuntu/Android/Sdk` (gitignored; created by the update script if missing)

### Common commands

See [README.md](README.md) for product context. Standard Gradle tasks:

| Task | Command |
|------|---------|
| Build debug APK | `./gradlew :app:assembleDebug` |
| Unit tests (no device) | `./gradlew :app:testDebugUnitTest` |
| Lint | `./gradlew :app:lintDebug` |
| Instrumented tests (device/emulator) | `./gradlew :app:connectedDebugAndroidTest` |

`./gradlew` must be executable (`chmod +x ./gradlew`).

### Emulator / on-device run caveats

- Release and default debug APKs package **arm64-v8a only** (on-device AI native libs). They install on physical arm64 phones, not on x86_64 emulators.
- Cloud VMs are typically **x86_64 without `/dev/kvm`**, so the Android Emulator cannot run arm64 system images here. Use **Robolectric unit tests** for UI/logic validation in cloud agents.
- To run the full app interactively: use an **arm64 Android device** or an **arm64 host with KVM**, then `./gradlew :app:installDebug`.

### Secrets

No API keys are required to build or run tests. Runtime keys are configured in-app via Settings. Optional `.env` is supported by the Secrets Gradle Plugin (see `.env.example`).
