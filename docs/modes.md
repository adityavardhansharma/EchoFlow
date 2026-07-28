# Chat and Imagine

EchoFlow has two top-level surfaces. They are not two feature sets — they are two *shapes* of
interaction, which is why they get separate composers, result presentation and history rather
than sharing one screen with a menu toggle.

**Chat** is turn-taking. You ask, the model answers, the answer is prose you read and move
past. Every chat capability — search, research, browser, artifacts, the Echo modes — is a
variation on "make the answer better".

**Imagine** is a creative loop. Prompt, render, judge, refine. The output is an artifact you
keep, with settings that persist across turns and a result that wants to be big.

Switching is lateral, never navigation: system back does not move between them.

## History is grandfathered, never rewritten

`ChatThread.kind` is stamped once at creation from the active mode and is immutable
afterwards. The v18 migration defaults it to `'chat'`, and **that default is the whole safety
story**: every conversation that predates the split becomes a Chat thread, including ones full
of generated images.

Nothing inspects a thread's content to classify it. A thread with forty messages and three
images was a conversation, and reclassifying it would feel to its owner like losing it.

The drawer filters by the active mode. Two details keep a filtered list from reading as a lost
one:

- The Imagine empty state says outright that older image chats stayed in Chat.
- A scoped search reports how many results the *other* mode holds.

Each mode also remembers where it was left, so a round trip returns you where you were rather
than somewhere else. "Where" has three states, not two — `ModePosition` is a thread, **Blank**,
or Unset. Blank exists because a fresh composer is a position the user deliberately navigated
to by pressing new; folding it into "nothing to remember" is exactly what makes a mode silently
reopen the conversation you had just left.

## Nothing hides work

A clip renders for minutes and is routinely started in one mode and waited for from the other.
`renderingChatIds` reads non-terminal rows straight from `generated_videos` — the only source
that covers live turns, resumed jobs and process death alike — and drives a 6dp breathing dot
on the mode switch and on drawer rows. One vocabulary for "still working", used nowhere else,
so one glance always means one thing.

Notification taps switch modes before selecting, since a thread selected while its own history
is filtered out of view looks exactly like the app losing it.

## The top bar

```
[☰]        ⟨ 💬 Chat │ ✦ Imagine ⟩        [+]
```

Place, **identity**, action. The centre slot belongs to the most permanent thing on screen, so
the mode switch takes it and the model selector moved down to the composer's chip row — in a
multi-provider app the model is something you change mid-thought, and the bottom edge is where
your thumb already is.

The switch is a **floating tray with a raised thumb**, wearing the composer's own material
recipe (`surfaceContainerHigh`, 3dp tonal, 8dp shadow) so the two pills bookend the screen.
That construction also produces the recessed look: the tray shadows *down* onto content while
the thumb shadows *into* the tray, making the unselected side read as carved out. There is
deliberately no true inner shadow — it would need custom drawing, vanish in dark themes where
shadows barely register, and be foreign to Material's lighting model. Tonal contrast carries
the depth when shadows cannot.

The thumb travels on a spring and stretches at the midpoint of the journey, which is what makes
selection feel like an object moving rather than a highlight jumping. Labels never move; only
their colour crossfades underneath.

### One rule for toggles

Floating chrome toggles (the mode switch) wear the tray. In-page toggles (the media switch, the
Settings selector) stay flat. Same distinction that separates the floating composer from an
in-page text field: elevation belongs to chrome that hovers over scrolling content.

## Imagine

Image and video are **one surface with a media switch**, not two modes. They share a prompt, a
framing, a result to keep and a refine loop; how long it takes, whether it can be edited and
whether it has audio are settings, not separate features.

The media switch *is* the capability — choosing Video and pressing send is the whole gesture,
which is why both left Chat's "+" menu.

### What sits where

Only two controls live beside the prompt: the **medium** and the **model** — the two decisions
that change what you are making. Everything that shapes a single request (reference image,
shape, resolution, audio) lives behind the composer's **+**. Four settings stacked above an
empty text box makes a creative tool read as a settings screen.

