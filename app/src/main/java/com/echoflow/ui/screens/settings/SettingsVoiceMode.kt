@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.echoflow.data.CustomModelProvider
import com.echoflow.data.usage.UsageKeys
import com.echoflow.data.voice.RealtimeCatalog
import com.echoflow.data.voice.RealtimeVoice
import com.echoflow.data.voice.VoiceAccount
import com.echoflow.data.voice.VoiceModeConfig
import com.echoflow.data.voice.VoiceProvider
import com.echoflow.data.voice.VoiceProviderKeys
import com.echoflow.data.voice.VoiceLanguages
import com.echoflow.data.voice.VoiceLlms
import com.echoflow.data.voice.VoiceOption
import com.echoflow.data.voice.VoiceStyle
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LLMS_COLLAPSED = 6

/**
 * Voice mode settings: who runs calls (ElevenLabs, Gemini Live or OpenAI Realtime), then that
 * provider's voice, model and language. For ElevenLabs that's also the voice style and the LLM.
 * Shown from the settings hub once any of the three can run a call.
 */
@Composable
internal fun VoiceModePage(
    viewModel: SettingsViewModel,
    onOpenElevenLabs: () -> Unit,
    onOpenGemini: () -> Unit,
    onOpenOpenAi: () -> Unit,
    onBack: () -> Unit,
    voiceViewModel: VoiceModeSettingsViewModel = composeViewModel(),
) {
    val providers by viewModel.customProviderConfig.collectAsState()
    val key = if (providers.elevenLabsAvailable) providers.elevenLabsApiKey.trim() else ""
    LaunchedEffect(key) { voiceViewModel.bind(key) }

    val config by voiceViewModel.config.collectAsState()
    val account by voiceViewModel.account.collectAsState()
    val verifiedHash by voiceViewModel.verifiedKeyHash.collectAsState()
    val verifying by voiceViewModel.verifying.collectAsState()
    val llms by voiceViewModel.llms.collectAsState()
    val llmsLoading by voiceViewModel.llmsLoading.collectAsState()
    val keys = VoiceProviderKeys.from(providers, verifiedHash, UsageKeys::hash)
    val picked = config.provider

    var showVoices by rememberSaveable { mutableStateOf(false) }
    var showRealtimeVoices by rememberSaveable { mutableStateOf(false) }
    var showLanguages by rememberSaveable { mutableStateOf(false) }
    var llmsExpanded by rememberSaveable { mutableStateOf(false) }

    SettingsPageScaffold(title = "Voice mode", subtitle = "Talk with EchoFlow · ${picked.label}", onBack = onBack) {
        PageSection("Provider", "Who listens and answers. The call screen is the same for all three.")
        Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
            VoiceProvider.entries.forEachIndexed { index, provider ->
                ProviderRow(
                    provider = provider,
                    selected = provider == picked,
                    status = when {
                        keys.ready(provider) -> null
                        provider == VoiceProvider.ElevenLabs && verifying -> "Checking your key…"
                        else -> "Needs your ${provider.label} key"
                    },
                    index = index, count = VoiceProvider.entries.size,
                    onClick = { voiceViewModel.saveProvider(provider) },
                )
            }
        }
        Spacer(Modifier.height(Spacing.s))
        Text(
            picked.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
        Spacer(Modifier.height(Spacing.xl))

        if (!keys.ready(picked)) {
            NotConnectedCard(
                provider = picked,
                verifying = picked == VoiceProvider.ElevenLabs && verifying,
                fallback = keys.resolve(picked),
                onOpenKey = when (picked) {
                    VoiceProvider.ElevenLabs -> onOpenElevenLabs
                    VoiceProvider.Gemini -> onOpenGemini
                    VoiceProvider.OpenAi -> onOpenOpenAi
                },
            )
            return@SettingsPageScaffold
        }

        if (picked.isRealtime) {
            RealtimeSettings(
                provider = picked,
                config = config,
                onOpenVoices = { showRealtimeVoices = true },
                onPickModel = { voiceViewModel.saveRealtimeModel(picked, it) },
                onOpenLanguages = { showLanguages = true },
            )
            return@SettingsPageScaffold
        }

        AccountCard(account)
        Spacer(Modifier.height(Spacing.xl))

        PageSection("Voice", "Who you hear. Search your voices or ElevenLabs' Voice Library.")
        PickerRow(
            icon = Icons.Default.RecordVoiceOver,
            polygon = MaterialShapes.Cookie9Sided,
            title = config.voiceName?.takeIf { config.voiceId != null } ?: "Default voice",
            subtitle = if (config.voiceId == null) "ElevenLabs' default agent voice" else "Tap to change",
            index = 0, count = 1,
            onClick = {
                voiceViewModel.openPicker()
                showVoices = true
            },
        )
        Spacer(Modifier.height(Spacing.xl))

        PageSection("Voice style")
        ConnectedToggleRow(
            options = VoiceStyle.entries.map { it.name to it.label },
            selected = config.style.name,
            onSelect = { voiceViewModel.saveStyle(VoiceStyle.fromStorage(it)) },
            icons = listOf(Icons.Default.AutoAwesome, Icons.Default.Bolt),
        )
        Spacer(Modifier.height(Spacing.s))
        Text(
            config.style.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
        if (config.style == VoiceStyle.Expressive && config.effectiveStyle == VoiceStyle.Fast) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "This voice is a professional clone, so it uses Fast to keep its character.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
        }
        Spacer(Modifier.height(Spacing.xl))

        PageSection("Model", "The LLM that answers in voice mode. ElevenLabs runs and bills it.")
        if (llmsLoading && llms.isEmpty()) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = Spacing.s))
        }
        val shown = when {
            llmsExpanded -> llms
            else -> {
                val head = llms.take(LLMS_COLLAPSED)
                // Keep the picked model visible even when it's further down the list.
                if (head.none { it.id == config.llm }) head + llms.filter { it.id == config.llm } else head
            }
        }
        val hasMore = llms.size > shown.size || llmsExpanded
        val rowCount = shown.size + if (hasMore) 1 else 0
        Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
            shown.forEachIndexed { index, llm ->
                ChoiceRow(
                    title = llm.label,
                    subtitle = llm.id,
                    selected = llm.id == config.llm,
                    index = index, count = rowCount,
                    onClick = { voiceViewModel.saveLlm(llm.id) },
                )
            }
            if (hasMore) {
                ShowMoreRow(
                    expanded = llmsExpanded,
                    expandText = "Show all ${llms.size} models",
                    collapseText = "Show fewer",
                    index = rowCount - 1, count = rowCount,
                    onClick = { llmsExpanded = !llmsExpanded },
                )
            }
        }
        Spacer(Modifier.height(Spacing.xl))

        PageSection("Language", "What the agent listens for and answers in.")
        PickerRow(
            icon = Icons.Default.Language,
            polygon = MaterialShapes.Clover4Leaf,
            title = VoiceLanguages.label(config.language),
            subtitle = "It still follows you if you switch mid-chat",
            index = 0, count = 1,
            onClick = { showLanguages = true },
        )
        Spacer(Modifier.height(Spacing.xl))

        FormCard {
            Text("How voice mode works", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Spacing.s))
            listOf(
                "Tap the waveform in an empty composer to start. You can talk, type, or both.",
                "Your turns are saved into the chat and shown when the call ends.",
                "ElevenLabs bills each minute the call is open, including the LLM.",
                "A call ends by itself after 5 minutes of quiet.",
            ).forEach { line ->
                Text(
                    "•  $line",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }

    if (showVoices) {
        VoicePickerSheet(
            viewModel = voiceViewModel,
            selectedId = config.voiceId,
            libraryAllowed = account?.isFree != true,
            onDismiss = {
                voiceViewModel.stopPreview()
                showVoices = false
            },
        )
    }
    if (showRealtimeVoices && picked.isRealtime) {
        RealtimeVoiceSheet(
            provider = picked,
            selected = config.realtimeVoice(picked),
            onSelect = {
                voiceViewModel.saveRealtimeVoice(picked, it)
                showRealtimeVoices = false
            },
            onDismiss = { showRealtimeVoices = false },
        )
    }
    if (showLanguages) {
        LanguageSheet(
            selected = config.language,
            onSelect = {
                voiceViewModel.saveLanguage(it)
                showLanguages = false
            },
            onDismiss = { showLanguages = false },
        )
    }
}

