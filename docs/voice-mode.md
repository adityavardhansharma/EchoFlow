# Voice mode

Live, two-way voice conversations powered by **ElevenLabs Agents**, using only the user's
ElevenLabs key.

## How a user meets it

- **Entry.** When the ElevenLabs key is on and has authenticated, an empty composer shows a
  waveform button where Send sits. As soon as the user types or attaches anything it becomes Send
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
- **Heard chips.** Key details in what the user is saying (amounts, dates, names) rise onto the
  line as chips while they talk. `HeardChips` picks them from the live transcript on the device.
- **Live card.** When an answer has a shape (a plan, steps, options), the agent calls the
  `show_card` client tool and a card builds at the top of the screen. When the user changes
  something, the agent sends the whole card again and changed lines are marked.
- **Controls.** Keyboard (opens a "Type to talk…" field; typing asks the agent to hold off), one
  big button (mic on/off; Stop while the agent talks), and End. While the agent talks, a tap
  anywhere also stops it and drops the rest of that reply. If the mic permission is refused, the
  session runs typing-only and the agent still answers out loud.
- **At the ear.** Holding the phone to the ear turns the screen off (proximity wake lock) and moves
  the sound to the earpiece, unless a headset is connected.
- **Ending.** End, Back, the notification's End action, or 5 minutes of quiet (with a 30 s
  countdown) closes the call. A recap shows at once, while the call saves: the call's length and
  turns, the card (or the agent's last words), and **Open chat** / **Keep talking**. A call where
  nothing was said goes straight back to the chat. The chat shows every turn as normal messages,
  so typing can continue with the chat's own model.

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
  unchanged config costs no request. If the agent was deleted on ElevenLabs, it recreates it. The
  agent carries the `show_card` client tool (and asks for `client_tool_call` events); an account
  that rejects it gets the same agent without the tool.
- **Session.** `VoiceSession` connects to the signed WebSocket URL, sends
  `conversation_initiation_client_data` (the prompt with the chat so far, the LLM, the voice and the
  language), and then:
  - streams 16 kHz PCM up, with echo cancellation and noise suppression;
  - plays the agent's PCM down on a voice-communication track;
  - answers pings, handles interruptions, and sends typed text as `user_message`;
  - answers `client_tool_call` for `show_card` with a `client_tool_result`.
- **Saving.** `VoiceModeController` saves each finished turn into the chat as it happens, so a
  crash loses nothing. After the call it fetches ElevenLabs' own transcript
  (`GET /v1/convai/conversations/{id}`) and rewrites the session's turns if they differ.
- **Background.** `VoiceModeService` (a microphone foreground service) keeps the call alive with
  the screen off.
- **Spend.** Each call is recorded in the Spend ledger as *Voice mode*. The amount is what
  ElevenLabs charged when it reports it, otherwise an estimate from the call length.
