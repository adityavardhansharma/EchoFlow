@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.echoflow.data.ImagineMedia
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.theme.Spacing

/**
 * One settings page for the Imagine surface, split the same way the surface itself is: a media
 * selector on top, then everything that applies to what you are making.
 *
 * The selector is bound to the *same* preference the composer's media toggle writes, so the
 * two never disagree about what you are currently making — and opening Settings from Imagine
 * lands on the half you were already using.
 */
@Composable
internal fun ImaginePage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val media by viewModel.imagineMedia.collectAsState()

    SettingsPageScaffold(title = "Imagine", subtitle = "Create images & video in chat", onBack = onBack) {
        ConnectedToggleRow(
            options = listOf(
                ImagineMedia.Image.storageKey to "Image",
                ImagineMedia.Video.storageKey to "Video",
            ),
            selected = media.storageKey,
            onSelect = { viewModel.saveImagineMedia(ImagineMedia.fromStorage(it)) },
            icons = listOf(Icons.Default.Image, Icons.Default.Movie),
        )
        Spacer(Modifier.height(Spacing.xl))

        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = media,
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            label = "imagineSections",
        ) { current ->
            when (current) {
                ImagineMedia.Image -> ImageGenSection(viewModel)
                ImagineMedia.Video -> VideoGenSection(viewModel)
            }
        }
    }
}
