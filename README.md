<div align="center">
  <img src="logo1.png" alt="EchoFlow logo" width="96" />
  <h1>EchoFlow</h1>
  <p><strong>A privacy-first Android AI workspace for chat, local models, custom endpoints, web search, research, agents, browser control, and artifacts.</strong></p>

  <p>
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.x-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
    <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
    <img alt="Android" src="https://img.shields.io/badge/Android-API%2024+-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
    <img alt="Privacy" src="https://img.shields.io/badge/Privacy-No%20backend%20%7C%20BYOK-111827?style=for-the-badge" />
  </p>

  <p>
    <img alt="OpenRouter" src="https://img.shields.io/badge/OpenRouter-cloud%20models-111827?style=flat-square" />
    <img alt="OpenAI" src="https://img.shields.io/badge/OpenAI-direct%20API-10A37F?style=flat-square&logo=openai&logoColor=white" />
    <img alt="Claude" src="https://img.shields.io/badge/Claude-direct%20API-D97757?style=flat-square" />
    <img alt="Gemini" src="https://img.shields.io/badge/Gemini-direct%20API-4285F4?style=flat-square&logo=googlegemini&logoColor=white" />
    <img alt="Cerebras" src="https://img.shields.io/badge/Cerebras-direct%20API-E23B3B?style=flat-square" />
    <img alt="Ollama" src="https://img.shields.io/badge/Ollama-local%20%2F%20LAN-111827?style=flat-square" />
    <img alt="OpenAI compatible" src="https://img.shields.io/badge/OpenAI--compatible-LM%20Studio%20%7C%20Jan%20%7C%20vLLM-6366F1?style=flat-square" />
  </p>

  <p>
    <img alt="On-device" src="https://img.shields.io/badge/On--device-LiteRT%20%2F%20MediaPipe-FF6F00?style=flat-square" />
    <img alt="Search" src="https://img.shields.io/badge/Search-OpenRouter%20%7C%20Exa%20%7C%20Parallel%20%7C%20Firecrawl-0F766E?style=flat-square" />
    <img alt="Research" src="https://img.shields.io/badge/Deep%20Research-Exa%20%7C%20Parallel%20%7C%20Firecrawl-7C3AED?style=flat-square" />
    <img alt="License" src="https://img.shields.io/badge/License-MIT-green?style=flat-square" />
  </p>

  <p>
    <a href="https://github.com/adityavardhansharma/EchoFlow/releases/latest"><strong>Download from GitHub Releases</strong></a>
  </p>
</div>

## Overview

EchoFlow is a native Android AI app built around one idea: your device should stay in charge. It is **bring-your-own-key**, **local-first**, and has **no EchoFlow backend, no account system, and no telemetry**. You can use OpenRouter, direct provider APIs, an Ollama server on your network, an OpenAI-compatible local server, or fully on-device models.

It has grown from a chat app into a small AI workspace: normal chat, web search, Deep Research, structured data extraction, Browser Flow, generated artifacts, Echo Adviser, Echo Fusion, and Echo Agents all live in the same Material 3 Expressive interface.

## Privacy Model

- **No backend:** EchoFlow talks directly to the providers you configure.
- **No account or telemetry:** there is no EchoFlow cloud account, analytics pipeline, or hidden sync.
- **Local storage:** conversations, settings, runs, and downloaded model metadata live on-device.
- **BYOK:** provider keys are entered at runtime and stored on your device.
- **Offline path:** on-device models can run without internet or API keys.
- **Network tools are explicit:** search, browser, data extraction, and provider calls use the services you turn on.

## Current Features

