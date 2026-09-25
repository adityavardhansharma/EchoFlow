@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.echoflow.data.DeepgramDictation
import com.echoflow.data.DictationVocabulary
import com.echoflow.data.SttCatalog
import com.echoflow.data.SttModel
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing

/**
 * Custom vocabulary for models that accept it, shown directly under the model list while one is
 * selected. Gemini 3.5 Transcribe receives it as `custom_vocabulary`; Deepgram Nova-3 receives the
 * same terms as keyterms, so the card is labelled after whichever model will use them. Terms can be typed or pasted comma-separated ("Aditya, Jyoti") and turn into
 * chips as soon as a separator lands, so the list always shows exactly what is sent. The same
 * list applies to in-app and system-wide dictation.
 */
@Composable
internal fun SttVocabularyCard(viewModel: SettingsViewModel, model: SttModel) {
    val terms by viewModel.sttVocabulary.collectAsState()
    val keyterms = model.id == SttCatalog.DEEPGRAM_MODEL_ID
    var draft by rememberSaveable { mutableStateOf("") }
    val full = terms.size >= DictationVocabulary.MAX_TERMS

    fun commit(raw: String = draft) {
        if (raw.isNotBlank()) viewModel.addSttVocabulary(raw)
        draft = ""
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.base).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedPolygonShape(MaterialShapes.Sunny))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Spellcheck, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.width(Spacing.base))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (keyterms) "Vocabulary · Keyterms" else "Custom vocabulary",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (keyterms) "Words and phrases Deepgram listens for and spells your way."
                        else "Names and terms Gemini should hear and spell your way.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Text(
                    "${terms.size} / ${DictationVocabulary.MAX_TERMS}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (full) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics {
                        contentDescription = "${terms.size} of ${DictationVocabulary.MAX_TERMS} words"
                    },
                )
            }

            Spacer(Modifier.height(Spacing.m))
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = terms.isEmpty(),
                transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                label = "sttVocabularyTerms",
            ) { empty ->
                if (empty) {
                    Text(
                        "Add people, places, brands, or jargon it keeps getting wrong — " +
                            "for example: Aditya, Jyoti, EchoFlow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        terms.forEach { term -> SttVocabularyChip(term) { viewModel.removeSttVocabulary(term) } }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.m))
            OutlinedTextField(
                value = draft,
                onValueChange = { value ->
                    // A typed or pasted separator commits everything before it as chips; the
                    // unfinished tail stays in the field.
                    val cut = value.indexOfLast { it == ',' || it == '\n' || it == ';' }
                    if (cut >= 0) {
                        commit(value.substring(0, cut))
                        draft = value.substring(cut + 1).trimStart()
                    } else {
                        draft = value
                    }
                },
                enabled = !full,
                singleLine = true,
                placeholder = { Text("Add words, separated by commas") },
                supportingText = if (full) {
                    { Text("Vocabulary is full. Remove a word to add another.") }
                } else null,
                trailingIcon = if (draft.isNotBlank()) {
                    {
                        FilledTonalIconButton(onClick = { commit() }, modifier = Modifier.padding(end = Spacing.xs)) {
                            Icon(Icons.Default.Add, "Add to vocabulary")
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    // Leaving the field must never silently drop a half-typed word.
                    .onFocusChanged { if (!it.isFocused && draft.isNotBlank()) commit() },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (keyterms) {
                        "Sent as Deepgram keyterms in chat and system-wide. They bias recognition toward " +
                            "these spellings; they don't rewrite what you say. Up to " +
                            "${DeepgramDictation.MAX_KEYTERMS} are sent per recording, adding ~\$0.078 / hr."
                    } else "Used for Gemini dictation in chat and system-wide.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(vertical = Spacing.s),
                )
                if (terms.isNotEmpty()) {
                    TextButton(onClick = viewModel::clearSttVocabulary) { Text("Clear all") }
                }
            }
        }
    }
}

@Composable
private fun SttVocabularyChip(term: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(term, style = MaterialTheme.typography.labelLarge) },
        trailingIcon = {
            Icon(Icons.Default.Close, "Remove $term", Modifier.size(InputChipDefaults.IconSize))
        },
        shape = MaterialTheme.shapes.small,
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = null,
    )
}
