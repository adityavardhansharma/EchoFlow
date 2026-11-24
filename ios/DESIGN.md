# EchoFlow iOS design notes: Liquid Glass

How EchoFlow's Material 3 Expressive UI becomes an iOS app that follows Apple's current design
language. The [design canvas](https://claude.ai/artifact/CL7HW6gAK6nBeaSWMj6Pka) is the visual
reference; this file is the reasoning behind it.

> API names below were checked against Apple's SwiftUI docs for `glassEffect(_:in:)` and `Glass`
> where noted. Everything else comes from WWDC25 session 323 ("Build a SwiftUI app with the new
> design") notes and community references, so confirm each signature in Xcode before relying on it.

## 1. The rules the canvas follows

1. **Glass is the navigation layer only.** Nav bar buttons, the Chat/Imagine switch, the composer,
   menus, sheets and floating actions are glass. Messages, cards, lists, reports, images and code
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
6. **Regular glass by default, clear glass over media.** Imagine is the only place with `.clear`,
   because the controls sit on generated images.
7. **Respect accessibility settings.** Reduce Transparency, Increase Contrast and Reduce Motion are
   handled by the system when we use the real APIs. Don't fake glass with custom blur views.

## 2. Theming

The Android app has six palettes plus dynamic colour. On iOS:

- **Surfaces use system colours** (`.background`, `.secondarySystemBackground`, grouped
  backgrounds) so glass refracts what it is meant to, in light and dark.
- **Each palette becomes an accent tint**, applied with `.tint(_:)` at the root. Values in the canvas
  were picked so white text on the tint passes 4.5:1 in light mode, and dark text passes in dark mode:

| Palette | Light | Dark |
|---|---|---|
| Mono | `#1C1C1E` | `#F2F2F7` |
| Ocean | `#0062CC` | `#409CFF` |
| Forest | `#1E7B3A` | `#4CD97B` |
| Sunset | `#C0420B` | `#FF9F5A` |
| Lavender | `#5E4BD1` | `#A99BFF` |
| Rose | `#C2185B` | `#FF7FAE` |

- **Dynamic colour has no iOS equivalent.** Drop the "Wallpaper accent" option, or map it to the
  palette picker.
- **Type** is SF Pro with Dynamic Type text styles (`.largeTitle`, `.headline`, `.body`, …). The
  canvas uses Inter as a stand-in because SF Pro can't be embedded on the web.
- **Icons** are SF Symbols. The canvas draws simple line icons in their place.

## 3. Screen map

| Android (Compose) | iOS (SwiftUI) | Canvas board |
|---|---|---|
| Chat top bar: drawer button, Chat/Imagine switch, new chat | Floating glass buttons in `.toolbar`, segmented `Picker` in `.principal` | Chat · new chat |
| Greeting and suggestion chips | Same, as content | Chat · new chat |
| Composer with model chip, mic, send | `.safeAreaBar(edge: .bottom)` holding a glass rounded rectangle; voice button turns into send when there's text | Chat · new chat, conversation |
| Plus menu (Attach, Capabilities, Echo Labs) | `Menu` with `Section`s, growing out of the + button | Chat · plus menu |
| Model picker bottom sheet | `.sheet` at `.medium` detent (inset glass), search field, filter `Picker` | Chat · model picker |
| Navigation drawer | Full-screen chat list with a bottom search field and a prominent new-chat button. On iPad, the sidebar of a `NavigationSplitView` | Chats sidebar |
| Voice mode stage | Full-screen cover, glass control cluster at the bottom | Voice mode |
| Deep Research workspace | Pushed view, Report / Sources / Steps picker in a bottom glass bar | Deep Research report |
| Artifact workspace | Pushed view with `WKWebView` preview, version menu in the title | Artifact workspace |
| Browser Flow | Full-screen `WKWebView`, glass address capsule, confirmation sheet at a small detent | Browser Flow |
| Fusion, Adviser, Agents and memory lines in chat | Solid cards inside the message list (content, not glass) | Fusion, Adviser and Agents |
| Artifacts gallery | `LazyVGrid` with a bottom search field | Artifacts gallery |
| Imagine | Media canvas with `.clear` glass controls, the same composer | Imagine |
| Imagine options sheet | `.sheet` with shape grid, resolution picker, sound toggle | Imagine · options |
| Projects hub, project home | Large-title lists, project composer at the bottom | Projects, Project home |
| Schedules | Large-title list, running-now card, toggles, prominent "New schedule" | Schedules |
| Settings and its pages | Inset grouped `List` with coloured icon tiles, glass back button | Settings, Models, Echo Labs, Spend, Memory |
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
