package com.echoflow.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Exports an artifact to PDF using Android's built-in print framework — no extra dependency and no
 * APK weight. An HTML artifact prints as-is; a markdown/latex report is rendered to a print-ready
 * HTML document (markdown via marked, math via KaTeX) and printed. The system print dialog lets the
 * user "Save as PDF" or send to a printer.
 *
 * Report rendering libraries and fonts are bundled, so reports and math export offline.
 */
object ArtifactExport {

    // Keep transient print WebViews alive until the print adapter is done with them.
    private val liveWebViews = mutableListOf<WebView>()

    fun printArtifact(context: Context, scope: CoroutineScope, title: String, type: String, content: String, offline: Boolean = true) {
        scope.launch(Dispatchers.Main.immediate) {
            val html = withContext(Dispatchers.IO) {
                when (Artifact.normalizeType(type)) {
                    Artifact.TYPE_HTML -> content
                    else -> reportHtml(context, title, content)
                }
            }
            printHtml(context, title, html, offline)
        }
    }

    private fun printHtml(context: Context, title: String, html: String, offline: Boolean) {
        val webView = WebView(context)
        liveWebViews.add(webView)
        ArtifactWebSecurity.configure(webView, offline)
        webView.webViewClient = object : ArtifactWebSecurity.Client(offline) {
            private var printed = false
            override fun onPageFinished(view: WebView, url: String?) {
                if (printed) return
                printed = true
                // Give marked/KaTeX a moment to lay out before snapshotting to PDF.
                Handler(Looper.getMainLooper()).postDelayed({
                    runCatching {
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                        val jobName = title.ifBlank { "Artifact" }
                        val delegate = view.createPrintDocumentAdapter(jobName)
                        val adapter = object : android.print.PrintDocumentAdapter() {
                            override fun onStart() = delegate.onStart()
                            override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
                                cancellationSignal: android.os.CancellationSignal?, callback: LayoutResultCallback?, extras: android.os.Bundle?) =
                                delegate.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
                            override fun onWrite(pages: Array<android.print.PageRange>?, destination: android.os.ParcelFileDescriptor?,
                                cancellationSignal: android.os.CancellationSignal?, callback: WriteResultCallback?) =
                                delegate.onWrite(pages, destination, cancellationSignal, callback)
                            override fun onFinish() {
                                delegate.onFinish()
                                liveWebViews.remove(view)
                                view.destroy()
                            }
                        }
                        printManager.print(
                            jobName,
                            adapter,
                            PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .build(),
                        )
                    }.onFailure {
                        liveWebViews.remove(webView)
                        webView.destroy()
                    }
                }, 700)
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    /** Wrap markdown (+ LaTeX math) in a print-tuned HTML document. */
    internal fun reportHtml(context: Context, title: String, markdown: String): String {
        // Embed the raw markdown safely in a text/plain script block.
        val safe = android.util.Base64.encodeToString(markdown.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        fun asset(name: String) = context.assets.open("artifact/$name").bufferedReader().use { it.readText() }
        fun script(name: String) = asset(name).replace("</script", "<\\/script", ignoreCase = true)
        return """
            <!DOCTYPE html>
            <html><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>${asset("katex.css")}</style>
            <script>${script("marked.js")}</script>
            <script>${script("katex.js")}</script>
            <script>${script("katex-auto-render.js")}</script>
            <style>
              @page { margin: 18mm; }
              body { font-family: Georgia, 'Times New Roman', serif; line-height: 1.55; color: #111; }
              h1, h2, h3 { font-family: 'Helvetica Neue', Arial, sans-serif; line-height: 1.25; }
              h1 { font-size: 26px; } h2 { font-size: 20px; margin-top: 1.4em; } h3 { font-size: 16px; }
              p, li { font-size: 13px; }
              pre, code { font-family: ui-monospace, Consolas, monospace; font-size: 12px; }
              pre { background: #f5f5f5; padding: 10px; border-radius: 6px; overflow-x: auto; }
              table { border-collapse: collapse; width: 100%; font-size: 12px; }
              th, td { border: 1px solid #ccc; padding: 6px 8px; text-align: left; }
              blockquote { border-left: 3px solid #ccc; margin: 0; padding-left: 12px; color: #555; }
              img { max-width: 100%; }
            </style>
            </head><body>
            <div id="content"><pre style="white-space:pre-wrap">${android.text.TextUtils.htmlEncode(markdown)}</pre></div>
            <script id="src" type="text/plain">$safe</script>
            <script>
              window.addEventListener('load', function () {
                try {
                  var bytes = Uint8Array.from(atob(document.getElementById('src').textContent), function(c) { return c.charCodeAt(0); });
                  var md = new TextDecoder().decode(bytes);
                  // Treat model-authored Markdown as data. marked's HTML option is disabled and
                  // the rendered DOM is passed through an allowlist before it reaches the document.
                  var rendered = marked.parse(md, { html: false });
                  var template = document.createElement('template');
                  template.innerHTML = rendered;
                  var blocked = ['SCRIPT', 'IFRAME', 'OBJECT', 'EMBED', 'FORM', 'BASE', 'META', 'LINK', 'STYLE', 'SVG', 'MATH'];
                  var nodes = template.content.querySelectorAll('*');
                  for (var i = nodes.length - 1; i >= 0; i--) {
                    var node = nodes[i];
                    if (blocked.indexOf(node.tagName) >= 0) { node.remove(); continue; }
                    for (var j = node.attributes.length - 1; j >= 0; j--) {
                      var attr = node.attributes[j];
                      var name = attr.name.toLowerCase();
                      var value = attr.value.trim().toLowerCase();
                      if (name.indexOf('on') === 0 || name === 'style' || name === 'srcdoc' ||
                          ((name === 'href' || name === 'src' || name === 'xlink:href') &&
                           !(value.startsWith('https://') || value.startsWith('mailto:') ||
                             (name === 'src' && value.startsWith('data:image/'))))) node.removeAttribute(attr.name);
                    }
                  }
                  document.getElementById('content').replaceChildren(template.content.cloneNode(true));
                  if (window.renderMathInElement) {
                    renderMathInElement(document.body, {
                      delimiters: [
                        { left: '${'$'}${'$'}', right: '${'$'}${'$'}', display: true },
                        { left: '${'$'}', right: '${'$'}', display: false }
                      ]
                    });
                  }
                } catch (e) {}
              });
            </script>
            </body></html>
        """.trimIndent()
    }
}
