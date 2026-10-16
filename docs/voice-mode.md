# Voice mode

Live, two-way voice conversations powered by **ElevenLabs Agents**, using only the user's
ElevenLabs key.

## How a user meets it

- **Entry.** When the ElevenLabs key is on and has authenticated, an empty composer shows a
  waveform button where Send sits. As soon as the user types or attaches anything it becomes Send
  again.
- **The stage.** Voice mode replaces the message list with an animated orb, light beams rising from
  the bottom (different lengths, following the voice level), a status line (Listening, Thinking,
  Speaking), and live captions. Typed and spoken turns aren't listed while the call is open.
- **Composer.** It has a mic toggle, a "Type to talk…" field, Send (with text) or a speaker toggle,
  and End. Typing tells the agent to hold off talking. If the mic permission is refused, the
  session runs typing-only and the agent still answers out loud.
- **Ending.** End, Back, the notification's End action, or 5 minutes of quiet (with a 30 s
  countdown) closes the call. A loader shows while the conversation is saved. The chat then shows
  every turn as normal messages, so typing can continue with the chat's own model.

## Settings (Settings → Voice mode)

The page appears in the hub only once the key has authenticated.

- **Plan.** The ElevenLabs tier and credits left come from `GET /v1/user/subscription`. A key
  without the user-read permission still counts as valid, and the plan then shows as unknown.
- **Voice.** A searchable picker covers *My voices* (`GET /v2/voices`) and the public *Voice
  Library* (`GET /v1/shared-voices`), with previews. Picking a library voice copies it into My
  Voices first. Free plans can't use library voices with agents, so those rows are disabled there.
- **Voice style.**
  - **Expressive** (the default) is `eleven_v4_turbo` with expressive mode. It falls back to
    `eleven_v3_conversational` if the account rejects v4.
  - **Fast** is `eleven_flash_v2_5`.
  - Professional voice clones always use Fast to keep their character.
- **Model.** The LLM comes from `GET /v1/convai/llm/list`, with deprecated models and dated
  snapshots hidden. It is the only model voice mode uses.
- **Language.** This is the agent's language. The prompt still lets the agent follow the user if
  they switch mid-chat.

## How it works

- **Agent.** `VoiceAgentProvisioner` keeps one "EchoFlow Voice" agent per key in sync with the
  settings. It creates the agent once and PATCHes it when the settings hash changes, so an
  unchanged config costs no request. If the agent was deleted on ElevenLabs, it recreates it.
- **Session.** `VoiceSession` connects to the signed WebSocket URL, sends
  `conversation_initiation_client_data` (the prompt with the chat so far, the LLM, the voice and the
  language), and then:
  - streams 16 kHz PCM up, with echo cancellation and noise suppression;
  - plays the agent's PCM down on a voice-communication track;
  - answers pings, handles interruptions, and sends typed text as `user_message`.
- **Saving.** `VoiceModeController` saves each finished turn into the chat as it happens, so a
  crash loses nothing. After the call it fetches ElevenLabs' own transcript
  (`GET /v1/convai/conversations/{id}`) and rewrites the session's turns if they differ.
- **Background.** `VoiceModeService` (a microphone foreground service) keeps the call alive with
  the screen off.
- **Spend.** Each call is recorded in the Spend ledger as *Voice mode*. The amount is what
  ElevenLabs charged when it reports it, otherwise an estimate from the call length.
