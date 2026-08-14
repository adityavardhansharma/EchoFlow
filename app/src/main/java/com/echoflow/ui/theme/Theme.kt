@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive theme entry point.
 *
 * Uses [MaterialExpressiveTheme] with the expressive [MotionScheme] so every transition in the app
 * is driven by the physics-based spring system Google introduced with Material 3 Expressive.
 * The public signature is unchanged so the rest of the app keeps wiring through [EchoFlowTheme].
 */
@Composable
fun EchoFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeName: String = "monochrome",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        themeName == "dynamic" && supportsDynamic ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        themeName == "dynamic" ->
            if (darkTheme) OceanDark else OceanLight // pre-Android 12: no Material You sampling
        themeName == "ocean" -> if (darkTheme) OceanDark else OceanLight
        themeName == "forest" -> if (darkTheme) ForestDark else ForestLight
        themeName == "sunset" -> if (darkTheme) SunsetDark else SunsetLight
        themeName == "lavender" -> if (darkTheme) LavenderDark else LavenderLight
        themeName == "rose" -> if (darkTheme) RoseDark else RoseLight
        else -> if (darkTheme) MonochromeDark else MonochromeLight // unknown theme names
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes,
        typography = Typography,
        content = content,
    )
}
