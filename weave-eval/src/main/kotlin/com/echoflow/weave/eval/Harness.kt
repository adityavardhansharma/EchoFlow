package com.echoflow.weave.eval

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.eval.Formatter
import com.echoflow.weave.core.prompt.EchoUiPrompt
import com.echoflow.weave.core.prompt.EchoUiTiers
import com.echoflow.weave.core.prompt.IntelligentUiLevel
import java.io.File
import kotlin.system.exitProcess

/**
 * The model eval harness (07 §10), file-based so no provider API key is needed:
 *
 *   prepare --run eval/runs/<name> [--tier 2] [--level balanced]
 *       writes system.md (the 06 prompt section), tasks/<id>.md (one user turn each),
 *       manifest.tsv and AGENT.md (instructions for whoever answers the requests)
 *   score --run eval/runs/<name> --model <model-id>
 *       grades responses/<model-id>/<id>.md and writes report-<model-id>.md / .json
 *   compare --run eval/runs/<name>
 *       one table across every model in responses/
 *
 * Replies can come from any source: a provider SDK, a CLI agent, or a person. Each reply file is
 * the assistant's whole answer to one task, exactly as it would appear in chat.
 */
fun main(args: Array<String>) {
    val cmd = args.firstOrNull() ?: usage()
    val opts = args.drop(1).chunked(2).filter { it.size == 2 }.associate { (k, v) -> k.removePrefix("--") to v }
    val run = File(opts["run"] ?: usage())
    when (cmd) {
        "prepare" -> prepare(run, opts["tier"]?.toInt() ?: EchoUiTiers.shippedTier(1), IntelligentUiLevel.fromStorage(opts["level"]))
        "score" -> println(score(run, opts["model"] ?: usage()).summaryLine())
        "compare" -> println(compare(run))
        else -> usage()
    }
}

private fun usage(): Nothing {
    System.err.println("usage: prepare|score|compare --run <dir> [--tier n] [--level balanced|immersive] [--model id]")
    exitProcess(2)
}

fun prepare(run: File, tier: Int, level: IntelligentUiLevel) {
    val tasks = Tasks.forTier(tier)
    File(run, "tasks").mkdirs()
    File(run, "responses").mkdirs()
    File(run, "system.md").writeText(EchoUiPrompt.section(tier, level, catalog = BuiltinCatalog.forBuild(tier)))
    for (t in tasks) File(run, "tasks/${t.id}.md").writeText(t.prompt + "\n")
    File(run, "manifest.tsv").writeText(
        "tier\t$tier\nlevel\t${level.storageKey}\n" + tasks.joinToString("\n") { "${it.id}\t${it.category}\t${it.control}" } + "\n",
    )
    File(run, "AGENT.md").writeText(
        """
        |# Answering eval requests
        |
        |You are standing in for a chat model inside the EchoFlow app. For every file in `tasks/`:
        |
        |1. Treat `system.md` as your system prompt and the task file as the user's message.
        |2. Write the reply you would send in the chat, exactly as the user would see it, to
        |   `responses/<model-id>/<task-id>.md` (same file name as the task).
        |3. Answer each task on its own, as a fresh conversation. Do not look at other files in this
        |   repository, earlier answers, tests or the harness: only `system.md` and the task.
        |4. Do not run, check or fix your Weave. Write it once, the way you would in a live reply.
        |
        |Some messages are ordinary questions. Answer those the way the system prompt says.
        |""".trimMargin() + "\n",
    )
    println("prepared ${tasks.size} tasks (tier $tier, ${level.storageKey}) in $run")
}

private fun manifest(run: File): Triple<Int, IntelligentUiLevel, List<EvalTask>> {
    val lines = File(run, "manifest.tsv").readLines().filter { it.isNotBlank() }
    val tier = lines.first { it.startsWith("tier\t") }.substringAfter('\t').toInt()
    val level = IntelligentUiLevel.fromStorage(lines.first { it.startsWith("level\t") }.substringAfter('\t'))
    val ids = lines.filterNot { it.startsWith("tier\t") || it.startsWith("level\t") }.map { it.substringBefore('\t') }
    val byId = Tasks.all.associateBy { it.id }
    return Triple(tier, level, ids.mapNotNull { byId[it] })
}

fun score(run: File, model: String): ModelReport {
    val (tier, level, tasks) = manifest(run)
    val dir = File(run, "responses/$model")
    val max = EchoUiTiers.maxBlocks(level)
    val scores = tasks.map { t ->
        val f = File(dir, "${t.id}.md")
        Scoring.score(t, if (f.exists()) f.readText() else null, tier, max)
    }
    val report = ModelReport(model, tier, scores)
    File(run, "report-$model.md").writeText(markdown(report))
    File(run, "report-$model.json").writeText(json(report))
    return report
}

