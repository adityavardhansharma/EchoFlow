package com.echoflow.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Exports an artifact to PDF using Android's built-in print framework — no extra dependency and no
 * APK weight. An HTML artifact prints as-is; a markdown/latex report is rendered to a print-ready
 * HTML document (markdown via marked, math via KaTeX) and printed. The system print dialog lets the
 * user "Save as PDF" or send to a printer.
 *
 * Note: report math uses CDN libraries, so exporting a report with equations needs a connection
 * (the on-screen preview always uses the app's native renderer). Plain text exports work offline.
 */
object ArtifactExport {

    // Keep transient print WebViews alive until the print adapter is done with them.
    private val liveWebViews = mutableListOf<WebView>()

    fun printArtifact(context: Context, title: String, type: String, content: String) {
        val html = when (Artifact.normalizeType(type)) {
            Artifact.TYPE_HTML -> content
            else -> reportHtml(title, content)
        }
        val webView = WebView(context)
        liveWebViews.add(webView)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                // Give marked/KaTeX a moment to lay out before snapshotting to PDF.
                Handler(Looper.getMainLooper()).postDelayed({
                    runCatching {
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                        val jobName = title.ifBlank { "Artifact" }
                        val adapter = view.createPrintDocumentAdapter(jobName)
                        printManager.print(
                            jobName,
                            adapter,
                            PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .build(),
                        )
                    }
                    liveWebViews.remove(webView)
                }, 700)
            }
        }
        webView.loadDataWithBaseURL("https://localhost/", html, "text/html", "utf-8", null)
    }

    /** Wrap markdown (+ LaTeX math) in a print-tuned HTML document. */
    private fun reportHtml(title: String, markdown: String): String {
        // Embed the raw markdown safely in a text/plain script block.
        val safe = markdown.replace("</script>", "<\\/script>")
        val katex = "https://cdn.jsdelivr.net/npm/katex@0.16.9/dist"
        return """
            <!DOCTYPE html>
            <html><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <link rel="stylesheet" href="$katex/katex.min.css">
            <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
            <script defer src="$katex/katex.min.js"></script>
            <script defer src="$katex/contrib/auto-render.min.js"></script>
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
            <div id="content"></div>
            <script id="src" type="text/plain">$safe</script>
            <script>
              window.addEventListener('load', function () {
                try {
                  var md = document.getElementById('src').textContent;
                  document.getElementById('content').innerHTML = marked.parse(md);
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
