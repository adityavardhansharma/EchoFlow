package com.echoflow.weave.compose

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.echoflow.weave.core.program.StateCodec
import com.echoflow.weave.runtime.WeaveBlockController
import java.io.File

/**
 * Share and export (01 §23.4): a block as a PNG, as a `.weave` file (source plus a state line;
 * EchoFlow opens these as a new chat) or as plain text. Files go to cache/shared_docs, the
 * folder FileProvider already exposes.
 */
object WeaveShare {
    private fun safeName(title: String?) = (title ?: "block").replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "block" }.take(60)

    private fun file(context: Context, name: String): File = File(context.cacheDir, "shared_docs").apply { mkdirs() }.let { File(it, name) }

    private fun share(context: Context, f: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun image(context: Context, block: WeaveBlockController, bitmap: Bitmap) {
        val f = file(context, safeName(block.title) + ".png")
        runCatching { f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }.onSuccess { share(context, f, "image/png") }
    }

    /** `.weave` file contents: the canonical source and, when state differs, a `# state` line. */
    fun weaveText(block: WeaveBlockController): String {
        val p = block.program ?: return block.source()
        val s = block.session ?: return block.source()
        return block.source() + "\n# state " + StateCodec.encodeState(p, s.state) + "\n"
    }

    fun weaveFile(context: Context, block: WeaveBlockController) {
        val text = weaveText(block)
        // never write a file the import side would refuse (WeaveImport.MAX_FILE_BYTES)
        if (text.toByteArray().size > WeaveImport.MAX_FILE_BYTES) { block.flashChip("This block is too big to share as a file"); return }
        val f = file(context, safeName(block.title) + ".weave")
        runCatching { f.writeText(text) }.onSuccess { share(context, f, "application/x-weave") }
    }

    /** Splits a `.weave` file into its source and stored state JSON (01 §23.4). */
    fun parseWeaveFile(text: String): Pair<String, String> {
        val lines = text.trimEnd().lines()
        val stateLine = lines.lastOrNull { it.startsWith("# state ") }
        val source = lines.filterNot { it === stateLine }.joinToString("\n").trimEnd()
        return source to (stateLine?.removePrefix("# state ")?.trim() ?: "{}")
    }
}
