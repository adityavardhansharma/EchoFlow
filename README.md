<div align="center">
  <h1>EchoFlow</h1>
  <p><strong>A privacy-first, bring-your-own-key AI chat app for Android — cloud and on-device models, web search, and multi-step Deep Research, wrapped in expressive Material 3 design.</strong></p>

  <p>
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
    <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-2026.05.00-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
    <img alt="Material 3" src="https://img.shields.io/badge/Material%203-Expressive-6750A4?style=for-the-badge&logo=materialdesign&logoColor=white" />
    <img alt="Android" src="https://img.shields.io/badge/Android-API%2024+-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  </p>

  <p>
    <img alt="OpenRouter" src="https://img.shields.io/badge/OpenRouter-Cloud%20Models-111827?style=flat-square" />
    <img alt="On-device" src="https://img.shields.io/badge/On--device-LiteRT%20%2F%20MediaPipe-FF6F00?style=flat-square" />
    <img alt="Web Search" src="https://img.shields.io/badge/Search-Exa%20%2F%20Parallel%20%2F%20Firecrawl-0F766E?style=flat-square" />
    <img alt="Room" src="https://img.shields.io/badge/Room-Local%20Storage-2563EB?style=flat-square" />
    <img alt="MIT License" src="https://img.shields.io/badge/License-MIT-green?style=flat-square" />
  </p>

  <p>
    <a href="https://github.com/adityavardhansharma/EchoFlow/releases/latest"><strong>📥 Download the app from GitHub Releases</strong></a>
  </p>
</div>

## Overview

EchoFlow is a native Android AI chat client that puts you in control. It is **bring-your-own-key (BYOK)** and **local-first**: there is **no EchoFlow backend, no account, and no telemetry**. You plug in your own provider key — or run a model fully on-device — and the app talks directly to the provider. Your keys, conversations, and downloaded models stay on your phone.

Beyond everyday chat, EchoFlow adds two opt-in power-user modes: **Deep Research**, which investigates the web across multiple steps and writes a cited report, and **Data Agent**, which extracts structured data from the web into clean tables. Both run in the background and never route through anyone's servers but the providers you choose.

## Privacy

- **No backend, no accounts, no analytics or telemetry.** EchoFlow runs entirely on your device and talks straight to the providers you configure.
- **Bring your own key.** API keys are stored only on this device.
- **Local-first storage.** Conversations and history live in a local Room database; nothing is uploaded.
- **Fully offline option.** On-device models need no internet and no API key at all.

## Highlights

- **Any cloud model via OpenRouter** — Claude, GPT, Gemini, DeepSeek and more, with a live model directory (pricing + context windows) to build your own picker.
- **On-device local models** — run models like Gemma, Qwen and DeepSeek distills entirely on your phone: private, offline, and free.
- **Web search for any model** — Exa, Parallel, Firecrawl, or OpenRouter's server-side search.
- **Deep Research** — opt-in, multi-step web investigation that produces a cited report; runs in the background and resumes after interruption.
- **Data Agent** — Firecrawl-powered extraction that returns structured results as cards and tables.
- **Streaming conversations** with smooth text reveal and **reasoning traces** in a collapsible panel.
- **Rich markdown rendering** — headings, lists, blockquotes, links, tables, and syntax-highlighted code.
- **Image attachments** for multimodal conversations.
- **Material 3 Expressive UI** — Material You dynamic color, accent themes, light/dark, and an adaptive tablet layout.

## Download & Setup

