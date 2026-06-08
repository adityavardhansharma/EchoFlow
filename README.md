<div align="center">
  <h1>EchoFlow</h1>
  <p><strong>A polished Android AI chat client with expressive Material design, streaming responses, reasoning traces, and rich markdown rendering.</strong></p>

  <p>
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
    <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-2026.05.00-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
    <img alt="Material 3" src="https://img.shields.io/badge/Material%203-Expressive-6750A4?style=for-the-badge&logo=materialdesign&logoColor=white" />
    <img alt="Android" src="https://img.shields.io/badge/Android-API%2024+-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  </p>

  <p>
    <img alt="OpenRouter" src="https://img.shields.io/badge/OpenRouter-AI%20Models-111827?style=flat-square" />
    <img alt="Room" src="https://img.shields.io/badge/Room-Local%20History-0F766E?style=flat-square" />
    <img alt="DataStore" src="https://img.shields.io/badge/DataStore-Preferences-2563EB?style=flat-square" />
    <img alt="Retrofit" src="https://img.shields.io/badge/Retrofit-Networking-C2410C?style=flat-square" />
    <img alt="Moshi" src="https://img.shields.io/badge/Moshi-JSON-9333EA?style=flat-square" />
    <img alt="MIT License" src="https://img.shields.io/badge/License-MIT-green?style=flat-square" />
  </p>
</div>

## Overview

EchoFlow is a native Android chat app built for fast, comfortable conversations with AI models through OpenRouter. It combines a refined Compose interface with practical chat features: streaming answers, persisted conversations, custom model management, image attachments, reasoning display for supported models, and rich markdown output that can handle the kind of structured responses people actually ask for.

The app is designed to feel modern without being loud. It uses Material 3 Expressive motion, dynamic color, shaped controls, adaptive surfaces, and a focused chat layout where the conversation stays central.

## Highlights

- **Streaming AI conversations** with smooth text reveal instead of bursty token dumps.
- **Reasoning traces** for supported models, shown in a collapsible panel separate from the final answer.
- **OpenRouter model support** with a default Gemini model and user-managed custom model IDs.
- **Persistent chat history** backed by Room, including threads, messages, reasoning content, and custom models.
- **Rich markdown rendering** for headings, paragraphs, nested lists, blockquotes, dividers, links, inline styles, tables, and code blocks.
- **Syntax-highlighted code** using the Highlights engine for readable technical answers.
- **Image attachment support** for multimodal conversations.
- **Settings built into the app** for API key storage, theme mode, color selection, and custom model management.
- **Material You dynamic color** by default on supported Android versions, with curated fallback accent palettes.

## Design System

EchoFlow follows a Material 3 Expressive direction: tactile, shaped, and responsive, but still calm enough for long chat sessions.

### Chat Surface

The chat screen uses a floating transparent top bar and a floating input toolbar so the message list can breathe across the full screen. Messages are padded around those floating elements, keeping the latest response readable without wasting vertical space.

Assistant responses are presented as full-width readable content, while user messages keep a distinct authored-message shape. The empty state, assistant identity, drawer header, and launcher assets share the same EchoFlow brand mark for a cohesive visual system.

### Motion And Shape

Interactive controls use expressive shape morphs, springy press feedback, and Material shape primitives from `androidx.graphics.shapes`. Buttons and icon surfaces feel alive without turning the UI into decoration.

### Color

The theme defaults to Android wallpaper-derived dynamic color where available. EchoFlow also includes curated accent options for users who want a specific mood. Color roles are applied across containers, drawers, controls, and message surfaces instead of only tinting buttons.

### Markdown Experience

AI answers often include tables, code, nested lists, explanations, and links. EchoFlow treats markdown as a first-class reading experience:

- finished assistant messages use the rich renderer
- streaming messages use the same parsing path to avoid restyling jumps
- code blocks are selectable, horizontally scrollable, and syntax highlighted
- tables wrap content instead of clipping it

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 Expressive |
| Architecture | ViewModel, StateFlow, Compose state |
| AI Provider | OpenRouter chat completions |
| Networking | OkHttp, Retrofit |
| JSON | Moshi |
| Persistence | Room |
| Preferences | DataStore / shared settings repository |
| Images | Coil |
| Markdown | Custom Compose markdown renderer plus Mike Penz markdown dependencies |
| Code Highlighting | `dev.snipme:highlights` |
| Design Utilities | `androidx.graphics:graphics-shapes` |
| Testing Tooling | JUnit, AndroidX test, Robolectric, Roborazzi |

## Project Structure

```text
app/src/main/java/com/echoflow
├── data        # Room entities/DAOs, settings storage, OpenRouter service
├── ui          # ViewModels and app state
├── ui/components
│   ├── reusable expressive UI pieces
│   └── markdown rendering
├── ui/screens  # Chat and settings screens
└── ui/theme    # Material Expressive theme, color, shape, motion helpers
```

## Core Features

### Conversations

EchoFlow stores chat threads locally and keeps each conversation's scroll state independent. Switching between chats does not inherit the previous chat's offset, and active streams stay pinned only when the user is already near the bottom.

### Models

The app ships with a simple default model and lets users add their own OpenRouter model IDs from Settings. This keeps the picker clean while still supporting power users who want to bring specific providers or experimental models.

### Reasoning

For reasoning-capable models, EchoFlow requests reasoning tokens and separates them from the final answer. The reasoning panel can expand while the model is thinking and collapse once the answer begins, keeping the main message clean.

### Web Search (Auto)

EchoFlow supports OpenRouter's web search via a simple toggle in Settings. When enabled, the **Auto** engine is used:

- The model autonomously decides if and when to search the web based on your prompt.
- If the AI provider supports native search (OpenAI, Anthropic, xAI), the model uses it directly at **provider rates**.
- Otherwise, web search falls back to **Exa** at **$0.005/request** (up to 10 results; +$0.001 per extra result).
- Exa combines keyword and embeddings-based search for high-quality results.

No separate search engine configuration is needed — the toggle is all you see. Enable it and the model handles the rest.

### Appearance

Users can choose light, dark, system, dynamic wallpaper color, or curated accent palettes. The theme is applied through Compose and Material 3 role colors so the whole interface moves together.

## Libraries

EchoFlow is built with:

- `androidx.compose` for declarative native UI
- `androidx.compose.material3` for Material 3 and expressive components
- `androidx.graphics.shapes` for morphing polygon-based UI shapes
- `androidx.room` for local chat persistence
- `androidx.datastore` for app preferences
- `okhttp` and `retrofit` for OpenRouter networking
- `moshi` for JSON parsing and adapters
- `coil-compose` for image loading
- `dev.snipme:highlights` for code syntax highlighting
- `com.mikepenz:multiplatform-markdown-renderer` modules for markdown support
- `roborazzi` and AndroidX test libraries for visual and unit test infrastructure

## License

EchoFlow is released under the MIT License. See [LICENSE.txt](LICENSE.txt) for details.
