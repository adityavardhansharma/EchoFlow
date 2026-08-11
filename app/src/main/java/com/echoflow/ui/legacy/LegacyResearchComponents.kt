@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.legacy

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echoflow.data.Citation
import com.echoflow.data.ResearchJson
import com.echoflow.data.ResearchRun
import com.echoflow.ui.components.RichMarkdown
import com.echoflow.ui.theme.Spacing
import org.json.JSONArray
import org.json.JSONObject

/**
 * The pre-redesign Deep Research UI, kept alive verbatim so existing conversations keep rendering
 * exactly as they were written.
 *
 * **This file is frozen.** Nothing in `ui/components` may depend on it, and it is reached from a
 * single dispatch point: assistant messages whose persisted segments are typed `"report"`,
 * `"plan"` or `"data"` (written before the redesign), plus any run still stamped
 * [ResearchRun.UI_VERSION_LEGACY] that resumes across an app update. Research produced from now on
 * writes a `"research"` segment and is drawn by `ResearchTimeline` / `ResearchResultCard` instead.
 *
 * Don't "improve" anything here — a change to these composables retroactively rewrites how old
 * chats look, which is the exact failure this split exists to prevent. The one thing it does still
 * inherit is the design system itself (theme colours, typography, [Spacing], markdown rendering):
 * old messages should keep following palette and font changes, or they would look broken sitting
 * next to new ones. Composition is frozen; skin is not.
 */

private fun faviconFor(url: String): String =
    "https://www.google.com/s2/favicons?domain=${runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: ""}&sz=64"

private fun legacyHostOf(url: String): String =
    runCatching { android.net.Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url

private fun legacyOpenUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }
}

/**
 * Live Deep Research progress, mirroring the foreground notification: engine + topic, the
 * current phase with a determinate/indeterminate bar, the plan as a checklist, the sources
 * found so far, and a Cancel action. Driven entirely from the persisted [ResearchRun] so it
 * looks identical whether watched live or reopened after the app was killed.
 */
@Composable
fun LegacyResearchProgressCard(
    run: ResearchRun,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps = remember(run.planJson) { ResearchJson.stepsFromJson(run.planJson) }
    val sources = remember(run.sourcesJson) { ResearchJson.sourcesFromJson(run.sourcesJson) }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Science, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (run.engineKind == "data-agent") "Data Agent" else "Deep Research",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        run.engineLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Cancel")
                }
            }

            Spacer(Modifier.height(Spacing.m))
            Text(
                run.topic,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(Spacing.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text(
                    run.phase ?: "Working…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(Spacing.s))
            if (run.progressTotal > 0) {
                LinearProgressIndicator(
                    progress = { run.progressDone.toFloat() / run.progressTotal },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            run.costInfo?.takeIf { it.isNotBlank() }?.let { cost ->
                Spacer(Modifier.height(Spacing.s))
                Text(
                    "Spent so far: $cost",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (steps.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.m))
                steps.forEachIndexed { index, step ->
                    val done = index < run.progressDone
                    val active = index == run.progressDone && !run.isTerminal
                    Row(
                        Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            done -> Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            active -> LoadingIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            else -> Icon(Icons.Default.RadioButtonUnchecked, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(Spacing.s))
                        Text(
                            step,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (done || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.m))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        sources.take(6).forEach { source ->
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(22.dp)) {
                                AsyncImage(faviconFor(source.url), null, Modifier.padding(2.dp).clip(CircleShape))
                            }
                        }
                    }
                    Spacer(Modifier.width(Spacing.s))
                    Text(
                        "${sources.size} source${if (sources.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A finished research report: distinct from a normal chat bubble — a titled container with
 * the full markdown body and a sources section, plus copy. The [planSteps] (if any) collapse
 * into a "How this was researched" disclosure so the report leads with the answer.
 */
@Composable
fun LegacyReportCard(
    report: String,
    citations: List<Citation>,
    planSteps: List<String>,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Plain background — the report reads like a first-class answer, not a boxed card. Only a
    // small label + a divider before sources give it light structure.
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Science, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.s))
            Text(
                "Research report",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(
                onClick = onCopy,
                modifier = Modifier.size(30.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) { Icon(Icons.Default.ContentCopy, "Copy report", Modifier.size(15.dp)) }
        }

        if (planSteps.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.s))
            LegacyResearchPlanDisclosure(planSteps)
        }

        Spacer(Modifier.height(Spacing.s))
        RichMarkdown(report, Modifier.fillMaxWidth())

        if (citations.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.base))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.base))
            LegacySourcesRow(citations)
        }
    }
}

