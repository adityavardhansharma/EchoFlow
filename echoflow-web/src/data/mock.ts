export interface Thread {
  id: string;
  title: string;
  group: "pinned" | "today" | "yesterday" | "week" | "older";
  pinned?: boolean;
}

export interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  /** Markdown-ish assistant blocks */
  reasoning?: string;
  citations?: { label: string; url: string }[];
}

export interface Conversation {
  id: string;
  messages: Message[];
}

export const threads: Thread[] = [
  { id: "t1", title: "Kotlin coroutines deep dive", group: "pinned", pinned: true },
  { id: "t2", title: "Echo Fusion comparison", group: "pinned", pinned: true },
  { id: "t3", title: "Privacy model architecture", group: "today" },
  { id: "t4", title: "Imagine: sunset cityscape", group: "today" },
  { id: "t5", title: "Ollama setup on LAN", group: "yesterday" },
  { id: "t6", title: "Research: on-device LLMs 2026", group: "week" },
  { id: "t7", title: "Browser Flow test session", group: "week" },
  { id: "t8", title: "Custom OpenAI endpoint", group: "older" },
];

export const conversations: Record<string, Conversation> = {
  t3: {
    id: "t3",
    messages: [
      {
        id: "m1",
        role: "user",
        content: "How does EchoFlow keep conversations private compared to cloud chat apps?",
      },
      {
        id: "m2",
        role: "assistant",
        content:
          "EchoFlow is built around **local-first privacy**. There is no EchoFlow backend sitting between you and your models — conversations, API keys, and settings live on your device.\n\nKey differences from typical cloud chat:\n\n1. **No account required** — nothing to sign up for or sync to a vendor cloud.\n2. **Bring your own keys** — you choose OpenRouter, OpenAI, Claude, or run fully offline with on-device models.\n3. **No telemetry** — the app doesn't log what you type to EchoFlow servers.\n4. **Web search is opt-in** — Exa, Parallel, or Firecrawl only when you enable them per message.",
        reasoning:
          "User is asking about privacy positioning. I'll contrast with cloud-first apps and highlight EchoFlow's architecture: no backend, local storage, BYOK, optional search providers.",
        citations: [
          { label: "EchoFlow README — privacy", url: "#" },
          { label: "Local models docs", url: "#" },
        ],
      },
      {
        id: "m3",
        role: "user",
        content: "Can I use it completely offline?",
      },
      {
        id: "m4",
        role: "assistant",
        content:
          "Yes. Install an on-device model from **Settings → Local models** (LiteRT / MediaPipe `.litertlm` files). Chat works fully offline — no keys, no network. Imagine and cloud-only features obviously need connectivity, but core conversation doesn't.",
      },
    ],
  },
};

export const defaultConversationId = "t3";

export const emptyStateSuggestions = [
  "Explain quantum entanglement simply",
  "Draft a privacy policy outline",
  "Compare Kotlin Flow vs RxJava",
  "Ideas for a weekend hike",
];

export const settingsNav = [
  {
    id: "appearance",
    title: "Appearance",
    subtitle: "Theme & accent color",
    href: "/settings/appearance",
    icon: "palette",
  },
  {
    id: "cloud",
    title: "Cloud models",
    subtitle: "OpenRouter catalog & keys",
    href: "/settings",
    icon: "cloud",
  },
  {
    id: "search",
    title: "Web search",
    subtitle: "Exa, Parallel, Firecrawl",
    href: "/settings",
    icon: "search",
  },
  {
    id: "local",
    title: "Local models",
    subtitle: "On-device LiteRT catalog",
    href: "/settings",
    icon: "device",
  },
  {
    id: "labs",
    title: "Echo Labs",
    subtitle: "Custom endpoints & experiments",
    href: "/settings",
    icon: "labs",
  },
  {
    id: "data",
    title: "Data & storage",
    subtitle: "Export, clear conversations",
    href: "/settings",
    icon: "data",
  },
];

export const models = [
  { id: "claude", label: "Claude Sonnet 4", provider: "Anthropic" },
  { id: "gpt", label: "GPT-4.1", provider: "OpenAI" },
  { id: "gemini", label: "Gemini 2.5 Pro", provider: "Google" },
  { id: "local", label: "Gemma 3 4B", provider: "On device" },
];

export const activeModelId = "claude";
