@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.echoflow.data.SttCatalog
import com.echoflow.data.SttCostTier
import com.echoflow.data.SttMode
import com.echoflow.data.SttModel
import com.echoflow.data.SystemDictationPermissions
import com.echoflow.data.SystemDictationSetup
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.diffAdded

/** Cloud dictation settings and the on-device placeholder. */
@Composable
internal fun SpeechToTextPage(
    viewModel: SettingsViewModel,
    onOpenCloudModels: () -> Unit,
    onOpenSarvam: () -> Unit,
    onBack: () -> Unit,
) {
    val mode by viewModel.sttMode.collectAsState()

    SettingsPageScaffold(
        title = "Dictation",
        subtitle = "Dictate in chat and other apps",
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

        SystemDictationRow(viewModel)
        Spacer(Modifier.height(Spacing.xl))

        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            label = "sttSections",
        ) { current ->
            when (current) {
                SttMode.Cloud -> SttCloudSection(viewModel, onOpenCloudModels, onOpenSarvam)
                SttMode.OnDevice -> SttOnDeviceSection()
            }
        }
    }
}

@Composable
private fun SttCloudSection(viewModel: SettingsViewModel, onOpenCloudModels: () -> Unit, onOpenSarvam: () -> Unit) {
    val apiKey by viewModel.apiKey.collectAsState()
    val selectedId by viewModel.sttCloudModel.collectAsState()
    val config by viewModel.customProviderConfig.collectAsState()
    val isSarvam = selectedId == SttCatalog.SARVAM_MODEL_ID
    val hasKey = SttCatalog.apiKey(selectedId, apiKey, config).isNotBlank()

    Column {
        SttKeyStatusCard(
            hasKey = hasKey,
            provider = if (isSarvam) "Sarvam" else "OpenRouter",
            onOpenCloudModels = if (isSarvam) onOpenSarvam else onOpenCloudModels,
        )

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Model", "Which model transcribes your voice")
        SttCloudModelList(
            models = SttCatalog.availableModels(config),
            selectedId = selectedId,
            onSelect = viewModel::saveSttCloudModel,
        )
        if (SttCatalog.sarvamAvailable(config)) {
            Spacer(Modifier.height(Spacing.m))
            SttHinglishRow(viewModel)
        }
        Spacer(Modifier.height(Spacing.m))
        Text(
            "Prices are per hour of audio. Saaras is billed directly to your Sarvam key; " +
                "the rest use OpenRouter. " +
                "One red \$ is cheap; two or three green \$ cost more. " +
                "Best is the recommended dictation model.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SttHinglishRow(viewModel: SettingsViewModel) {
    val enabled by viewModel.sarvamHinglishEnabled.collectAsState()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hinglish", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Hindi dictation arrives in English letters. Other languages stay in native script.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.s))
            Switch(checked = enabled, onCheckedChange = viewModel::saveSarvamHinglishEnabled)
        }
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
                            if (model.showCostTier) SttCostMark(model.costTier)
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

/** Selected provider key status and a shortcut to its settings. */
@Composable
private fun SttKeyStatusCard(hasKey: Boolean, provider: String, onOpenCloudModels: () -> Unit) {
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
                    Text("$provider key saved", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
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
                    Text("$provider key needed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(
                        if (provider == "Sarvam") "Enable Sarvam and save its key under Custom models to turn on the chat mic."
                        else "Add it under OpenRouter in Models to turn on the chat mic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open $provider settings", tint = MaterialTheme.colorScheme.onTertiaryContainer)
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

/** Only the permission walk belongs to composition. The OS service owns everything after setup. */
@Composable
private fun SystemDictationRow(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val enabled by viewModel.systemWideDictation.collectAsState()
    val mode by viewModel.sttMode.collectAsState()
    val model by viewModel.sttCloudModel.collectAsState()
    val key by viewModel.apiKey.collectAsState()
    val config by viewModel.customProviderConfig.collectAsState()
    val ready = mode == SttMode.Cloud && SttCatalog.apiKey(model, key, config).isNotBlank()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var launchedStep by rememberSaveable { mutableIntStateOf(0) }
    val setup = remember(viewModel) { SystemDictationSetup(viewModel::saveSystemWideDictation) }
    fun finish(granted: Boolean) { setup.finish(granted && ready); step = 0; launchedStep = 0 }
    val audio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (step == 1) { if (it) step = 2 else finish(false) }
    }
    val overlay = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (step == 2) { if (Settings.canDrawOverlays(context)) step = 3 else finish(false) }
    }
    val accessibility = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (step == 3) { if (SystemDictationPermissions.accessibility(context)) step = 4 else finish(false) }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (step == 4) { if (it) step = 5 else finish(false) }
    }
    val notificationSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (step == 5) finish(SystemDictationPermissions.granted(context))
    }
    LaunchedEffect(step) {
        if (step == 0 || launchedStep == step) return@LaunchedEffect
        launchedStep = step
        try {
            when (step) {
                1 -> if (SystemDictationPermissions.microphone(context)) step = 2
                    else audio.launch(Manifest.permission.RECORD_AUDIO)
                2 -> if (Settings.canDrawOverlays(context)) step = 3
                    else overlay.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                3 -> if (SystemDictationPermissions.accessibility(context)) step = 4
                    else accessibility.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                4 -> if (SystemDictationPermissions.notifications(context)) finish(SystemDictationPermissions.granted(context))
                    else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else step = 5
                5 -> if (SystemDictationPermissions.notifications(context)) finish(SystemDictationPermissions.granted(context))
                    else notificationSettings.launch(
                        if (Build.VERSION.SDK_INT >= 26) {
                            val action = if (NotificationManagerCompat.from(context).areNotificationsEnabled())
                                Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, SystemDictationPermissions.CHANNEL)
                        }
                        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
        } catch (_: Exception) { finish(false) }
    }
    LaunchedEffect(ready) { if (!ready) finish(false) }
    DisposableEffect(lifecycle, setup) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !SystemDictationPermissions.granted(context)) setup.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("System-wide dictation", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Turn on to use dictation across your device, outside EchoFlow too.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!ready) Text("Requires Cloud mode and the selected provider’s API key.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (step != 0) Text("Complete permission setup to enable.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.s))
            Switch(checked = enabled, enabled = ready && step == 0,
                onCheckedChange = { if (it) { setup.begin(); step = 1 } else setup.cancel() },
                modifier = Modifier.semantics { contentDescription = "System-wide dictation" })
        }
    }
}