That fixes the "+" too. One rule holds app-wide: **+ adds to the message you are writing, the
pencil starts a new one.** Two plus buttons a thumb's width apart meaning two unrelated things
is a coin flip whose cost is a lost draft.

Framing is promoted out of Settings because in a creative tool the shape of the thing you are
making is a per-prompt decision. Each option is drawn at its true proportion and springs to its
new shape as the selection moves — the composer-scale echo of the card's stretch-to-aspect
animation. Video framing is a hard contract (an out-of-set ratio is a 400 from OpenRouter), so
unsupported options are disabled with the reason; image framing is an instruction in the
prompt, so every ratio stays available.

Resolution shows only what the chosen model accepts, priced per option — it is the setting most
likely to quietly triple a bill. One supported value renders as a line of text rather than a
picker with a single button; none renders nothing.

### Results

A contact sheet, not a conversation: no assistant header, no user bubble, media at 480dp rather
than a 340dp chat bubble. The generating → stretch → reveal choreography is unchanged, just
given a larger stage.

The prompt prints underneath as a caption — quiet type, no container, aligned to the media
rather than to a speaker. Tapping it loads it back into the composer, so every past result is a
starting point; long-press copies it. Actions sit in one low pill (Save · Share · Reference)
rather than a line of 48dp circles competing with the picture.

**Reference** replaces the old "Ask about this". A picture you wrote the prompt for holds no
mysteries, so sending it to Chat was a dead end dressed as an exit; attaching it to the next
Imagine turn is the loop the mode exists for. Switching Image → Video carries the last image
over automatically as a first frame — guarded so it fires only on the transition, never
displaces a deliberate attachment, and stays quiet when the model cannot accept one.

### The blank canvas

A living, touchable dot field, full bleed, built from the same material as the generation
placeholder. It answers a finger, so the first gesture anyone makes here gets a reply. Every
value in it is tuned *down* — the difference between a texture that feels expensive and one
that feels like a screensaver is entirely restraint.

Over it: one headline and one example prompt, drawn from a pool of a hundred per medium. A
single fixed example makes a surface read as a demo. The video pool is written separately
because video prompts have to describe motion, and image phrasing there teaches people to ask
for a still.

## Provider identity

Provenance is information — the privacy story, the cost story and the latency story at once —
so every model needs to be identifiable at a glance. Real vendor logos are a trademark
minefield, so each provider gets a stable `MaterialShapes` polygon plus a **theme colour role**
(not a brand hex, so identity survives dynamic colour and all six accents). Shape carries the
distinction; the colour role adds a second axis without fighting the user's palette.

The same mark appears in the composer pill, picker rows and Imagine cards from one definition.

## Files

| Concern | File |
| --- | --- |
| Mode + media enums | `data/AppMode.kt`, `data/ImagineMedia.kt` |
| Thread ownership | `data/Models.kt` (`ChatThread.kind`), `data/ChatRepository.kt` |
| Mode state, scoping, bridges | `ui/ChatViewModel.kt` |
| Mode switch | `ui/components/ModeSwitch.kt` |
| Model pill, media toggle, chip row | `ui/components/ContextChips.kt` |
| Provider marks | `ui/components/ProviderIdentity.kt` |
| Ratio grid | `ui/components/AspectRatioPicker.kt` |
| Result action bar | `ui/components/MediaActionBar.kt` |
| Blank-canvas copy | `data/ImaginePrompts.kt` |
| Drawer list + recency | `ui/components/DrawerThreadList.kt` |
| Router / surfaces | `ui/screens/ChatScreen.kt`, `ChatSurface.kt`, `ImagineSurface.kt` |
| Imagine canvas + composer + sheets | `ui/screens/ImagineCanvas.kt`, `ImagineComposer.kt`, `ImagineOptionsSheet.kt`, `ImaginePickerSheet.kt` |
| Field + prompt line | `ui/screens/ImagineField.kt`, `ImaginePromptLine.kt` |
| Merged settings | `ui/screens/SettingsImagine.kt` |