/** Collapsed "How this was researched" recap of the plan steps. */
@Composable
fun LegacyResearchPlanDisclosure(steps: List<String>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "plan-chevron")
    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "How this was researched · ${steps.size} steps",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.KeyboardArrowDown, if (expanded) "Collapse" else "Expand", Modifier.size(20.dp).rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = Spacing.s)) {
                    steps.forEachIndexed { i, step ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text("${i + 1}. ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Text(step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Adaptive renderer for a Data Agent result (a JSON string). Renders recursively so nested
 * data reads cleanly: an array of objects becomes stacked item cards; a nested object becomes
 * a titled section with indented label→value rows; primitives align in two columns; anything
 * unparseable falls back to markdown. Plain background — no boxed grey card.
 */
@Composable
fun LegacyDataResultCard(
    json: String,
    citations: List<Citation>,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val array = remember(json) { runCatching { JSONArray(json) }.getOrNull() }
    val obj = remember(json) { if (array == null) runCatching { JSONObject(json) }.getOrNull() else null }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Science, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.s))
            Text(
                "Data result",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(
                onClick = onCopy,
                modifier = Modifier.size(30.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(15.dp)) }
        }
        Spacer(Modifier.height(Spacing.m))

        when {
            array != null -> LegacyJsonArrayView(array, Modifier.fillMaxWidth())
            obj != null -> LegacyJsonObjectView(obj, Modifier.fillMaxWidth())
            else -> RichMarkdown(json, Modifier.fillMaxWidth())
        }

        if (citations.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.base))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.base))
            LegacySourcesRow(citations)
        }
    }
}

/** Renders any JSON value, recursing into objects/arrays. */
@Composable
private fun LegacyJsonValue(value: Any?, modifier: Modifier = Modifier) {
    when (value) {
        null, JSONObject.NULL -> Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        is JSONObject -> LegacyJsonObjectView(value, modifier)
        is JSONArray -> LegacyJsonArrayView(value, modifier)
        else -> Text(value.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = modifier)
    }
}

/** Object → for each key: nested groups become titled sections, leaves become label→value rows. */
@Composable
private fun LegacyJsonObjectView(obj: JSONObject, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        obj.keys().forEach { key ->
            val value = obj.opt(key)
            val nested = value is JSONObject || (value is JSONArray && legacyArrayHasObjects(value))
            if (nested) {
                Column {
                    Text(
                        legacyPrettyKey(key),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Box(Modifier.padding(start = Spacing.m, top = Spacing.xs)) { LegacyJsonValue(value) }
                }
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        legacyPrettyKey(key),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.4f).padding(end = Spacing.s),
                    )
                    LegacyJsonValue(value, Modifier.weight(0.6f))
                }
            }
        }
    }
}

/** Array → object elements become stacked item cards; primitives become a bullet list. */
@Composable
private fun LegacyJsonArrayView(arr: JSONArray, modifier: Modifier = Modifier) {
    if (legacyArrayHasObjects(arr)) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.padding(Spacing.m)) { LegacyJsonValue(item) }
                }
            }
        }
    } else {
        Column(modifier) {
            for (i in 0 until arr.length()) {
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(arr.opt(i)?.toString() ?: "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private fun legacyArrayHasObjects(arr: JSONArray): Boolean =
    (0 until arr.length()).any { arr.opt(it) is JSONObject }

private fun legacyPrettyKey(key: String): String =
    key.replace('_', ' ').replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }

/**
 * Compact source chips under a finished answer; each opens the page.
 *
 * A frozen copy of the shared `SourcesRow`: the new research surfaces present sources very
 * differently, and old reports shouldn't shift when that component evolves.
 */
@Composable
private fun LegacySourcesRow(citations: List<Citation>, modifier: Modifier = Modifier) {
    if (citations.isEmpty()) return
    val context = LocalContext.current
    Column(modifier) {
        Text(
            "Sources",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.s),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            citations.forEach { citation ->
                Surface(
                    onClick = { legacyOpenUrl(context, citation.url) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        Modifier.padding(horizontal = Spacing.m, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = faviconFor(citation.url),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            legacyHostOf(citation.url),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
