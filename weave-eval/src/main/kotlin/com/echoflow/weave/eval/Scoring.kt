package com.echoflow.weave.eval

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.check.Severity
import com.echoflow.weave.core.eval.EvalContext
import com.echoflow.weave.core.eval.Formatter
import com.echoflow.weave.core.eval.Session
import com.echoflow.weave.core.program.Compiler
import com.echoflow.weave.core.program.WeaveProgram
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** A fenced block found in a reply. */
data class Fence(val info: String, val body: String)

/** The grade for one task reply (07 §10.2). */
data class TaskScore(
    val task: EvalTask,
    val responded: Boolean,
    val blocks: Int,
    val parseOk: Boolean,
    val dropped: Int,
    val renders: Boolean,
    val completed: Boolean,
    val tokens: Int,
    val detail: String,
    val diagnostics: List<String>,
)

/** Metrics per model × tier, and the tier-table bars (07 §10.3). */
data class ModelReport(val model: String, val tier: Int, val scores: List<TaskScore>) {
    private val withBlock = scores.filter { !it.task.control && it.blocks > 0 }
    private val tasks = scores.filter { !it.task.control }
    /** All 20 control prompts (07 §10.2), answered or not: an unanswered control is not a block. */
    private val controls = scores.filter { it.task.control }

    val blockCount get() = withBlock.size
    val parseSuccess get() = ratio(withBlock.count { it.parseOk }, withBlock.size)
    val droppedPerBlock get() = if (withBlock.isEmpty()) 0.0 else withBlock.sumOf { it.dropped }.toDouble() / withBlock.size
    val renderSuccess get() = ratio(withBlock.count { it.renders }, withBlock.size)
    val taskCompletion get() = ratio(tasks.count { it.completed }, tasks.size)
    val tokenCost get() = if (withBlock.isEmpty()) 0.0 else withBlock.sumOf { it.tokens }.toDouble() / withBlock.size
    val overUse get() = ratio(controls.count { it.blocks > 0 }, controls.size)
    val missing get() = scores.count { !it.responded }

    fun passesBars(balanced: Boolean = true) = parseSuccess >= 0.95 && droppedPerBlock <= 0.5 && renderSuccess >= 0.97 &&
        taskCompletion >= 0.85 && (!balanced || overUse <= 0.10)

    private fun ratio(a: Int, b: Int) = if (b == 0) 0.0 else a.toDouble() / b
}

object Scoring {
    /** Diagnostics whose unit of removal is a statement (02 §9.1). */
    private val STATEMENT_DROPS = setOf("E101", "E102", "E103", "E104", "E105", "E106", "E107", "E110")

    private val FENCE_OPEN = Regex("^(```+|~~~+)(\\S*)(.*)$")

    /** Top-level fences only (02 §1.1): indented, quoted or listed fences are not blocks. */
    fun fences(reply: String): List<Fence> {
        val out = ArrayList<Fence>()
        val lines = reply.replace("\r\n", "\n").split('\n')
        var i = 0
        while (i < lines.size) {
            val m = FENCE_OPEN.matchEntire(lines[i])
            if (m == null) { i++; continue }
            val marker = m.groupValues[1]
            val info = (m.groupValues[2] + m.groupValues[3]).trim()
            val body = ArrayList<String>()
            i++
            while (i < lines.size && !(lines[i].startsWith(marker.substring(0, 3)) && lines[i].trim().all { it == marker[0] } && lines[i].trim().length >= marker.length)) {
                body += lines[i]; i++
            }
            i++
            out += Fence(info, body.joinToString("\n"))
        }
        return out
    }

    /** New-block weave fences (not edits). Matching is on the first word, case-sensitive (T-001). */
    fun weaveBlocks(reply: String) = fences(reply).filter { it.info.split(Regex("\\s+")).firstOrNull() == "weave" && !it.info.startsWith("weave edit") }

    fun formatter() = Formatter(Locale.forLanguageTag("en-IN"), ZoneId.of("Asia/Kolkata"), { Instant.parse("2026-11-26T04:30:00Z") })

    fun score(task: EvalTask, reply: String?, tier: Int, maxBlocks: Int = 1): TaskScore {
        if (reply == null) return TaskScore(task, false, 0, false, 0, false, false, 0, "no reply", emptyList())
        val blocks = weaveBlocks(reply)
        if (blocks.isEmpty()) {
            val detail = if (task.control) "plain text (correct)" else "no weave block"
            return TaskScore(task, true, 0, false, 0, false, false, 0, detail, emptyList())
        }
        // Only the first `maxBlocks` render (05 §11.4); grade the first block.
        val body = blocks.first().body
        val catalog = BuiltinCatalog.forBuild(tier)
        val program: WeaveProgram = try { Compiler.compile(body, catalog) } catch (e: Throwable) {
            return TaskScore(task, true, blocks.size, false, 1, false, false, tokens(body), "compiler threw ${e.javaClass.simpleName}", emptyList())
        }
        val diags = program.diagnostics
        val parseOk = diags.none { it.code.startsWith("E1") }
        val dropped = diags.count { it.code in STATEMENT_DROPS }
        val session = Session(program, EvalContext(formatter()))
        val root = if (program.renderable) session.root() else null
        val renders = root != null && root.error == null
        var completed = false
        var detail = if (renders) "renders" else if (program.needsNewerApp) "needs a newer app" else "no renderable root"
        if (!task.control && renders) {
            val r = try { CheckRunner(session).run(task.checks) } catch (e: Throwable) { CheckResult(false, "check threw ${e.javaClass.simpleName}") }
            completed = r.pass
            detail = r.detail
        }
        if (task.control) detail = "block written for a plain question"
        val all = (diags + session.diagnostics).filter { it.severity == Severity.ERROR || it.code in setOf("W401", "W407") }
        return TaskScore(task, true, blocks.size, parseOk, dropped, renders, completed, tokens(body), detail, all.map { "${it.code} ${it.message}" }.distinct())
    }

    /** Approximate tokenizer: ceil(UTF-8 bytes / 4), the same as the prompt snapshots (T-502). */
    fun tokens(s: String) = (s.toByteArray(Charsets.UTF_8).size + 3) / 4
}