1. Grab the latest APK from [GitHub Releases](https://github.com/adityavardhansharma/EchoFlow/releases/latest) and install it (you may need to allow installs from unknown sources).
2. Open **Settings → Cloud models** and paste an [OpenRouter](https://openrouter.ai) API key to use cloud models — or open **Settings → Local models** to download an on-device model and run with no key at all.
3. (Optional) Add an Exa, Parallel, or Firecrawl key under **Settings → Web search** to enable web search, Deep Research, and the Data Agent.

## Modes

### Chat

Streaming answers with independent per-conversation scroll state, reasoning traces for supported models, image attachments, and first-class markdown. Pick any configured cloud or local model from the in-chat model picker.

### Web Search

Give any model live answers. Choose a provider in Settings (OpenRouter server-side search, or **Exa / Parallel / Firecrawl** with your own key), or flip the per-message **Web search** toggle from the **+** menu to search a single message using your last-used provider — no Settings trip required. Citations are shown under the answer.

### Deep Research

An opt-in mode for questions that need real investigation, producing a cited, well-structured report. Choose how it runs from the engine picker:

- **Exa** — Deep Lite, Deep, Deep Reasoning, and **Exa Agent** (with a Low/Medium/High/X-High effort dial)
- **Parallel** — Pro and Ultra
- **Firecrawl** — autonomous deep research
- **Your own chat models** — any OpenRouter model that plans searches and writes the report

Runs happen in a foreground service with a **status-bar notification** (live progress + Cancel) and **resume after the app is closed**. Reports lead with a TL;DR, use sections and comparison tables, and cite sources inline.

### Data Agent

When you need *data* instead of an explanation — pricing, specs, lists, contacts — point the **Firecrawl** agent at a task and get structured results back, rendered automatically as cards and label→value sections. Includes a **live credit meter** and a configurable **spending limit**. Off by default; enable it in **Settings → Data Agent**.

## On-device Models

EchoFlow runs local LLMs through LiteRT-LM / MediaPipe, fully on the device:

- **Curated catalog** of mobile-ready models, plus **search across Hugging Face** for `.task` / `.litertlm` bundles.
- **Import your own** model file from storage.
- **Hugging Face token** support for gated models (e.g. Gemma).
- Local models are private, work offline, and need no API key. Web search can still be offered to them as a tool.

## Design System

EchoFlow follows a Material 3 Expressive direction: tactile, shaped, and responsive, but calm enough for long sessions.

- **Chat surface** — a floating transparent top bar and floating input toolbar let the message list span the full screen. Assistant replies are full-width readable content; user messages keep a distinct authored shape.
- **Motion & shape** — expressive shape morphs, springy press feedback, and Material shape primitives from `androidx.graphics.shapes`.
- **Color** — Material You wallpaper-derived dynamic color by default, with curated accent palettes; color roles flow across containers, drawers, controls, and surfaces.
- **Markdown** — finished and streaming messages share one parsing path (no restyle jumps); code is selectable, scrollable, and highlighted; tables wrap instead of clipping. Deep Research reports and Data Agent results have purpose-built layouts.

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 Expressive |
| Architecture | ViewModel, StateFlow, Compose state |
| Cloud AI | OpenRouter chat completions |
| On-device AI | LiteRT-LM / MediaPipe GenAI |
| Search & Research | Exa, Parallel, Firecrawl, OpenRouter |
| Background work | Foreground service + Room-backed run store |
| Networking | OkHttp, Retrofit |
| JSON | Moshi |
| Persistence | Room |
| Preferences | Shared settings repository |
| Images | Coil |
| Markdown | Custom Compose renderer + Mike Penz markdown modules |
| Code Highlighting | `dev.snipme:highlights` |
| Design Utilities | `androidx.graphics:graphics-shapes` |
| Testing | JUnit, AndroidX test, Robolectric, Roborazzi |

## Project Structure

```text
app/src/main/java/com/echoflow
├── data        # Room entities/DAOs, settings, OpenRouter + web search services,
│               # local-model engine/downloader, Deep Research engine + foreground service
├── ui          # ViewModels and app state
├── ui/components  # reusable expressive UI, markdown + research/result rendering
├── ui/screens  # Chat and settings screens
└── ui/theme    # Material Expressive theme, color, shape, motion helpers
```

## Building from Source

EchoFlow is a standard Gradle Android project — open it in Android Studio and run the `app` configuration, or build a debug APK:

```bash
./gradlew assembleDebug
```

No keys are required to build. Provider keys are entered at runtime in the app's Settings.

## License

EchoFlow is released under the MIT License. See [LICENSE.txt](LICENSE.txt) for details.