/** One provider to pick: its logo, name, and what's missing when it can't run yet. */
@Composable
private fun ProviderRow(
    provider: VoiceProvider,
    selected: Boolean,
    status: String?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = groupedItemShape(index, count),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderLogo(directProviderBrand(provider.keyProvider()), size = 40.dp)
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(provider.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    status ?: provider.engine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        status != null -> MaterialTheme.colorScheme.error
                        selected -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun VoiceProvider.keyProvider(): CustomModelProvider = when (this) {
    VoiceProvider.ElevenLabs -> CustomModelProvider.ElevenLabs
    VoiceProvider.Gemini -> CustomModelProvider.Gemini
    VoiceProvider.OpenAi -> CustomModelProvider.OpenAi
}

/** What runs the call, in a few words, for the provider row. */
private fun VoiceProvider.engine(): String = when (this) {
    VoiceProvider.ElevenLabs -> "Agents · your pick of LLM and voice"
    VoiceProvider.Gemini -> "Gemini Live · speech to speech"
    VoiceProvider.OpenAi -> "GPT Realtime · speech to speech"
}

@Composable
private fun NotConnectedCard(
    provider: VoiceProvider,
    verifying: Boolean,
    /** Who runs calls meanwhile, if anyone. */
    fallback: VoiceProvider?,
    onOpenKey: () -> Unit,
) {
    FormCard {
        if (verifying) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(32.dp))
                Spacer(Modifier.width(Spacing.m))
                Text("Checking your ElevenLabs key…", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Text("Connect ${provider.label}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                when (provider) {
                    VoiceProvider.ElevenLabs -> "ElevenLabs calls run on your ElevenLabs key. Add a working key to use it."
                    VoiceProvider.Gemini -> "Gemini Live runs on your Gemini API key. Add it and switch Gemini on."
                    VoiceProvider.OpenAi -> "GPT Realtime runs on your OpenAI API key. Add it and switch OpenAI on."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (fallback != null && fallback != provider) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Until then, calls use ${fallback.label}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(Spacing.m))
            Button(onClick = onOpenKey) { Text("Open ${provider.label} key") }
        }
    }
}

/** Gemini Live and OpenAI Realtime: a built-in voice, the realtime model, and the language. */
@Composable
private fun RealtimeSettings(
    provider: VoiceProvider,
    config: VoiceModeConfig,
    onOpenVoices: () -> Unit,
    onPickModel: (String) -> Unit,
    onOpenLanguages: () -> Unit,
) {
    val voices = RealtimeCatalog.voices(provider)
    val voice = voices.firstOrNull { it.id == config.realtimeVoice(provider) }
    PageSection("Voice", "Who you hear: one of ${provider.label}'s ${voices.size} built-in voices.")
    PickerRow(
        icon = Icons.Default.RecordVoiceOver,
        polygon = MaterialShapes.Cookie9Sided,
        title = voice?.name ?: config.realtimeVoice(provider),
        subtitle = voice?.traits ?: "Tap to change",
        index = 0, count = 1,
        onClick = onOpenVoices,
    )
    Spacer(Modifier.height(Spacing.xl))

    val models = RealtimeCatalog.models(provider)
    val selectedModel = config.realtimeModel(provider)
    PageSection("Model", "It hears you and speaks back itself, so there's no separate LLM or voice model.")
    Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
        models.forEachIndexed { index, model ->
            ChoiceRow(
                title = model.label,
                subtitle = model.blurb,
                selected = model.id == selectedModel,
                index = index, count = models.size,
                onClick = { onPickModel(model.id) },
            )
        }
    }
    Spacer(Modifier.height(Spacing.xl))

    PageSection("Language", "The language it starts in.")
    PickerRow(
        icon = Icons.Default.Language,
        polygon = MaterialShapes.Clover4Leaf,
        title = VoiceLanguages.label(config.language),
        subtitle = "It still follows you if you switch mid-chat",
        index = 0, count = 1,
        onClick = onOpenLanguages,
    )
    Spacer(Modifier.height(Spacing.xl))

    FormCard {
        Text("How voice mode works", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.s))
        val billing = when (provider) {
            VoiceProvider.Gemini ->
                "Google bills the audio it hears and speaks, by token. Spend shows an estimate until Google publishes 3.8 Live prices."
            else -> "OpenAI bills the audio it hears and speaks, by token. Spend shows each call."
        }
        listOf(
            "Tap the waveform in an empty composer to start. You can talk, type, or both.",
            "Your turns are saved into the chat and shown when the call ends.",
            billing,
            "A call ends by itself after 5 minutes of quiet.",
        ).forEach { line ->
            Text(
                "•  $line",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

/** Gemini's or OpenAI's built-in voices. They have no preview clips, so it's a plain list. */
@Composable
private fun RealtimeVoiceSheet(
    provider: VoiceProvider,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val all = RealtimeCatalog.voices(provider)
    val shown = all.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.traits.contains(query.trim(), ignoreCase = true)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.base)) {
            Text(
                "${provider.label} voices",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
            Spacer(Modifier.height(Spacing.m))
            if (all.size > 12) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search by name or style") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.m))
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.base, end = Spacing.base, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
            modifier = Modifier.heightIn(min = 240.dp),
        ) {
            itemsIndexed(shown, key = { _, v -> v.id }) { index, voice ->
                ChoiceRow(
                    title = voice.name,
                    subtitle = voice.traits,
                    selected = voice.id.equals(selected, ignoreCase = true),
                    index = index, count = shown.size,
                    onClick = { onSelect(voice.id) },
                )
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No voices match \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.base),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCard(account: VoiceAccount?) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l)) {
            Text(
                account?.let { "${it.planLabel} plan" } ?: "ElevenLabs connected",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.xs))
            val detail = when {
                account == null -> "This key can't read its plan, so credits aren't shown."
                account.creditLimit <= 0 -> "No credits on this plan."
                else -> buildString {
                    val format = NumberFormat.getIntegerInstance()
                    append("${format.format(account.creditsLeft)} of ${format.format(account.creditLimit)} credits left")
                    account.resetsAtUnix?.let {
                        append(" · resets ")
                        append(SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(it * 1000)))
                    }
                }
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (account != null && account.creditLimit > 0) {
                Spacer(Modifier.height(Spacing.m))
                LinearWavyProgressIndicator(
                    progress = { (account.creditsLeft.toFloat() / account.creditLimit).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (account?.isFree == true) {
                Spacer(Modifier.height(Spacing.s))
                Text(
                    "On the free plan, Voice Library voices can't be used by agents. Your own and default voices work.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** A row that opens a picker: shaped badge, current value, chevron. */
@Composable
private fun PickerRow(
    icon: ImageVector,
    polygon: RoundedPolygon,
    title: String,
    subtitle: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Badge(icon, polygon, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = groupedItemShape(index, count),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, polygon: RoundedPolygon, container: Color, content: Color) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedPolygonShape(polygon))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = content)
    }
}

/** Searchable voices from the user's account and the public Voice Library, with previews. */
@Composable
private fun VoicePickerSheet(
    viewModel: VoiceModeSettingsViewModel,
    selectedId: String?,
    libraryAllowed: Boolean,
    onDismiss: () -> Unit,
) {
    val state by viewModel.picker.collectAsState()
    val previewing by viewModel.previewing.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.voices.size) {
        if (nearEnd && state.voices.isNotEmpty()) viewModel.loadMore()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = Spacing.base)) {
            Text("Choose a voice", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.xs))
            Spacer(Modifier.height(Spacing.m))
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search by name, accent or style") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Close, "Clear search") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.m))
            ConnectedToggleRow(
                options = listOf(VoiceSource.Mine.name to "My voices", VoiceSource.Library.name to "Voice Library"),
                selected = state.source.name,
                onSelect = { viewModel.setSource(VoiceSource.valueOf(it)) },
                icons = listOf(Icons.Default.Person, Icons.Default.LibraryMusic),
            )
            if (state.source == VoiceSource.Library && !libraryAllowed) {
                Spacer(Modifier.height(Spacing.s))
                Text(
                    "Your free plan can browse the library, but agents can only speak with library voices on a paid plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.error?.let {
                Spacer(Modifier.height(Spacing.s))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            AnimatedVisibility(visible = state.loading && state.voices.isEmpty()) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = Spacing.m))
            }
        }
        Spacer(Modifier.height(Spacing.s))
        val includeDefault = state.source == VoiceSource.Mine && state.query.isBlank()
        val total = state.voices.size + if (includeDefault) 1 else 0
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = Spacing.base, end = Spacing.base, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
            modifier = Modifier.heightIn(min = 240.dp),
        ) {
            if (includeDefault) {
                item(key = "default") {
                    VoiceRow(
                        name = "Default voice",
                        traits = "ElevenLabs' default agent voice",
                        selected = selectedId == null,
                        busy = false,
                        canPreview = false,
                        previewing = false,
                        index = 0, count = total,
                        onPreview = {},
                        onPick = {
                            viewModel.useDefaultVoice()
                            onDismiss()
                        },
                    )
                }
            }
            itemsIndexed(state.voices, key = { _, v -> v.voiceId }) { i, voice ->
                val index = i + if (includeDefault) 1 else 0
                val blocked = voice.library != null && !libraryAllowed
                VoiceRow(
                    name = voice.name,
                    traits = voiceTraits(voice),
                    selected = voice.voiceId == selectedId,
                    busy = state.picking == voice.voiceId,
                    canPreview = voice.previewUrl != null,
                    previewing = previewing == voice.voiceId,
                    enabled = !blocked,
                    index = index, count = total,
                    onPreview = { viewModel.togglePreview(voice) },
                    onPick = { viewModel.pickVoice(voice, onDone = onDismiss) },
                )
            }
            if (state.loading && state.voices.isNotEmpty()) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(Spacing.base), contentAlignment = Alignment.Center) {
                        LoadingIndicator(Modifier.size(36.dp))
                    }
                }
            }
            if (!state.loading && state.voices.isEmpty() && state.error == null) {
                item(key = "empty") {
                    Text(
                        if (state.query.isBlank()) "No voices here yet." else "No voices match \"${state.query}\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.base),
                    )
                }
            }
        }
    }
}

