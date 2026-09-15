# Contributing to EchoFlow

EchoFlow is one Android application module. Start with [the architecture guide](docs/architecture.md) to find the owner of a feature, then read the nearby tests before changing behavior.

## Local setup

1. Use JDK 21 and set `JAVA_HOME` to that installation.
2. Install the Android SDK platform matching `compileSdk` in `app/build.gradle.kts`, plus platform tools and compatible build tools. Use `sdkmanager --list` to find the published package ID.
3. Set `ANDROID_HOME` or put `sdk.dir=/your/Android/Sdk` in the gitignored `local.properties`.
4. Run `./gradlew :app:assembleDebug`.

No API keys are needed for builds or tests. Runtime provider keys are configured in Settings. Never commit credentials, local SDK paths, or signing material.

## Making a change

- Put screen code in its feature package: `ui/screens/chat`, `imagine`, `projects`, or `settings`.
- Keep a route's ViewModel collection and navigation near the screen entry point. Give smaller composables values and callbacks so previews and tests can use them directly.
- Keep draft state, asynchronous jobs, and cancellation together in a controller with an explicit coroutine scope. Expose `StateFlow`, keeping its mutable source private.
- Put provider payloads, persistence, and protocol behavior in `data`. Inject transport functions when testing a protocol without the network.
- Keep helpers private unless another file needs them; use `internal` for shared app implementation details.
- Extract by responsibility when a file starts owning unrelated workflows. A new feature should have a clear home without adding another large branch to a general screen or ViewModel.
- Preserve Room migrations, persisted settings names, message serialization, and Android component names during structural changes.

## Verification

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

For a targeted iteration:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.echoflow.ui.chat.ChatAttachmentControllerTest'
```

Use regression tests for behavior that can break: cancellation, thread navigation, stream persistence, provider payloads, and file admission. Existing Compose/Robolectric tests cover screen semantics and layout. Run the full suite before submitting cross-feature changes.

An arm64 device is needed to exercise the packaged on-device inference engines. JVM tests do not establish that native inference or a live provider works. See [AGENTS.md](AGENTS.md) for device and emulator constraints.

## Pull requests

Explain the user-visible problem, what owns the behavior after the change, and the checks you ran. For UI changes, include screenshots when practical. Call out any checks that could not run and why.
