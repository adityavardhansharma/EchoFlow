# EchoFlow for iOS

This folder is where the native iOS app will live. It is **design-first** for now: nothing here is
built or shipped yet, and the Android app in `app/` is unaffected.

- **Design canvas:** [EchoFlow iOS · Liquid Glass](https://claude.ai/artifact/CL7HW6gAK6nBeaSWMj6Pka)
  has every Android screen redrawn in Liquid Glass (22 phone screens plus a component kit), with
  light/dark and all six palettes as tweaks. The link is private until the owner shares it.
- **Design notes:** [`DESIGN.md`](DESIGN.md) covers the rules the canvas follows, how each Android
  surface maps to iOS, and the SwiftUI APIs to use.
- **Code sketches:** [`EchoFlow/`](EchoFlow/) has the intended project layout and two unbuilt
  SwiftUI files (theme tokens and the glass chat composer). They show intent, not working code.

## Target

| | |
|---|---|
| Minimum OS | iOS 26 / iPadOS 26. Liquid Glass and its SwiftUI APIs start there. |
| Toolchain | Xcode 26 or later, Swift 6 strict concurrency. With the Xcode 27 SDK, use the UIScene lifecycle (SwiftUI `App` already does) and don't assume a fixed iPhone size on iPad. |
| UI | SwiftUI only. UIKit only where SwiftUI has no equivalent (for example `WKWebView` for Artifacts and Browser Flow). |
| State | `@Observable` models, one per feature, mirroring the Android ViewModels and controllers. |
| Storage | SwiftData for chats, projects and artifacts; Keychain for API keys. |

## Planned layout

```
ios/
  EchoFlow.xcodeproj            (not created yet)
  EchoFlow/
    App/                        EchoFlowApp, root navigation, app lock gate
    DesignSystem/               palettes, glass components, icons (SF Symbols)
    Features/
      Chat/                     composer, messages, plus menu, model picker
      Imagine/                  canvas, options sheet
      Projects/                 hub, project home, files, instructions
      Schedules/
      Workspaces/               artifact, research and browser workspaces
      Settings/                 models, Echo Labs, spend, memory, voice
    Data/                       provider clients, persistence, streaming
  EchoFlowKeyboard/             keyboard extension for system-wide dictation (later)
  EchoFlowTests/
```

## What still has to be decided

- **On-device models.** LiteRT-LM and MediaPipe are Android-first. On iOS the options are Apple's
  Foundation Models framework, MLX Swift, or llama.cpp through a Swift package. This needs a spike
  before the Models screen's "On iPhone" tab is final.
- **Schedules.** iOS gives no exact-time background execution. Runs would use `BGTaskScheduler`
  plus a local notification, so the copy on that screen says iOS picks the moment.
- **System-wide dictation.** Android uses an accessibility overlay. On iOS a custom keyboard
  extension can insert text, but it can't record from the microphone, even with Full Access. So
  recording is a separate decision: the keyboard would hand off to the containing app (or a Live
  Activity it starts) to capture and transcribe audio, then pass the text back through a shared app
  group. That flow needs a spike, plus a review and privacy check.
- **Distribution.** TestFlight first. The Android sideload updater has no iOS equivalent.