private fun voiceTraits(voice: VoiceOption): String = voice.traits.ifBlank {
    voice.description?.take(80) ?: (voice.category ?: "")
}

@Composable
private fun VoiceRow(
    name: String,
    traits: String,
    selected: Boolean,
    busy: Boolean,
    canPreview: Boolean,
    previewing: Boolean,
    index: Int,
    count: Int,
    onPreview: () -> Unit,
    onPick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onPick,
        enabled = enabled && !busy,
        shape = groupedItemShape(index, count),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = Spacing.s, end = Spacing.base, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreview, enabled = canPreview) {
                Icon(
                    if (previewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (previewing) "Stop preview" else "Play preview of $name",
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (traits.isNotBlank()) {
                    Text(
                        traits,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                busy -> LoadingIndicator(Modifier.size(28.dp))
                selected -> Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                else -> Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LanguageSheet(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            "Language",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.l),
        )
        Spacer(Modifier.height(Spacing.m))
        val options = VoiceLanguages.options
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.base, end = Spacing.base, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
        ) {
            itemsIndexed(options, key = { _, o -> o.first }) { index, (code, label) ->
                ChoiceRow(
                    title = label,
                    subtitle = null,
                    selected = code == selected,
                    index = index, count = options.size,
                    onClick = { onSelect(code) },
                )
            }
        }
    }
}

/**
 * Which voice providers can run a call: an ElevenLabs key that has authenticated, or a Gemini or
 * OpenAI key that's switched on. Voice mode shows in the hub when any of them can.
 */
@Composable
internal fun rememberVoiceProviderKeys(viewModel: SettingsViewModel): VoiceProviderKeys {
    val providers by viewModel.customProviderConfig.collectAsState()
    val voiceViewModel: VoiceModeSettingsViewModel = composeViewModel()
    val key = if (providers.elevenLabsAvailable) providers.elevenLabsApiKey.trim() else ""
    LaunchedEffect(key) { voiceViewModel.bind(key) }
    val verifiedHash by voiceViewModel.verifiedKeyHash.collectAsState()
    return VoiceProviderKeys.from(providers, verifiedHash, UsageKeys::hash)
}

/** The hub row's subtitle: the model and voice of whoever runs calls now. */
internal fun voiceModeHubSubtitle(config: VoiceModeConfig, keys: VoiceProviderKeys): String =
    when (val provider = keys.resolve(config.provider)) {
        null, VoiceProvider.ElevenLabs -> VoiceLlms.label(config.llm) + " · " +
            (config.voiceName?.takeIf { config.voiceId != null } ?: "Default voice")
        else -> RealtimeCatalog.modelLabel(config.realtimeModel(provider)) + " · " +
            RealtimeVoice(config.realtimeVoice(provider), "").name
    }
