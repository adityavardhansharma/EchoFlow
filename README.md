<div align="center">
<img src="logo1.png" alt="EchoFlow" width="88" />

# EchoFlow

### A privacy-first Android AI workspace

Chat, local models, custom endpoints, web search, deep research, agents, artifacts, and image/video generation — no backend, no account, no telemetry.

[![Release](https://img.shields.io/github/v/release/adityavardhansharma/EchoFlow?style=flat-square&color=000000&label=release)](https://github.com/adityavardhansharma/EchoFlow/releases/latest)
[![License](https://img.shields.io/badge/license-MIT-000000?style=flat-square)](LICENSE.txt)
[![Platform](https://img.shields.io/badge/platform-Android%2024%2B-000000?style=flat-square)](#)
[![Kotlin](https://img.shields.io/badge/kotlin-2.x-000000?style=flat-square)](#)

**[Download the latest APK →](https://github.com/adityavardhansharma/EchoFlow/releases/latest)**

</div>

<br/>

## What it is

EchoFlow is a native Android app for talking to AI models — your way. There's no EchoFlow server sitting in the middle: you bring your own API keys, point it at your own Ollama box, or skip the network entirely and run a model on your phone. Nothing you type gets logged anywhere except your own device.

It started as a chat app and grew into a small workspace: web search, background research, structured data extraction, a controllable browser, generated documents, and a few ways to make multiple models work together.

## Why people use it

| | |
|---|---|
| **Nothing leaves your control** | No EchoFlow backend, no account, no analytics. Conversations, keys, and settings live on your device. |
| **Any model you want** | OpenRouter, OpenAI, Claude, Gemini, Cerebras, a local Ollama server, any OpenAI-compatible endpoint, or fully offline on-device models. |
| **More than chat** | Web search, deep research with citations, structured data extraction, browser automation, and document generation, built around whichever model you're using. |
| **Models working together** | Have one model consult a stronger one mid-answer, run several models in parallel and let a judge synthesize the results, or hand a model its own tools and a worker to delegate to. |

## Quick start

```text
1. Install the APK from Releases.
2. Open Settings and connect a model:
     - Cloud models    -> OpenRouter
     - Local models     -> on-device catalog
     - Anything else    -> Echo Labs -> Custom API Endpoint
3. Start chatting.
```

No keys are required just to install and look around — on-device models work fully offline.

## Connecting a model

| Provider | Where to set it up | Attachments |
|---|---|---|
| OpenRouter | Settings → Cloud models | Images/PDFs, depending on the model |
| OpenAI · Claude · Gemini | Echo Labs → Custom API Endpoint → Direct Cloud APIs | Images and PDFs |
| Cerebras | Echo Labs → Custom API Endpoint → Direct Cloud APIs | Images on Gemma-family models only |
| Ollama (local/LAN) | Echo Labs → Custom API Endpoint → Ollama API | Per-model toggle |
| OpenAI-compatible (LM Studio, Jan, vLLM, LocalAI…) | Echo Labs → Custom API Endpoint | Per-model toggle |
| On-device (LiteRT / MediaPipe) | Settings → Local models | `.litertlm` models only |

Web search (Exa, Parallel, Firecrawl) and OpenRouter's own server-side search work across every provider above except where noted.

## What you can do with it

**Chat** — streaming responses, markdown, code highlighting, reasoning traces, citations, and model switching mid-conversation.

**Web search** — toggle it per message or set a default. OpenRouter's search only works with OpenRouter models; Exa, Parallel, and Firecrawl work with anything.

**Deep research** — a background mode for questions that need real investigation. Runs notify you of progress, survive interruption, and come back as a cited report with sections and tables.

**Data Agent** — point it at a page or task and get structured output (prices, specs, contacts) instead of prose, with a visible credit budget.

**Browser Flow** — a live browser session that chat can drive across multiple turns, with confirmation prompts before it visits a new domain or sends anything.

**Artifacts** — generate and revise self-contained pages, reports, and documents, versioned as you iterate.

**Image generation** — describe an image, then edit it conversationally ("make the sky purple"). Runs on OpenRouter, or entirely on-device with a downloaded diffusion model.

**Video generation** — describe a short clip and get it back in the conversation. Rendering takes minutes, so it keeps going with the app closed and picks itself back up if the app is killed mid-render. You choose the shape; the model chooses the length. See [docs/video-generation.md](docs/video-generation.md).

**Echo Adviser** — let your model call in a stronger or more specialized model mid-answer when it's stuck.

**Echo Fusion** — run several models on the same prompt and have a judge model compare and merge their answers.

**Echo Agents** — give a model its own search/fetch tools plus a cheaper worker model to delegate sub-tasks to.

## Running models on-device

EchoFlow can run models entirely offline using LiteRT-LM and MediaPipe:

- A curated catalog of mobile-ready models
- Hugging Face search for `.task` and `.litertlm` files
- Importing your own model files
- Token support for gated Hugging Face models
- No internet connection or API key required once a model is downloaded

## Built with

Kotlin and Jetpack Compose (Material 3 Expressive), targeting Android 24+. Networking via OkHttp/Retrofit, persistence via Room, on-device inference via LiteRT-LM and MediaPipe GenAI, search and research via Exa/Parallel/Firecrawl/OpenRouter, markdown rendering via a custom Compose renderer.

```text
app/src/main/java/com/echoflow
├── data           # Room entities/DAOs, provider services, settings, research, agents, browser, artifacts, image/video generation, local models
├── ui             # ViewModels and app state
├── ui/components  # cards, reports, markdown, browser/data/research result UI
├── ui/screens     # chat and settings screens
└── ui/theme       # color, shape, motion
```

## Building from source

```bash
./gradlew assembleDebug
```

No keys are needed to build — everything is configured at runtime in Settings.

## License

MIT — see [LICENSE.txt](LICENSE.txt).
