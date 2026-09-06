package com.echoflow.data

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/** One policy for every surface that executes untrusted artifact HTML. */
internal object ArtifactWebSecurity {
    /** Persist the offline policy with the document, including HTML downloaded from the app. */
    fun offlineHtml(html: String): String =
        "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; " +
            "script-src 'unsafe-inline' 'unsafe-eval' data:; style-src 'unsafe-inline' data:; " +
            "img-src data: blob:; font-src data:; media-src data: blob:; connect-src 'none'; " +
            "frame-src 'none'; form-action 'none'; base-uri 'none'\">\n" + html

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    fun configure(view: WebView, offline: Boolean) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            blockNetworkLoads = offline
            blockNetworkImage = offline
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
    }

    open class Client(private val offline: Boolean) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
        @Deprecated("Legacy navigation")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?) = true
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            val scheme = request?.url?.scheme?.lowercase()
            return if (scheme == "data" || scheme == "about" || (scheme == "https" && !offline)) null
            else WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(byteArrayOf()))
        }
    }
}
