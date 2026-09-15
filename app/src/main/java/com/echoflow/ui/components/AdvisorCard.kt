@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing

// ── Echo Adviser ────────────────────────────────────────────────────────────────────────

/**
 * One Echo Adviser consultation in the reply timeline — a tertiary-accented side-channel that
 * is visually unmistakable from a normal reasoning trace. While [active] it shows a branded
 * "consulting…" header with a wavy indicator; once resolved it reveals (expanded by default)
 * the question the answering model asked and the advisor's full advice. This is the
 * transparency win: the user watches a model escalate to a stronger mind and sees what it said.
 */
@Composable
fun AdvisorCard(
    advisorName: String,
    advisorModel: String,
    prompt: String,
    advice: String?,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasAdvice = !advice.isNullOrBlank()
    var userToggled by remember { mutableStateOf<Boolean?>(null) }
    // Default: open while consulting and once advice lands (the advice is the whole point).
    val expanded = userToggled ?: (active || hasAdvice)
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "advisor-chevron")
    val canToggle = hasAdvice || prompt.isNotBlank()

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.base)) {
            Surface(
                onClick = { if (canToggle) userToggled = !expanded },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedPolygonShape(MaterialShapes.Cookie7Sided))
                            .background(MaterialTheme.colorScheme.tertiary),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Psychology, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiary) }
                    Spacer(Modifier.width(Spacing.m))
                    Column(Modifier.weight(1f)) {
                        ModeBadge("ECHO ADVISER", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (active) "Consulting $advisorName…" else "$advisorName advised",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (advisorModel.isNotBlank()) {
                            Text(
                                shortModel(advisorModel),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!active && canToggle) {
                        Icon(
                            Icons.Default.KeyboardArrowDown, if (expanded) "Collapse" else "Expand",
                            Modifier.size(22.dp).rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (active) {
                Spacer(Modifier.height(Spacing.m))
                LinearWavyProgressIndicator(
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(visible = expanded && canToggle, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = Spacing.m)) {
                    if (prompt.isNotBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(Spacing.m)) {
                                Icon(Icons.Default.QuestionAnswer, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(Spacing.s))
                                Column {
                                    Text("What the model asked", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(2.dp))
                                    Text(prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    if (hasAdvice) {
                        if (prompt.isNotBlank()) Spacer(Modifier.height(Spacing.m))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.xs)) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Advice", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                        RichMarkdown(advice!!, Modifier.fillMaxWidth())
                    } else if (!active) {
                        Text(
                            "The advisor was consulted; its written advice wasn't returned separately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
