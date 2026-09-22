<div align="center">
  <img src="logo1.png" alt="EchoFlow" width="88" />

  # EchoFlow

  ### Your AI workspace for Android

  Chat with the models you choose — on your phone, in the cloud, or on your own network.

  [![Release](https://img.shields.io/github/v/release/adityavardhansharma/EchoFlow?style=flat-square&color=000000&label=release)](https://github.com/adityavardhansharma/EchoFlow/releases/latest)
  [![License](https://img.shields.io/badge/license-MIT-000000?style=flat-square)](LICENSE.txt)
  [![Platform](https://img.shields.io/badge/platform-Android%2024%2B-000000?style=flat-square)](#)

  **[Download the latest APK →](https://github.com/adityavardhansharma/EchoFlow/releases/latest)**
</div>

## What is EchoFlow?

EchoFlow is a native Android app for chatting with AI and turning conversations into useful work. Bring your own cloud API keys, connect to a model running on your laptop or home server, or download a model and run it directly on your phone.

EchoFlow has no EchoFlow backend, account, or telemetry. Your conversations, settings, and API keys are stored locally on your device. When you use a cloud or network model, the relevant request goes directly to the provider or endpoint you configured.

## Choose where your model runs

### On-device models — models running on your phone

Run supported language models locally on Android using LiteRT-LM, MediaPipe GenAI, or llama.cpp support. Once a model is downloaded, it can generate responses without an API key or internet connection.

- Browse the built-in catalog of mobile-ready models.
- Search Hugging Face for supported model files.
- Import your own `.task` or `.litertlm` model files.
- Use Hugging Face access tokens for gated models.
- Keep local-model conversations on the device when cloud access is disabled.

On-device inference depends on the model, phone hardware, available memory, and Android version. Local models may be slower or less capable than large cloud models, but they are useful when privacy or offline access matters most.

### Cloud models — provider APIs

Connect your own API keys for cloud models from the model and provider settings. Supported integrations include:

- OpenRouter
- OpenAI
- Anthropic Claude
- Google Gemini
- Cerebras
- xAI
- Sarvam

Provider model lists can be fetched where supported, and you can enter a model ID manually when a provider exposes a model that is not in the catalog. Attachments and tool support depend on the selected provider and model.

### Network models — models running on your network

Use a model hosted on another computer and reach it from your phone over Wi-Fi or any reachable network endpoint.

- **Ollama** — connect to an Ollama server by base URL and select its models.
- **OpenAI-compatible endpoints** — connect to LM Studio, Jan, vLLM, LocalAI, or another compatible server.
- Configure the endpoint and credentials in **Settings → Echo Labs → Custom API Endpoint**.

This lets a phone act as the chat client while inference runs on a desktop, workstation, NAS, or home server that you control.

## Chat

The main Chat surface supports:

- Streaming responses and model switching.
- Markdown, code blocks, syntax highlighting, and LaTeX.
- Images, PDFs, documents, and other supported attachments.
- Voice input and dictation inside EchoFlow.
- Web search, citations, and tool-enabled conversations.
- Separate conversation history for each chat.
- Local, cloud, and network-hosted models in the same app.

## Workspaces and creative tools

### Projects

Projects are durable workspaces for a specific subject or task. A project can contain:

- Multiple related chats.
- Shared project instructions.
- Uploaded documents and files.
- A focused home for continuing work over time.

### Artifacts

Ask a model to create and revise useful output instead of leaving everything as plain chat text. Artifacts can be opened in their own workspace, previewed or read, inspected as source, and revisited through version history.

Use Artifacts for model-generated pages, reports, documents, and other self-contained work products.

### Imagine

Imagine is a separate surface for image and video generation. Describe what you want, choose a model and format, then refine the result conversationally.

- Generate and edit images.
- Generate short videos where supported by the selected model.
- Choose supported aspect ratios, shapes, and generation options.
- Keep generated media organized separately from chat history.

See [docs/modes.md](docs/modes.md) and [docs/video-generation.md](docs/video-generation.md) for implementation details.

## Research and web tools

### Web search

Search can be enabled per message or configured as a default. Available search providers include:

- EchoCrawl
- OpenRouter search
- Exa
- Parallel
- Firecrawl

Some providers work with any selected model, while provider-native search is limited to compatible cloud models.

### Deep Research

Deep Research runs a longer, background investigation and returns a structured, cited report. Progress is visible in the app, and interrupted work can be recovered when the app is opened again.

### Data Agent

The Data Agent is designed for structured answers from pages and files — for example prices, specifications, contacts, or other repeated fields — instead of an unstructured paragraph.

### Browser Flow

Browser Flow gives chat access to a live remote browser session. The model can navigate and interact with pages while EchoFlow shows the session and asks for confirmation before sensitive actions such as entering a new domain or sending information.

## Models working together

EchoFlow can use more than one model for a task:

- **Echo Adviser** — ask a stronger or specialized advisor model for help during a difficult answer.
- **Echo Fusion** — send a prompt to multiple models and have a judge compare and synthesize their responses.
- **Echo Agents** — give a model search and fetch tools plus a worker model for delegated subtasks.

These capabilities are most useful with tool-capable cloud or network models. Availability and cost depend on the providers and models you configure.

## EchoOCR

EchoFlow includes document and file extraction for project files and attachments. **EchoOCR** identifies content extracted from scanned or image-based documents so it can be read, searched, and brought into a conversation.

Supported file handling can include plain-text reading, document parsing, PDF extraction, spreadsheet and structured-file workflows, and on-device OCR where applicable. The exact result depends on the file type and extraction path.

## Echo Labs beta features

Echo Labs contains experimental and advanced features that are still being developed.

### System-wide dictation — beta

System-wide dictation lets you dictate into other Android apps, not only EchoFlow. Enable it under **Settings → Echo Labs → System-wide dictation** and grant the Android accessibility/input permission requested by the app.

It uses the configured dictation provider and model independently from the chat model. Because it operates across the device and depends on Android permissions, it is currently labelled **beta**.

### Memory — beta

Memory is an optional, bring-your-own-key feature under **Settings → Echo Labs → Memory**. When enabled, eligible conversations can contribute lasting preferences, facts, and project context to your connected memory service.

- Memory is opt-in.
- You can control whether memory is used in conversations.
- Local-model chats stay on the device when cloud memory access is disabled.
- The app does not automatically upload chats without the relevant consent and settings.
- Never store passwords, API keys, or other secrets in memory-enabled conversations.

Memory is currently labelled **beta** because its controls, providers, and learning behavior are still evolving.

## Privacy and control

- No EchoFlow account is required.
- No EchoFlow server sits between you and your configured model provider.
- API keys are configured in the app and stored locally.
- On-device models can work offline after download.
- Network and cloud requests are controlled by the providers and endpoints you enable.
- Local chat history and workspace data are stored on the device.

Always review the privacy, retention, and billing policies of any cloud, search, memory, or hosted-model provider you connect.

## Quick start

1. Install the [latest APK](https://github.com/adityavardhansharma/EchoFlow/releases/latest).
2. Open **Settings → Models**.
3. Choose one of:
   - **On-device** to download a model for your phone.
   - **Cloud models** to configure OpenRouter or another supported provider.
   - **Echo Labs → Custom API Endpoint** to connect Ollama or an OpenAI-compatible server.
4. Select a model and start a chat.

No API key is needed to explore the app or run a downloaded on-device model.

## Build from source

EchoFlow is a single-module Kotlin and Jetpack Compose Android app.

Requirements:

- JDK 21
- Android SDK with the platform required by `compileSdk` in `app/build.gradle.kts`
- Android SDK platform-tools and compatible build-tools

Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

Run unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

No API keys are required to build the project. Runtime provider keys are entered in the app or supplied through the local development configuration described in [`.env.example`](.env.example).

See [docs/architecture.md](docs/architecture.md) for the codebase structure and [CONTRIBUTING.md](CONTRIBUTING.md) for contribution and verification guidance.

## License

MIT — see [LICENSE.txt](LICENSE.txt).
