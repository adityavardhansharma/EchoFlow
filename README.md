<div align="center">

<img src="logo1.png" alt="EchoFlow logo" width="96" />

# EchoFlow

**A privacy-first Android AI workspace** — chat, local models, custom endpoints, web search, deep research, agents, browser control, and artifacts, all in one app.

<br/>

<img src="https://img.shields.io/badge/Kotlin-2.x-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
<img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
<img src="https://img.shields.io/badge/Android-API%2024+-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
<img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge" alt="License" />

<br/>

<img src="https://img.shields.io/badge/🔒_No_backend-BYOK_only-0f172a?style=flat-square" alt="No backend" />
<img src="https://img.shields.io/badge/📡_Telemetry-none-0f172a?style=flat-square" alt="No telemetry" />
<img src="https://img.shields.io/badge/📴_Offline-on--device_models-0f172a?style=flat-square" alt="Offline capable" />

<br/><br/>

### 🤖 Bring your own provider

<img src="https://img.shields.io/badge/OpenRouter-routes%20100%2B%20models-6366F1?style=flat-square&logo=router&logoColor=white" alt="OpenRouter" />
<img src="https://img.shields.io/badge/OpenAI-GPT-10A37F?style=flat-square&logo=openai&logoColor=white" alt="OpenAI" />
<img src="https://img.shields.io/badge/Anthropic-Claude-D97757?style=flat-square&logo=anthropic&logoColor=white" alt="Claude" />
<img src="https://img.shields.io/badge/Google-Gemini-4285F4?style=flat-square&logo=googlegemini&logoColor=white" alt="Gemini" />
<img src="https://img.shields.io/badge/Cerebras-fast%20inference-F23B36?style=flat-square&logo=cloudflare&logoColor=white" alt="Cerebras" />
<img src="https://img.shields.io/badge/Ollama-local%20%2F%20LAN-1a1a1a?style=flat-square&logo=ollama&logoColor=white" alt="Ollama" />
<img src="https://img.shields.io/badge/OpenAI--compatible-LM%20Studio%20·%20Jan%20·%20vLLM-7c3aed?style=flat-square&logo=fastapi&logoColor=white" alt="OpenAI compatible" />
<img src="https://img.shields.io/badge/On--device-LiteRT%20%2F%20MediaPipe-FF6F00?style=flat-square&logo=tensorflow&logoColor=white" alt="On-device" />

<br/><br/>