- **OpenRouter cloud models:** live model directory, pricing/context metadata, custom model picker, image/PDF-capable cloud workflows.
- **Custom API Endpoint:** prerelease provider area for direct cloud APIs, Ollama, and OpenAI-compatible endpoints.
- **Direct cloud APIs:** OpenAI, Claude, Gemini, and Cerebras each get their own setup page, toggle, API key, model fetch, manual model entry, logo badge, and chat-picker model list.
- **Cerebras support:** streams through the Cerebras OpenAI-compatible endpoint; Gemma-family Cerebras models can use images, while GPT OSS/GLM are treated as text-only and PDFs are disabled.
- **Ollama:** connect to a local or LAN Ollama server, fetch models, test the connection, and opt into image/PDF support only when your model supports it.
- **OpenAI-compatible servers:** connect LM Studio, Jan, vLLM, LocalAI, or similar servers over the network, with model fetch fallbacks and test connection.
- **On-device models:** download curated LiteRT/MediaPipe models, search Hugging Face for `.task` and `.litertlm` bundles, import local model files, and use gated-model tokens.
- **Web search:** OpenRouter server-side search for OpenRouter models, plus Exa, Parallel, and Firecrawl client-side search that can work across cloud, custom endpoint, and local models.
- **Deep Research:** Exa, Parallel, Firecrawl, and OpenRouter-model research engines with background execution, notifications, citations, and resume after interruption.
- **Data Agent:** Firecrawl-powered structured extraction for prices, specs, lists, contacts, and other data-heavy tasks.
- **Browser Flow:** a live Firecrawl browser session controlled from chat, with domain/send confirmations and visible session controls.
- **Artifacts:** generate and revise self-contained web/document/report artifacts, with offline artifact handling for local workflows.
- **Echo Adviser:** let a main model consult a stronger or domain-specific OpenRouter advisor mid-answer.
- **Echo Fusion:** run a panel of OpenRouter models and a judge model to compare answers and produce a synthesis.
- **Echo Agents:** give the main model web search/fetch tools and a cheaper worker model it can delegate tasks to.
- **Rich chat rendering:** streaming markdown, tables, code highlighting, reasoning traces, citations, search progress cards, and result cards.
- **Material 3 Expressive UI:** dynamic color, accent themes, shaped controls, smooth transitions, and adaptive tablet-friendly layouts.

## Provider Matrix

| Provider path | Setup location | Model source | Search support | Attachments |
| --- | --- | --- | --- | --- |
| OpenRouter | Settings -> Cloud models | OpenRouter directory + manual picks | OpenRouter server search, Exa, Parallel, Firecrawl | Images/PDFs where provider model supports them |
| OpenAI direct | Echo Labs -> Custom API Endpoint -> Direct Cloud APIs -> OpenAI | `/v1/models` + manual | Exa, Parallel, Firecrawl | Images/PDFs enabled |
| Claude direct | Echo Labs -> Custom API Endpoint -> Direct Cloud APIs -> Claude | Anthropic models endpoint + manual | Exa, Parallel, Firecrawl | Images/PDFs enabled |
| Gemini direct | Echo Labs -> Custom API Endpoint -> Direct Cloud APIs -> Gemini | Gemini models endpoint + manual | Exa, Parallel, Firecrawl | Images/PDFs enabled |
| Cerebras direct | Echo Labs -> Custom API Endpoint -> Direct Cloud APIs -> Cerebras | Cerebras `/v1/models` + manual | Exa, Parallel, Firecrawl | Images for Gemma-family models; PDFs off |
| Ollama | Echo Labs -> Custom API Endpoint -> Ollama API | `/api/tags` + manual | Exa, Parallel, Firecrawl | User-controlled image/PDF toggles |
| OpenAI-compatible | Echo Labs -> Custom API Endpoint -> OpenAI-Compatible API | `/v1/models`, `/models`, `/api/v1/models` + manual | Exa, Parallel, Firecrawl | User-controlled image/PDF toggles |
| On-device | Settings -> Local models | Curated catalog, Hugging Face search, file import | Exa, Parallel, Firecrawl | Images for `.litertlm`; PDFs off |

## Getting Started

