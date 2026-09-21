# System-wide dictation verification

The Dictation settings switch defaults off. Setup requests microphone, draw-over-apps,
accessibility, and (Android 13+) notification permission in sequence. The enabled
preference is committed only when every grant is present. Leaving setup incomplete
keeps it off. The accessibility service owns the 48dp window and promotes itself to
a microphone foreground service only for recording/transcription.

The service uses `AudioWavRecorder`, `SpeechToTextTranscriber`, `SttCatalog`, and the
same selected model, key resolution, mode, and Hinglish preference as chat. The
recorder enforces a shared microphone owner across both entry points.

On Android 13+, insertion uses the active accessibility InputConnection only if the
editor session captured at recording start is still current. This inserts at the
caret and replaces only the active selection without changing the clipboard. A
dispatch exception, missing connection, or changed editor leaves the transcript on
the clipboard as a recoverable fallback. Android's accessibility InputConnection
does not report whether the receiving editor accepted a dispatched commit. Android
12 and older retain the copy-first accessibility paste path: the captured node must
refresh, remain eligible, match the currently focused node, and never have lost
focus. Android may display its own clipboard affordance; EchoFlow marks the clip
sensitive to suppress the preview.

## Device checks

- Deny each setup step or return without granting it: switch remains off.
- Grant setup: switch stays on; no idle foreground-service notification.
- Focus an external editable field: one translucent circle; drag does not start audio.
- Change focus, open Home, open a password field, or foreground EchoFlow: no circle.
- Tap: recording and a microphone FGS notification. Tap again: the same circle spins;
  additional taps cannot start another recording.
- Leave the eligible field during recording: stop capture and transcribe to clipboard.
- Revoke overlay/accessibility/microphone while enabled: switch off, remove circle,
  cancel capture/request, remove notification.
- Reopen a focused field after a completed session: remembered edge and vertical
  position survive. Rotation keeps the circle within system-bar bounds.
- With a real configured STT key: verify insertion at the selection/caret without
  replacing the rest of the field in native, Compose, WebView and rich editors,
  including ChatGPT, Claude and WhatsApp. Changing fields during transcription must
  leave the result on the clipboard and never insert into the new field.

Unit coverage in `SystemDictationTest` checks setup outcomes, repository default-off,
revocation on recreation, cross-instance auto-off observation, field eligibility,
paste decisions, and saved position. Existing STT catalog/request/settings tests
exercise the reused transcription configuration and payloads.

## Recorded validation

Captured directly with `adb exec-out screencap` on an API 35 x86_64 emulator:
`settings-off.png`, `settings-on.png`, `settings-landscape.png`, `overlay-idle.png`,
and `overlay-recording.png`. Overlay images use a separate minimal native notes
app with two editable fields, a password field, and a clear-focus button.

Live checks passed for microphone denial, backing out of overlay/accessibility
setup, full grant setup, no idle FGS, hiding inside EchoFlow and over a password
field, and dragging without recording with persisted left edge + Y. Recording
reported `isForeground=true`, notification 43, microphone type `0x80`, and an
unsilenced mono 16 kHz MIC capture. Revoking overlay permission during recording
removed the window and foreground notification and persisted the master switch off.

UIAutomator temporarily suppresses/unbinds accessibility services, which also
correctly turns this feature off. After setup, these checks used shell input and
screenshots rather than UIAutomator hierarchy dumps.

A placeholder key was used for the original UI/recording checks; no successful live
cloud transcription was claimed in that run. Unit tests cover modern editor-session
identity, secret-field exclusion, legacy ACTION_PASTE, clipboard fallback, target
eligibility/identity decisions, and microphone exclusion; existing STT tests cover
the reused provider request logic.