**[⬇️ Download the latest APK](https://github.com/adityavardhansharma/EchoFlow/releases/latest)**

</div>

---

## What is EchoFlow?

EchoFlow is a native Android AI app built around one idea: **your device stays in charge.** There's no EchoFlow backend, no account system, and no telemetry. Connect OpenRouter, a direct provider API, an Ollama server on your network, an OpenAI-compatible local server, or run fully on-device models — your keys, your data, your call.

It started as a chat app and grew into a small AI workspace: chat, web search, deep research, structured data extraction, browser automation, generated artifacts, multi-model panels, and agents — all inside one Material 3 Expressive interface.

## Table of Contents

- [Privacy model](#-privacy-model)
- [Features at a glance](#-features-at-a-glance)
- [Provider matrix](#-provider-matrix)
- [Getting started](#-getting-started)
- [Chat modes explained](#-chat-modes-explained)
- [On-device models](#-on-device-models)
- [Tech stack](#%EF%B8%8F-tech-stack)
- [Project structure](#-project-structure)
- [Build from source](#-build-from-source)
- [License](#-license)

---

## 🔐 Privacy Model

| | |
|---|---|
| **No backend** | EchoFlow talks directly to the providers you configure — nothing routes through an EchoFlow server. |
| **No account, no telemetry** | There's no cloud account, no analytics pipeline, no hidden sync. |
| **Local storage** | Conversations, settings, run history, and model metadata all live on-device. |
| **BYOK** | Provider keys are entered at runtime and stored locally on your device. |
| **Offline path** | On-device models run with zero internet connection and zero API keys. |
| **Explicit network use** | Search, browser automation, and provider calls only run for tools you've turned on. |

## ✨ Features at a Glance

<details open>
<summary><b>Models & providers</b></summary>
<br/>

- **OpenRouter** — live model directory with pricing/context metadata, custom model picker, image/PDF-capable workflows
- **Direct cloud APIs** — OpenAI, Claude, Gemini, and Cerebras each get their own setup page, key, model fetch, and chat-picker entry
- **Cerebras** — streams through Cerebras's OpenAI-compatible endpoint; Gemma-family models support images, GPT-OSS/GLM are text-only
- **Ollama** — connect to a local/LAN server, fetch models, test the connection, opt into image/PDF support per model
- **OpenAI-compatible servers** — LM Studio, Jan, vLLM, LocalAI, and similar, with model-fetch fallbacks
- **On-device** — curated LiteRT/MediaPipe catalog, Hugging Face search for `.task`/`.litertlm` bundles, local file import, gated-model tokens

</details>

<details open>
<summary><b>Search & research</b></summary>
<br/>

- **Web search** — OpenRouter server-side search for OpenRouter models, plus Exa, Parallel, and Firecrawl client-side search that works across any provider
- **Deep Research** — background research runs via Exa, Parallel, Firecrawl, or OpenRouter models, with notifications, citations, and resume-after-interruption
- **Data Agent** — Firecrawl-powered structured extraction for prices, specs, contacts, and other data-heavy tasks
- **Browser Flow** — a live, chat-controlled Firecrawl browser session with domain/send confirmations

</details>

<details open>
<summary><b>Power features</b></summary>
<br/>

- **Artifacts** — generate and revise self-contained web pages, reports, and documents, with offline-safe handling
- **Echo Adviser** — your main model can consult a stronger or domain-specific OpenRouter advisor mid-answer
- **Echo Fusion** — run a panel of OpenRouter models plus a judge model to compare and synthesize answers
- **Echo Agents** — give the main model web search/fetch tools and a cheaper worker model to delegate to

</details>

<details open>
<summary><b>Interface</b></summary>
<br/>

- Streaming markdown, tables, code highlighting, reasoning traces, citations, and search progress cards
- Material 3 Expressive design — dynamic color, accent themes, shaped controls, adaptive tablet layouts

</details>

## 🔌 Provider Matrix

| Provider | Setup location | Model source | Search support | Attachments |
|---|---|---|---|---|
| OpenRouter | Settings → Cloud models | OpenRouter directory + manual | OpenRouter search, Exa, Parallel, Firecrawl | Images/PDFs where the model supports them |
| OpenAI | Echo Labs → Custom API Endpoint → Direct Cloud APIs → OpenAI | `/v1/models` + manual | Exa, Parallel, Firecrawl | Images/PDFs enabled |
| Claude | Echo Labs → Custom API Endpoint → Direct Cloud APIs → Claude | Anthropic models endpoint + manual | Exa, Parallel, Firecrawl | Images/PDFs enabled |
| Gemini | Echo Labs → Custom API Endpoint → Direct Cloud APIs → Gemini | Gemini models endpoint + manual | Exa, Parallel, Firecrawl | Images/PDFs enabled |
| Cerebras | Echo Labs → Custom API Endpoint → Direct Cloud APIs → Cerebras | Cerebras `/v1/models` + manual | Exa, Parallel, Firecrawl | Images for Gemma-family models; PDFs off |
| Ollama | Echo Labs → Custom API Endpoint → Ollama API | `/api/tags` + manual | Exa, Parallel, Firecrawl | User-controlled toggles |
| OpenAI-compatible | Echo Labs → Custom API Endpoint → OpenAI-Compatible API | `/v1/models`, `/models`, `/api/v1/models` + manual | Exa, Parallel, Firecrawl | User-controlled toggles |
| On-device | Settings → Local models | Curated catalog, Hugging Face search, file import | Exa, Parallel, Firecrawl | Images for `.litertlm`; PDFs off |

## 🚀 Getting Started

1. **Install** the latest APK from [GitHub Releases](https://github.com/adityavardhansharma/EchoFlow/releases/latest).
2. **Pick how you want to run models:**
   - OpenRouter → Settings → Cloud models
   - On-device → Settings → Local models
   - Direct/custom providers → Settings → Echo Labs → Custom API Endpoint
3. **Add search keys** (optional) in Settings → Web search for Exa, Parallel, or Firecrawl.
4. **Turn on extra features** in Echo Labs — Browser Flow, Data Agent, Echo Adviser, Echo Fusion, Echo Agents, Artifacts, Custom API Endpoint.

No provider keys are required just to install and explore the app.

## 💬 Chat Modes Explained

<details>
<summary><b>Chat</b> — the default mode</summary>
<br/>
Streaming output, markdown, reasoning traces, citations, image/PDF attachments where available, and model switching from the in-chat picker.
</details>

<details>
<summary><b>Web Search</b></summary>
<br/>
Toggle the Web search chip from the <code>+</code> menu, or set a default in Settings. OpenRouter's server-side search only works with OpenRouter models; Exa, Parallel, and Firecrawl are client-side tools that work across OpenRouter, direct cloud APIs, Ollama, OpenAI-compatible endpoints, and local models.
</details>

<details>
<summary><b>Deep Research</b></summary>
<br/>
A background research mode for questions that need real investigation:

- Exa Deep Lite, Deep, Deep Reasoning, and Exa Agent with effort controls
- Parallel Pro and Ultra
- Firecrawl Research
- User-added OpenRouter research models

Runs use a foreground notification with progress and cancel controls, persist through interruption, and return cited reports with sections, tables, and source cards.
</details>

<details>
<summary><b>Data Agent</b></summary>
<br/>
For extracting structured data instead of prose. Uses Firecrawl, shows a live credit budget, and renders results as cards and tables.
</details>

<details>
<summary><b>Browser Flow</b></summary>
<br/>
Starts a live Firecrawl browser session that chat can control across multiple turns, with session state, step tracking, domain/send confirmations, and stop/finish controls.
</details>

<details>
<summary><b>Artifacts</b></summary>
<br/>
Builds or revises self-contained generated artifacts — small web pages, reports, documents. Versioned, with an offline-safe mode for local workflows.
</details>

<details>
<summary><b>Echo Adviser</b></summary>
<br/>
Lets the active OpenRouter chat model consult a configured advisor model before finishing difficult work — useful for coding, math, research, and review-style checks.
</details>

<details>
<summary><b>Echo Fusion</b></summary>
<br/>
Runs multiple OpenRouter models as a panel, then asks a judge model to compare, synthesize, and call out disagreement or gaps.
</details>

<details>
<summary><b>Echo Agents</b></summary>
<br/>
Gives the main OpenRouter model a tool-using workflow — web search, web fetch, and a separate worker model for delegated tasks, each with a configurable tool-call budget.
</details>

## 📱 On-device Models

EchoFlow supports fully local inference through LiteRT-LM / MediaPipe:

- Curated mobile-ready model catalog
- Hugging Face search for `.task` and `.litertlm` bundles
- Import models from local storage
- Hugging Face token support for gated models
- Local inference parameters kept separate from cloud parameters
- Full offline operation — no provider key required

## 🛠️ Tech Stack

| Area | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 Expressive |
| Android | compileSdk 37 · targetSdk 36 · minSdk 24 |
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
| Testing | JUnit, AndroidX test, Robolectric, Roborazzi |

## 📁 Project Structure

```text
app/src/main/java/com/echoflow
├── data           # Room entities/DAOs, provider services, settings, research, agents, browser, artifacts, local models
├── ui             # ViewModels and app state
├── ui/components  # expressive UI, markdown, cards, reports, browser/data/research result components
├── ui/screens     # Chat and settings screens
└── ui/theme       # Material theme, color, shape, motion helpers
```

## 🏗️ Build From Source

Open the project in Android Studio and run the `app` configuration, or build a debug APK:

```bash
./gradlew assembleDebug
```

No provider keys are required to build — API keys and endpoint URLs are configured at runtime in Settings.

## 📄 License

EchoFlow is released under the MIT License. See [LICENSE.txt](LICENSE.txt) for details.