1. Install the latest APK from [GitHub Releases](https://github.com/adityavardhansharma/EchoFlow/releases/latest).
2. Choose how you want to run models:
   - **OpenRouter:** Settings -> Cloud models.
   - **On-device:** Settings -> Local models.
   - **Direct/custom providers:** Settings -> Echo Labs -> Custom API Endpoint.
3. Add optional search keys in Settings -> Web search for Exa, Parallel, or Firecrawl.
4. Turn on optional Echo Labs features such as Browser Flow, Data Agent, Echo Adviser, Echo Fusion, Echo Agents, Artifacts, and Custom API Endpoint.

## Chat Modes

### Chat

The default mode for normal conversations. It supports streaming output, markdown, reasoning traces, citations, image attachments where available, PDF attachments where available, and model switching from the in-chat picker.

### Web Search

Use the per-message Web search chip from the `+` menu or set a default in Settings. OpenRouter server-side search is only used with OpenRouter models. Exa, Parallel, and Firecrawl are client-side tools and can work with OpenRouter, direct cloud APIs, Ollama, OpenAI-compatible endpoints, and local models.

### Deep Research

A background research mode for questions that need investigation. It supports:

- Exa Deep Lite, Deep, Deep Reasoning, and Exa Agent with effort controls.
- Parallel Pro and Ultra.
- Firecrawl Research.
- User-added OpenRouter research models.

Runs use a foreground notification with progress and cancel controls, persist through interruption, and return cited reports with sections, tables, and source cards.

### Data Agent

For extracting structured data instead of prose. It uses Firecrawl, shows a live credit budget, and renders structured results as cards and tables.

### Browser Flow

Starts a live Firecrawl browser that chat can control over multiple turns. EchoFlow shows session state, steps, domain confirmations, send confirmations, stop/finish controls, and a browser workspace.

### Artifacts

Builds or revises self-contained generated artifacts such as small web pages, reports, and documents. Artifacts are versioned and can run in an offline-safe mode for local workflows.

### Echo Adviser

Lets the active OpenRouter chat model consult a configured advisor model before finishing difficult work. Useful for coding, math, research, and review-style checks.

### Echo Fusion

Runs multiple OpenRouter models as a panel, then asks a judge model to compare, synthesize, and call out disagreement or missing pieces.

### Echo Agents

Gives the main OpenRouter model a tool-using workflow with web search, web fetch, and a separate worker model for delegated tasks. Each agent has a configurable tool-call budget.

## On-device Models

EchoFlow supports local model workflows through LiteRT-LM / MediaPipe:

- Curated mobile-ready catalog.
- Hugging Face search for `.task` and `.litertlm` bundles.
- Import from storage.
- Hugging Face token support for gated models.
- Local inference parameters separate from cloud parameters.
- Offline operation with no provider key.

## Design System

EchoFlow uses Material 3 Expressive throughout:

- Dynamic Material You color plus manual accent palettes.
- Light/dark/system theme controls.
- Morphing shapes and responsive motion.
- Full-height model/search sheets.
- Grouped settings rows and per-feature pages.
- Dense but readable operational UI for settings, agents, research, and local models.
- Custom markdown renderer with code highlighting, tables, selectable code, and stable streaming layout.

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 Expressive |
| Android | compileSdk 37, targetSdk 36, minSdk 24 |
| Architecture | ViewModel, StateFlow, Compose state |
| Cloud AI | OpenRouter, OpenAI, Anthropic Claude, Google Gemini, Cerebras |
| Custom/local endpoints | Ollama, OpenAI-compatible APIs |
| On-device AI | LiteRT-LM, MediaPipe GenAI |
| Search & research | OpenRouter search, Exa, Parallel, Firecrawl |
| Browser automation | Firecrawl browser sessions |
| Background work | Foreground services, notifications, Room-backed state |
| Networking | OkHttp, Retrofit |
| JSON | Moshi |
| Persistence | Room, shared settings repository |
| Images | Coil |
| Markdown/code | Custom Compose renderer, Mike Penz markdown modules, `dev.snipme:highlights` |
| Design utilities | `androidx.graphics:graphics-shapes` |
| Testing stack | JUnit, AndroidX test, Robolectric, Roborazzi |

## Project Structure

```text
app/src/main/java/com/echoflow
├── data           # Room entities/DAOs, provider services, settings, research, agents, browser, artifacts, local models
├── ui             # ViewModels and app state
├── ui/components  # expressive UI, markdown, cards, reports, browser/data/research result components
├── ui/screens     # Chat and settings screens
└── ui/theme       # Material theme, color, shape, motion helpers
```

## Build From Source

Open the project in Android Studio and run the `app` configuration, or build a debug APK:

```bash
./gradlew assembleDebug
```

No provider keys are required to build. API keys and endpoint URLs are configured at runtime in Settings.

## License

EchoFlow is released under the MIT License. See [LICENSE.txt](LICENSE.txt) for details.
