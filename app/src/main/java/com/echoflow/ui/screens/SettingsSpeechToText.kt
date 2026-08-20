@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.SttCatalog
import com.echoflow.data.SttCostTier
import com.echoflow.data.SttMode
import com.echoflow.data.SttModel
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.diffAdded

/**
 * Speech-to-text settings — shaped like Imagine: a Cloud | On-device selector on top, then
 * whatever applies to the chosen side.
 *
 * Cloud is the default and the only one that does anything today: it lists curated
 * OpenRouter STT models with their pricing, a $ / $$ / $$$ cost mark, and a Best badge on
 * the lowest Artificial Analysis word-error rate. STT always runs on OpenRouter with the
 * *Cloud models* key, no matter which chat model is selected — so the page also surfaces
 * whether that key is present, and points at Cloud models when it isn't. On-device is a
 * "coming soon" placeholder; local STT is not built yet.
 */
@Composable
internal fun SpeechToTextPage(
    viewModel: SettingsViewModel,
    onOpenCloudModels: () -> Unit,
    onBack: () -> Unit,
) {
    val mode by viewModel.sttMode.collectAsState()

    SettingsPageScaffold(
        title = "Speech to text",
        subtitle = "Talk into the chat box",
        onBack = onBack,
    ) {
        ConnectedToggleRow(
            options = listOf(
                SttMode.Cloud.storageKey to "Cloud",
                SttMode.OnDevice.storageKey to "On-device",
            ),
            selected = mode.storageKey,
            onSelect = { viewModel.saveSttMode(SttMode.fromStorage(it)) },
            icons = listOf(Icons.Default.CloudQueue, Icons.Default.PhoneAndroid),
        )
        Spacer(Modifier.height(Spacing.xl))

        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            label = "sttSections",
        ) { current ->
            when (current) {
                SttMode.Cloud -> SttCloudSection(viewModel, onOpenCloudModels)
                SttMode.OnDevice -> SttOnDeviceSection()
            }
        }
    }
}

@Composable
private fun SttCloudSection(viewModel: SettingsViewModel, onOpenCloudModels: () -> Unit) {
    val apiKey by viewModel.apiKey.collectAsState()
    val selectedId by viewModel.sttCloudModel.collectAsState()
    val hasKey = apiKey.isNotBlank()

    Column {
        PageSection("How it works", null)
        Text(
            "Tap the mic by the model on the chat bar, speak, and your words drop into the box " +
                "to fix and send. Transcription always runs on OpenRouter using your Cloud-models " +
                "key — separate from whichever model answers the chat.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.xl))
        SttKeyStatusCard(hasKey = hasKey, onOpenCloudModels = onOpenCloudModels)

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Model", "Which model transcribes your voice")
        SttCloudModelList(
            models = SttCatalog.CLOUD_MODELS,
            selectedId = selectedId,
            onSelect = viewModel::saveSttCloudModel,
        )
        Spacer(Modifier.height(Spacing.m))
        Text(
            "Prices are per minute of audio, billed by OpenRouter to your key. " +
                "One red \$ is cheap; two or three green \$ cost more. " +
                "Best is the lowest word-error rate on Artificial Analysis evals.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SttCloudModelList(
    models: List<SttModel>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
        models.forEachIndexed { index, model ->
            val selected = model.id == selectedId
            Surface(
                onClick = { onSelect(model.id) },
                shape = groupedItemShape(index, models.size),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(start = Spacing.base, end = Spacing.base, top = Spacing.m, bottom = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedPolygonShape(MaterialShapes.Cookie6Sided))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiaryContainer
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.GraphicEq, null, Modifier.size(20.dp),
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Spacer(Modifier.width(Spacing.base))
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Text(
                                model.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (model.isBest) SttBestBadge()
                            SttCostMark(model.costTier)
                        }
                        Text(
                            "${model.provider} · ${model.pricing}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            model.blurb,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selected) {
                        Spacer(Modifier.width(Spacing.s))
                        Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SttBestBadge() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            "Best",
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun SttCostMark(tier: SttCostTier) {
    val color = when (tier) {
        SttCostTier.Cheap -> MaterialTheme.colorScheme.error
        SttCostTier.Moderate, SttCostTier.Expensive -> MaterialTheme.colorScheme.diffAdded
    }
    val label = when (tier) {
        SttCostTier.Cheap -> "Cheap"
        SttCostTier.Moderate -> "A bit expensive"
        SttCostTier.Expensive -> "Very expensive"
    }
    Text(
        text = "\$".repeat(tier.dollars),
        color = color,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

/** Present-or-missing OpenRouter key, with a jump to Cloud models when it's missing. */
@Composable
private fun SttKeyStatusCard(hasKey: Boolean, onOpenCloudModels: () -> Unit) {
    if (hasKey) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text("OpenRouter key connected", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "The mic is available in chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        Surface(
            onClick = onOpenCloudModels,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text("OpenRouter key needed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(
                        "Add it under OpenRouter in Models to turn on the chat mic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open OpenRouter in Models", tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

@Composable
private fun SttOnDeviceSection() {
    Column {
        PageSection("On-device", null)
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedPolygonShape(MaterialShapes.Flower))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Schedule, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                Spacer(Modifier.height(Spacing.base))
                Text("Coming soon", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "On-device transcription that never leaves the phone is on the way. For now, use Cloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
