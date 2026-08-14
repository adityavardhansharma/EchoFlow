package com.echoflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.LocalReducedMotionOverride

/**
 * Anchors [PlusMenu] the same way the composer does: a sibling popup inside the anchor [Box].
 */
@Composable
internal fun PlusMenuHarness(
  expanded: Boolean,
  modifier: Modifier = Modifier,
  layoutDirection: LayoutDirection = LayoutDirection.Ltr,
  reducedMotion: Boolean? = null,
  anchorAlignment: Alignment = Alignment.BottomStart,
) {
  val menu: @Composable () -> Unit = {
    Box(
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface),
    ) {
      Box(
        Modifier
          .align(anchorAlignment)
          .padding(16.dp),
      ) {
        Box(
          Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
        )
        PlusMenu(
          expanded = expanded,
          onDismiss = {},
          showImage = true,
          showFiles = true,
          webSearchOn = false,
          deepResearchOn = false,
          dataAgentOn = false,
          dataAgentAvailable = true,
          echoAdviserOn = false,
          echoFusionOn = false,
          echoAgentOn = false,
          echoAdviserAvailable = true,
          echoFusionAvailable = true,
          echoAgentAvailable = true,
          browserFlowOn = false,
          browserFlowAvailable = true,
          artifactOn = true,
          onImage = {},
          onFiles = {},
          onToggleWebSearch = {},
          onToggleDeepResearch = {},
          onToggleDataAgent = {},
          onToggleEchoAdviser = {},
          onToggleEchoFusion = {},
          onToggleEchoAgent = {},
          onToggleBrowserFlow = {},
          onToggleArtifact = {},
        )
      }
    }
  }

  CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
    if (reducedMotion == null) {
      menu()
    } else {
      CompositionLocalProvider(LocalReducedMotionOverride provides reducedMotion) {
        menu()
      }
    }
  }
}
