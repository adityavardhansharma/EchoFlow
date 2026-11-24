# EchoFlow iOS design notes: Liquid Glass

How EchoFlow's Material 3 Expressive UI becomes an iOS app that follows Apple's current design
language. The [design canvas](https://claude.ai/artifact/CL7HW6gAK6nBeaSWMj6Pka) is the visual
reference; this file is the reasoning behind it.

> API names below were checked against Apple's SwiftUI docs for `glassEffect(_:in:)` and `Glass`
> where noted. Everything else comes from WWDC25 session 323 ("Build a SwiftUI app with the new
> design") notes and community references, so confirm each signature in Xcode before relying on it.

## 1. The rules the canvas follows

1. **Glass is the navigation layer only.** The tab bar, nav bar buttons, the composer, menus,
   sheets and floating actions are glass. Messages, cards, lists, reports, images and code
   blocks are solid content underneath. Never put glass on glass.
2. **No custom bar backgrounds.** Content scrolls under the floating controls, and the system
   scroll-edge effect (a soft blur and fade) keeps them legible. The canvas draws that fade at the
   top and bottom of each screen.
3. **Shapes are circles and capsules.** The Expressive cookie, blob and squircle buttons become
   44–56 pt circles and capsules. Containers use concentric corners, so a control inside a sheet
   follows the sheet's curve.
4. **Tint means something.** Only the primary action on a screen gets the palette tint (Send,
   New schedule, Unlock, Open it). Everything else is untinted glass. Destructive actions (end voice,
   stop Browser Flow) use red.
5. **Group related controls.** Neighbouring toolbar buttons share one glass shape inside a
   `GlassEffectContainer`, so they split and merge as the toolbar changes.
6. **Keep EchoFlow's own structure.** The drawer, the Chat and Imagine switch in the top bar, the
   greeting with suggestion chips, the model chip and mic above the composer, and the Settings pages
   all stay as they are on Android. Only the material changes. Liquid Glass lenses what is under it
   at the rim, so the canvas renders that refraction instead of a flat blur.
7. **Regular glass by default, clear glass over media.** Imagine is the only place with `.clear`,
   because the controls sit on generated images.
8. **Respect accessibility settings.** Reduce Transparency, Increase Contrast and Reduce Motion are
   handled by the system when we use the real APIs. Don't fake glass with custom blur views.

## 2. Theming

The Android app has six palettes plus dynamic colour. On iOS:

- **Surfaces use system colours** (`.background`, `.secondarySystemBackground`, grouped
  backgrounds) so glass refracts what it is meant to, in light and dark.
- **Each palette becomes an accent tint**, applied with `.tint(_:)` at the root. Values in the canvas
  stay close to the system colours so the app feels native. Text on a tinted button is white, except
  Mono (inverted) and Forest and Sunset in dark mode (dark text). Check contrast again once SF Pro is in:

| Palette | Light | Dark |
|---|---|---|
| Mono | `#000000` | `#FFFFFF` |
| Ocean | `#007AFF` (system blue) | `#0A84FF` |
| Forest | `#248A3D` | `#30D158` |
| Sunset | `#E8590C` | `#FF9F0A` |
| Lavender | `#5856D6` (system indigo) | `#7D7AFF` |
| Rose | `#E0245E` | `#FF375F` |

- **Dynamic colour has no iOS equivalent.** Drop the "Wallpaper accent" option, or map it to the
  palette picker.
- **Type** is SF Pro with Dynamic Type text styles (`.largeTitle`, `.headline`, `.body`, …). The
  canvas uses Inter as a stand-in because SF Pro can't be embedded on the web.
- **Icons** are SF Symbols. The canvas draws simple line icons in their place.

## 3. Screen map

| Android (Compose) | iOS (SwiftUI) | Canvas board |
|---|---|---|
| Chat top bar: drawer, Chat/Imagine switch, new chat | Glass circle buttons in `.toolbar`, and a glass segmented `Picker` in `.principal` | New chat |
| Greeting and suggestion chips | Same, as content | New chat |
| Composer: model chip and mic above, field with + and Send | `.safeAreaBar(edge: .bottom)`: a glass model chip and mic row over a glass field; the tinted trailing button starts voice mode while the field is empty and becomes Send once there is text | New chat, Conversation |
| Plus menu (Attach, Capabilities, Echo Labs) | `Menu` with `Section`s, growing out of the + button | Plus menu |
| Model picker bottom sheet | `.sheet` at `.medium` detent (inset glass), close and Manage buttons, search, filter | Model picker sheet |
| Navigation drawer | A drawer panel the chat slides away from, with search, pinned and dated chats, and a tinted New chat button. On iPad, the sidebar of a `NavigationSplitView` | Chats drawer |
| Voice mode stage | Full-screen cover, live card, transcript, glass controls (keyboard, stop, end) | Voice mode |
| Deep Research workspace | Pushed view, Report / Sources / Steps switch in a bottom glass bar | Deep Research report |
| Artifact workspace | Pushed view with `WKWebView` preview, version menu in a glass title, Preview / Code switch above the composer | Artifact workspace |
| Browser Flow | Full-screen `WKWebView`, glass address capsule, a glass confirmation panel before paying | Browser Flow |
| Fusion, Adviser, Agents and memory lines in chat | Solid cards inside the message list (content, not glass) | Fusion, Adviser and Agents |
| Artifacts gallery | Large-title grid with filter chips and a bottom glass search field | Creations gallery |
| Imagine | The Imagine side of the switch: media canvas with `.clear` glass controls, the same composer | Imagine |
| Imagine options sheet | `.sheet` with shape grid, count picker and start-from-image | Imagine · options sheet |
| Projects hub, project home | Large-title card grid; project home with instructions, files, chats and a project composer | Projects, Project home |
| Schedules | Large-title list, running-now card, toggles, tinted + button | Schedules |
| Settings and its pages | Inset grouped `List` with coloured icon tiles and a glass back button, same pages and order as Android | Settings, Models, Echo Labs, Spend, Memory |
| App lock | Blurred content with a Face ID unlock button (`LocalAuthentication`) | App lock |

## 4. SwiftUI APIs to use

Verified against Apple's documentation:

```swift
func glassEffect(_ glass: Glass = .regular, in shape: some Shape = DefaultGlassEffectShape()) -> some View
// Glass: .regular, .clear, .identity, .tint(Color?), .interactive(Bool)
```

From WWDC25 session 323 and community references (confirm in Xcode):

| Need | API |
|---|---|
| Group and morph glass shapes | `GlassEffectContainer(spacing:)`, `.glassEffectID(_:in:)`, `.glassEffectUnion(id:namespace:)` |
| Glass buttons | `.buttonStyle(.glass)`, `.buttonStyle(.glassProminent)`, `.buttonBorderShape(.circle)`, `.controlSize(.extraLarge)` |
| Toolbar groups | `ToolbarSpacer(.fixed)`, `ToolbarSpacer(.flexible)`, `.sharedBackgroundVisibility(.hidden)` |
| Bottom search | `.searchable(text:)` on iPhone sits at the bottom. `.searchToolbarBehavior(.minimized)` |
| Content under bars | `.scrollEdgeEffectStyle(_:for:)`, `.backgroundExtensionEffect()` |
| Sheet morphing from a button | `.matchedTransitionSource(id:in:)` + `.navigationTransition(.zoom(sourceID:in:))` |
| Concentric corners | `RoundedRectangle(cornerRadius: .containerConcentric, style: .continuous)` |
| Turning glass off | `Glass.identity`, or branch on `@Environment(\.accessibilityReduceTransparency)` |

The iOS 27 SDK (WWDC26) adds toolbar controls worth knowing: `ToolbarItem(placement:
.topBarPinnedTrailing)`, `.toolbarMinimizeBehavior(.onScrollDown, for: .navigationBar)` and
`ToolbarOverflowMenu`. Third-party summaries also mention `Tab(role: .prominent)`. Liquid Glass
itself needs no code changes between 26 and 27; apps pick up the refreshed look when rebuilt.

## 5. Sources

- Apple: [`glassEffect(_:in:)`](https://developer.apple.com/documentation/swiftui/view/glasseffect(_:in:)),
  [`Glass`](https://developer.apple.com/documentation/swiftui/glass)
- Apple: [What's new in SwiftUI, WWDC26](https://developer.apple.com/videos/play/wwdc2026/269/)
- [WWDC25 323 notes: Build a SwiftUI app with the new design](https://wwdcnotes.com/documentation/wwdc25-323-build-a-swiftui-app-with-the-new-design/)
- [conorluddy/LiquidGlassReference](https://github.com/conorluddy/LiquidGlassReference)
- [WWDC 2026 for teams in production (Atalaya)](https://www.atalayasoft.com/blog/wwdc-2026-swift-6-3-swiftui-ios-27)
