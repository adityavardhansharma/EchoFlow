package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The polling URL is an absolute URL chosen by the response body, and the request that uses
 * it carries the user's OpenRouter bearer token. Every case here is one where trusting that
 * value would hand the key to somewhere it does not belong.
 */
class OpenRouterVideoPollingOriginTest {

    private val canonical = "https://openrouter.ai/api/v1/videos/job-1"

    @Test fun `a legitimate openrouter url is used as given`() {
        assertEquals(
            "https://openrouter.ai/api/v1/videos/job-1?poll=1",
            OpenRouterVideoService.trustedPollingUrl("https://openrouter.ai/api/v1/videos/job-1?poll=1", "job-1"),
        )
    }

    @Test fun `openrouter subdomains are trusted`() {
        assertEquals(
            "https://api.openrouter.ai/v1/videos/job-1",
            OpenRouterVideoService.trustedPollingUrl("https://api.openrouter.ai/v1/videos/job-1", "job-1"),
        )
    }

    @Test fun `a foreign host never receives the api key`() {
        assertEquals(canonical, OpenRouterVideoService.trustedPollingUrl("https://evil.example/collect", "job-1"))
    }

    @Test fun `a lookalike host does not pass the suffix check`() {
        // The classic bypass: a domain that merely *ends with* the trusted name.
        assertEquals(
            canonical,
            OpenRouterVideoService.trustedPollingUrl("https://openrouter.ai.evil.example/x", "job-1"),
        )
        assertEquals(canonical, OpenRouterVideoService.trustedPollingUrl("https://notopenrouter.ai/x", "job-1"))
    }

    @Test fun `plaintext http is refused even on the right host`() {
        // The token must never travel unencrypted, however trusted the destination.
        assertEquals(canonical, OpenRouterVideoService.trustedPollingUrl("http://openrouter.ai/api/v1/videos/job-1", "job-1"))
    }

    @Test fun `missing, blank and unparseable urls fall back to the canonical job url`() {
        assertEquals(canonical, OpenRouterVideoService.trustedPollingUrl(null, "job-1"))
        assertEquals(canonical, OpenRouterVideoService.trustedPollingUrl("   ", "job-1"))
        assertEquals(canonical, OpenRouterVideoService.trustedPollingUrl("not a url", "job-1"))
        assertEquals(canonical, OpenRouterVideoService.trustedPollingUrl("/api/v1/videos/job-1", "job-1"))
    }

    @Test fun `non-http schemes are refused`() {
        assertEquals(canonical, OpenRouterVideoService.trustedPollingUrl("file:///data/secret", "job-1"))
        assertEquals(canonical, OpenRouterVideoService.trustedPollingUrl("ftp://openrouter.ai/x", "job-1"))
    }
}
