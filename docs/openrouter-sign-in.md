# OpenRouter connection

The OpenRouter settings card offers browser sign-in first and manual API-key entry
as a secondary dialog. Both methods feed the existing `SettingsRepository.apiKey`
flow and `getApiKeyDirect()` accessor, so chat, search, image and video consumers
continue to use the same active credential.

## Storage and migration

`openrouter_api_key` remains the active key in the existing encrypted preferences.
Missing `openrouter_signed_in` metadata means a manual connection; no existing key
is rewritten or invalidated on upgrade. The existing plaintext-to-encrypted
migration still runs before the connection state is read.

Successful sign-in atomically writes the active key and its origin. When switching
from a manual key, that key is retained as `openrouter_manual_key` in the same
encrypted store. Signing in again preserves this backup. Restoring it makes it
active and removes the backup entry. Explicit manual replacement or disconnect
removes the backup. Disconnect only removes local credentials; it does not revoke
keys on OpenRouter. Android backup rules already exclude all shared preferences.

## Browser flow

`OpenRouterAuthService` uses S256 PKCE and a random callback-path nonce. It opens
an ephemeral listener on **127.0.0.1 only**, then sends the browser to OpenRouter's
documented authorization URL. The local callback accepts a single code only for
the expected path and exchanges it directly with OpenRouter over HTTPS. The
exchange client has no credential/logging interceptors and follows no redirects.

The callback response includes a **Return to EchoFlow** link that brings the app
to the foreground. This Android link carries no code or credential and only
returns focus; the local listener handles authorization. The response never echoes
the authorization code and disables caching/referrer transmission.

The Settings ViewModel owns sign-in through rotation. Cancelling or clearing the
ViewModel closes the listener; it otherwise expires after ten minutes. A process
restart deliberately loses the in-memory verifier, requiring a fresh sign-in.
The prior active credential is retained throughout authorization and exchange.

No hosted callback service or OAuth client secret is required. OpenRouter labels
localhost integrations by host/port rather than giving them public marketplace
attribution. A future branded public callback would require a controlled HTTPS
domain and verified Android App Links; a custom scheme alone is not a documented
OpenRouter callback option.

Reference: https://openrouter.ai/docs/guides/overview/auth/oauth

## Validation

Repository tests cover both existing-key migration paths, restart, account
switching, restoring a saved manual key, manual replacement and disconnect.
Authentication tests cover the RFC 7636 challenge vector, callback rejection,
a real local socket callback with a mocked HTTPS exchange, and cancellation.
Compose tests cover the primary sign-in action, replacement confirmation, manual
entry and connection states, with light/dark and narrow-screen render captures.

A real OpenRouter account/browser authorization still needs a device smoke test:
sign in, return to EchoFlow, run a request, restore a manual key, then cancel and
retry sign-in. Also exercise process termination during browser authorization.
