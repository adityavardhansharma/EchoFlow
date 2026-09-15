@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoflow.ui.theme.Spacing

/** Short, human label for an OpenRouter model id, e.g. "anthropic/claude-opus-4.8" → "claude-opus-4.8". */
internal fun shortModel(id: String): String = id.substringAfterLast('/').ifBlank { id }

/** A tiny uppercase brand chip ("ECHO ADVISER" / "ECHO FUSION") so the mode is unmistakable. */
@Composable
internal fun ModeBadge(label: String, container: Color, onContainer: Color) {
    Surface(shape = CircleShape, color = container) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
            color = onContainer,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = 3.dp),
        )
    }
}
