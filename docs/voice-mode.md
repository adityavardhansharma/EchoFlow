# Voice mode

Live, two-way voice conversations powered by **ElevenLabs Agents**, the **Gemini Live API** or
the **OpenAI Realtime API**, using the user's own key for whichever one they pick. The call
screen, controls, captions and chat record are the same for all three.

## How a user meets it

- **Entry.** When a voice provider can run a call (an ElevenLabs key that has authenticated, or
  a Gemini or OpenAI key that's switched on), an empty composer shows a waveform button where
  Send sits. As soon as the user types or attaches anything it becomes Send
  again.
- **Instant start.** The call screen opens on the tap and the mic records straight away. What the
  user says while the socket connects is held and sent the moment it opens, so nobody waits on
  "Connecting…". While the waveform button shows, the agent is synced and a signed URL fetched in
  the background (free; nothing is billed until a socket opens), so the tap usually needs no
  round trip. If the user spoke before connecting, the agent skips its hello.
- **The screen ("Horizon").** The call takes the whole screen. One line across the middle is the
  call's pulse: it bends down with the user's voice (primary) and up with the agent's (tertiary),
  carries a moving bead while the agent thinks, and flashes outward when the user's turn lands.
  The agent's words sit above the line, the user's below. A header shows the voice's name, the
  call time and a sound toggle, with a quiet note for Connecting, Mic off and the idle countdown.
- **Glow.** A soft pool of light sits on the speaker's side of the line, and blurred beams rise
  from the bottom of the screen in the line's colour. While the user talks the beams stretch up to
  touch the line; while the agent talks they sink. Glows are toned down in light themes.
- **Heard chips.** Key details in what the user is saying (amounts, dates, names) rise onto the
  line as chips while they talk. `HeardChips` picks them from the live transcript on the device.
- **Controls.** Keyboard (opens a "Type to talk…" field; typing asks the agent to hold off), one
  big button in the line's colour (mic on/off in primary; Stop in tertiary while the agent talks),
  and End. While the agent talks, a tap anywhere also stops it and drops the rest of that reply.
  If the mic permission is refused, the session runs typing-only and the agent still answers out
  loud.
- **At the ear.** Holding the phone to the ear turns the screen off (proximity wake lock) and moves
  the sound to the earpiece, unless a headset is connected.
- **Ending.** End, Back, the notification's End action, or 5 minutes of quiet (with a 30 s
  countdown) closes the call. A recap shows at once, while the call saves: the call's length and
  turns, the agent's last words, and **Open chat** / **Keep talking**. A call where
  nothing was said goes straight back to the chat. The chat shows every turn as normal messages,
  so typing can continue with the chat's own model.

## Settings (Settings → Voice mode)

The page appears in the hub once any provider can run a call.

- **Provider.** ElevenLabs, Gemini or OpenAI, each with its logo. A provider without a key shows
  "Needs your … key" and a button to its key page. If the picked one can't run, calls use the first
  one that can, and the page says so.

### ElevenLabs

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

### Gemini and OpenAI

Both are speech-to-speech models: one model hears and speaks, so there's no LLM or voice-model
picker, only:

- **Voice.** Gemini Live's 30 built-in voices (Puck by default) or OpenAI's 10 (Marin by
  default), in a searchable list. Neither API offers preview clips.
- **Model.** `gemini-3.8-live` (the 3.1 and 2.5 Live previews shut down on 17 Nov 2026), or
  `gpt-realtime-2.1` / `gpt-realtime-2.1-mini`.
- **Language.** Added to the prompt as the language to start in; the model still follows the
  user if they switch.

## How it works

- **Agent.** `VoiceAgentProvisioner` keeps one "EchoFlow Voice" agent per key in sync with the
  settings. It creates the agent once and PATCHes it when the settings hash changes, so an
  unchanged config costs no request. If the agent was deleted on ElevenLabs, it recreates it. The
  agent has no tools; its `tools` list is sent empty so an agent made by an older version loses
  the card tool it used to carry.
- **Session.** `VoiceSession` connects to the signed WebSocket URL, sends
  `conversation_initiation_client_data` (the prompt with the chat so far, the LLM, the voice and the
  language), and then:
  - streams 16 kHz PCM up, with echo cancellation and noise suppression;
  - plays the agent's PCM down on a voice-communication track;
  - answers pings, handles interruptions, and sends typed text as `user_message`;
  - turns down any `client_tool_call` with an error result, since the app offers no tools.
- **Saving.** `VoiceModeController` saves each finished turn into the chat as it happens, so a
  crash loses nothing. After the call it fetches ElevenLabs' own transcript
  (`GET /v1/convai/conversations/{id}`) and rewrites the session's turns if they differ.
- **Gemini and OpenAI.** `RealtimeVoiceSession` runs both, with a `RealtimeVoiceProtocol` for
  each (`GeminiLiveProtocol`, `OpenAiRealtimeProtocol`). Both it and `VoiceSession` implement
  `LiveVoiceSession`, so the controller and the call screen treat all three the same. There's no
  agent to set up: the prompt (the same one ElevenLabs gets, with the chat so far), the model and
  the voice go in the socket's first message.
  - **Gemini Live** (`BidiGenerateContent` WebSocket, key in the URL): 16 kHz PCM up, 24 kHz down,
    with input and output transcription on. The user's turn is what was heard before the reply
    starts. Typed messages go in as `realtimeInput.text` (Gemini 3 Live takes `clientContent`
    only for seeding history). Muting sends `audioStreamEnd`. Connections last about 10
    minutes, so the session keeps the server's resume handle and moves to a new connection on
    `goAway`; `contextWindowCompression` lifts the 15 minute audio cap.
  - **OpenAI Realtime** (`/v1/realtime` WebSocket, bearer key): 24 kHz PCM both ways, semantic VAD,
    and `gpt-4o-transcribe` for the user's words. When the user talks over a reply, the server
    stops it and the app sends `conversation.item.truncate` with how much was played, so the
    model remembers only what the user heard. A tap or a typed message also sends
    `response.cancel`. A reply waits for the user's transcript before it's saved, so the chat
    stays in order.
  - A reply that's cut off is saved with the words shown up to that point. There's no server
    transcript to reconcile with afterwards.
  - Mic audio said before the socket is ready (or while Gemini reconnects) is held and sent once
    it is.
- **Background.** `VoiceModeService` (a microphone foreground service) keeps the call alive with
  the screen off.
- **Spend.** Each call is recorded in the Spend ledger as *Voice mode*. For ElevenLabs the amount
  is what ElevenLabs charged when it reports it, otherwise an estimate from the call length.
  Gemini and OpenAI calls are priced from the tokens each server reports (`RealtimePricing`).
  Google hasn't published `gemini-3.8-live` prices yet, so Gemini rows use Gemini 3.1 Flash
  Live's rates and are marked as estimates.
