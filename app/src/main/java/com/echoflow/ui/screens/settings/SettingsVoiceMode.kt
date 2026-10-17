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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.echoflow.data.usage.UsageKeys
import com.echoflow.data.voice.VoiceAccount
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
 * Voice mode settings: the voice, its style (Expressive or Fast), the LLM that answers, and the
 * language. Shown from the settings hub only once the ElevenLabs key has authenticated.
 */
@Composable
internal fun VoiceModePage(
    viewModel: SettingsViewModel,
    onOpenElevenLabs: () -> Unit,
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
    val verified = key.isNotEmpty() && verifiedHash == UsageKeys.hash(key)

    var showVoices by rememberSaveable { mutableStateOf(false) }
    var showLanguages by rememberSaveable { mutableStateOf(false) }
    var llmsExpanded by rememberSaveable { mutableStateOf(false) }

    SettingsPageScaffold(title = "Voice mode", subtitle = "Talk with EchoFlow · ElevenLabs", onBack = onBack) {
        if (!verified) {
            NotConnectedCard(verifying = verifying, onOpenElevenLabs = onOpenElevenLabs)
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

@Composable
private fun NotConnectedCard(verifying: Boolean, onOpenElevenLabs: () -> Unit) {
    FormCard {
        if (verifying) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(32.dp))
                Spacer(Modifier.width(Spacing.m))
                Text("Checking your ElevenLabs key…", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Text("Connect ElevenLabs", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Voice mode runs on your ElevenLabs key. Add a working key to turn it on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.m))
            Button(onClick = onOpenElevenLabs) { Text("Open ElevenLabs key") }
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

/** True when voice mode is set up: the ElevenLabs key is on and has authenticated. */
@Composable
internal fun rememberVoiceModeReady(viewModel: SettingsViewModel): Boolean {
    val providers by viewModel.customProviderConfig.collectAsState()
    val voiceViewModel: VoiceModeSettingsViewModel = composeViewModel()
    val key = if (providers.elevenLabsAvailable) providers.elevenLabsApiKey.trim() else ""
    LaunchedEffect(key) { voiceViewModel.bind(key) }
    val verifiedHash by voiceViewModel.verifiedKeyHash.collectAsState()
    return key.isNotEmpty() && verifiedHash == UsageKeys.hash(key)
}
