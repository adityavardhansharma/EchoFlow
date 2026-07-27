# Video generation

Describe a clip in chat, get it back in the conversation. OpenRouter is the only route —
there is no on-device path, because a clip needs a datacentre GPU for minutes at a time.

Turn it on with **+ → Create video**. Configure it in **Settings → Video generation**.

## Why this is not "image generation, but longer"

Image generation is one streaming chat completion: request in, base64 out, seconds later.
Video is a different surface entirely. OpenRouter serves it from `/api/v1/videos` as an
**asynchronous job API**:

1. `POST /api/v1/videos` → `{ id, polling_url, status }`
2. `GET <polling_url>` until `status` is terminal (`completed`, `failed`, `cancelled`, `expired`)
3. `GET /api/v1/videos/{id}/content?index=0` → MP4 bytes

That takes 30 seconds to several minutes. Three consequences drive the whole design.

### The row is the job, not the result

`generated_videos` is written **before** OpenRouter is called and updated at every state
change, carrying the provider's job id and polling URL. That is what makes an interrupted
run recoverable: on launch, `ChatViewModel` finds every unfinished row and resumes polling
it. A clip the user has already paid for is never abandoned because the process died.

`GeneratedVideoStore` owns that lifecycle. The MP4 downloads to a temp file and is renamed
into place only once whole, so a dropped connection cannot leave a truncated clip that the
row claims is playable.

### The chat card points at the job, not the file

An image segment persists a file path. A video segment persists the **row id**
(`VideoRef.videoId`), because the message can be written while the clip is still rendering.
The card resolves its live state through `ChatViewModel.observeVideo(id)`, so it shows the
truth whether the clip finished a second ago or while the app was closed.

If a turn is killed before it can persist a message at all, the resume pass inserts the card
itself — otherwise the finished clip would have nothing in the conversation pointing at it.

### Deleting a chat has to stop the render first

A render outlives the turn that started it, so `deleteThread` cannot just remove rows. It
cancels the chat's stream job **and** any resumed video jobs, and *joins* them — cancellation
alone is not enough, since a coroutine is only stopped once it has unwound. Only then are
files removed and the thread deleted.

The store defends the same boundary independently, because cancellation always has a window:

- Every write after `createJob` checks the row still exists. The DAO's `REPLACE` strategy
  would otherwise re-insert a job pointing at a deleted chat.
- `completeWithDownload` re-checks after the download and does the row write *inside* the
  guarded block, so a job that disappears between the rename and the update cannot leave a
  finished MP4 that nothing points at.
- `deleteFilesForChat` sweeps by row id, not only by recorded path, catching half-written
  `.tmp` files from a killed download that never got a `filePath`.

`VideoGenerationException.JobRemoved` ends the run quietly — the conversation is gone, so
there is no failure to report and nowhere to report it.

Two consequences of that quiet ending are easy to get wrong:

- Resumed jobs are started **lazily**, registered, and only then started, so a job can never
  be running before the map that `cancelWorkForChat` consults knows about it. Launching
  eagerly leaves a window whose width depends on dispatcher internals.
- The "your video is ready" notification is gated on `GeneratedVideo.isPlayable`, not on the
  flow completing. A run whose chat was deleted mid-render *completes normally* and has no
  file, so keying off completion would announce a clip for a conversation that no longer
  exists — and tapping it would open nothing.

### Length is the model's call

EchoFlow never sends `duration`. Models publish discrete supported lengths (Veo offers 4, 6
and 8 seconds; Sora goes to 20) and picking for the user would mean guessing at both cost and
pacing. The settings page says so outright, so the absence reads as a decision rather than an
oversight.

## Framing is validated, not assumed

`supported_resolutions` and `supported_aspect_ratios` differ per model and an out-of-set value
is a hard 400 — not a silent downgrade. `VideoRequestPolicy` reconciles the user's preference
against the selected model's declared capabilities before anything is sent:

- Supported preference → passed through.
- Unsupported → substitute one with the **same orientation** first, so a portrait request
  never comes back landscape; resolution snaps to the closest offer.
- Model declares nothing → send nothing. Several image-to-video models take their framing from
  the input image, and inventing a parameter for them would fail the request.

The settings page disables unsupported chips rather than hiding them, so the page does not
rearrange itself every time the model changes.

## The API key only goes to OpenRouter

`polling_url` is an **absolute URL supplied by the response body**, and the request that uses
it carries the user's bearer token. Forwarding it unchecked would turn any tampered, proxied
or compromised response into a credential exfiltration path.

`OpenRouterVideoService.trustedPollingUrl` accepts only HTTPS on `openrouter.ai` or a
subdomain of it, and falls back to the canonical `/videos/{id}` URL otherwise — a downgrade
rather than a hard failure, so a legitimate response-shape change cannot break generation.
The check runs on **every poll**, not just where the URL is first received: it is persisted
across launches, and trusting the stored value would leave the token one bad row away from a
foreign host.

## Pricing

OpenRouter ships three SKU spellings in one directory response, in two units, sliced by
resolution and/or audio:

| SKU | Unit | Example |
| --- | --- | --- |
| `cents_per_video_output_second_720p` | cents | xAI |
| `duration_seconds_720p` | dollars | Sora |
| `duration_seconds_with_audio_720p` | dollars | Veo |

`OpenRouterVideoModelDirectory.parsePricing` normalizes all of them to USD per output second.
Quoting the wrong one would be off by 100×, or quote a variant the user is not buying — hence
`OpenRouterVideoPricingTest`.

## Background behaviour

A `KeepAliveService` hold spans the render so the process is not frozen mid-poll, and a
notification fires when the clip lands. Polling backs off from 5s to 20s — clips rarely arrive
in under half a minute, so a tight loop only burns battery and rate limit. A job still running
after 20 minutes is given up on.

## In-chat presentation

The card deliberately reuses image generation's choreography — dot-field placeholder, stretch
to the media's real aspect ratio, scanline reveal — so the two creation modes read as one
idea. What differs:

- The reveal lands on the clip's **opening frame**, pulled with one `MediaMetadataRetriever`
  pass, so it never sweeps onto a black rectangle waiting for a decoder. That pass also gives
  the true aspect ratio, honouring the rotation tag (without it, portrait clips render in a
  landscape box).
- The player is built only when the user taps play. A conversation can hold many clips, and a
  decoder per card would exhaust the device's codecs.
- The status line is real information: "Rendering — this takes a few minutes" is worth saying
  when the wait is minutes rather than seconds.

Playback is media3 with a **TextureView** surface, which clips correctly to the card's rounded
corners inside a scrolling list.

## Files

| Concern | File |
| --- | --- |
| Job + result rows | `data/Models.kt` (`GeneratedVideo`, `VideoModel`), `data/Daos.kt` |
| File and lifecycle | `data/GeneratedVideoStore.kt` |
| HTTP contract | `data/OpenRouterVideoService.kt` |
| Capabilities + pricing | `data/OpenRouterVideoModelDirectory.kt` |
| Framing rules | `data/VideoRequestPolicy.kt` |
| submit → poll → download | `data/VideoGenerationEngine.kt` |
| Settings page | `ui/screens/SettingsVideoGen.kt` |
| In-chat card and player | `ui/components/VideoGenComponents.kt` |
| Turn routing and resume | `ui/ChatViewModel.kt` |