fun compare(run: File): String {
    val models = File(run, "responses").listFiles { f -> f.isDirectory }?.map { it.name }?.sorted() ?: emptyList()
    val reports = models.map { score(run, it) }
    val sb = StringBuilder()
    sb.append("| Model | Blocks | Parse success | Dropped / block | Render success | Task completion | Tokens / block | Over-use | Missing | Meets tier ${reports.firstOrNull()?.tier ?: ""} bar |\n")
    sb.append("|---|---|---|---|---|---|---|---|---|---|\n")
    for (r in reports) sb.append(r.row()).append('\n')
    File(run, "summary.md").writeText("# Eval summary\n\n" + sb.toString())
    return sb.toString()
}

private fun pct(d: Double) = "%.1f %%".format(d * 100)

private fun ModelReport.row() =
    "| $model | $blockCount | ${pct(parseSuccess)} | ${"%.2f".format(droppedPerBlock)} | ${pct(renderSuccess)} | ${pct(taskCompletion)} | ${"%.0f".format(tokenCost)} | ${pct(overUse)} | $missing | ${if (passesBars()) "yes" else "no"} |"

fun ModelReport.summaryLine() =
    "$model: parse ${pct(parseSuccess)}, dropped ${"%.2f".format(droppedPerBlock)}, render ${pct(renderSuccess)}, " +
        "completion ${pct(taskCompletion)}, tokens ${"%.0f".format(tokenCost)}, over-use ${pct(overUse)}, missing $missing, bar ${if (passesBars()) "met" else "not met"}"

private fun markdown(r: ModelReport): String {
    val sb = StringBuilder("# ${r.model} — tier ${r.tier}\n\n")
    sb.append("| Metric | Value | Bar (07 §10.3) |\n|---|---|---|\n")
    sb.append("| Parse success | ${pct(r.parseSuccess)} | ≥ 95 % |\n")
    sb.append("| Dropped statements / block | ${"%.2f".format(r.droppedPerBlock)} | ≤ 0.5 |\n")
    sb.append("| Render success | ${pct(r.renderSuccess)} | ≥ 97 % |\n")
    sb.append("| Task completion | ${pct(r.taskCompletion)} | ≥ 85 % |\n")
    sb.append("| Tokens / block | ${"%.0f".format(r.tokenCost)} | — |\n")
    sb.append("| Over-use (controls) | ${pct(r.overUse)} | ≤ 10 % |\n")
    sb.append("| Missing replies | ${r.missing} | — |\n\n")
    sb.append("**Meets the tier ${r.tier} bar: ${if (r.passesBars()) "yes" else "no"}.**\n\n")
    sb.append("| Task | Category | Blocks | Parse | Renders | Completed | Detail |\n|---|---|---|---|---|---|---|\n")
    for (s in r.scores) {
        sb.append("| ${s.task.id} | ${s.task.category} | ${s.blocks} | ${mark(s.parseOk, s.blocks)} | ${mark(s.renders, s.blocks)} | ${if (s.task.control) "—" else mark(s.completed, 1)} | ${s.detail.replace("|", "\\|").take(160)} |\n")
    }
    val withDiags = r.scores.filter { it.diagnostics.isNotEmpty() }
    if (withDiags.isNotEmpty()) {
        sb.append("\n## Diagnostics\n")
        for (s in withDiags) {
            sb.append("\n**${s.task.id}**\n")
            for (d in s.diagnostics.take(12)) sb.append("- ").append(d.replace("|", "\\|")).append('\n')
        }
    }
    return sb.toString()
}

private fun mark(b: Boolean, blocks: Int) = if (blocks == 0) "—" else if (b) "✓" else "✗"

private fun json(r: ModelReport): String {
    val j = { s: String -> Formatter.jsonString(s) }
    val tasks = r.scores.joinToString(",\n    ") { s ->
        "{\"id\": ${j(s.task.id)}, \"category\": ${j(s.task.category)}, \"responded\": ${s.responded}, \"blocks\": ${s.blocks}, " +
            "\"parseOk\": ${s.parseOk}, \"dropped\": ${s.dropped}, \"renders\": ${s.renders}, \"completed\": ${s.completed}, " +
            "\"tokens\": ${s.tokens}, \"detail\": ${j(s.detail)}, \"diagnostics\": [${s.diagnostics.joinToString(", ") { j(it) }}]}"
    }
    return """{
  "model": ${j(r.model)}, "tier": ${r.tier},
  "parseSuccess": ${r.parseSuccess}, "droppedPerBlock": ${r.droppedPerBlock}, "renderSuccess": ${r.renderSuccess},
  "taskCompletion": ${r.taskCompletion}, "tokensPerBlock": ${r.tokenCost}, "overUse": ${r.overUse}, "missing": ${r.missing},
  "meetsBar": ${r.passesBars()},
  "tasks": [
    $tasks
  ]
}
"""
}
