package com.echoflow.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.echoflow.R

/**
 * Bundled type families for EchoFlow.
 *
 * We deliberately move off the platform Roboto: its flat, mechanical letterforms fight the app's
 * rounded, expressive M3 surfaces. The pairing here is chosen so the type agrees with that geometry
 * and reads premium.
 *
 * - [Figtree]: reading / UI voice for body, labels and titles. Rounded terminals and a tall
 *   x-height echo the rounded corners across the app and stay calm over long model answers.
 * - [Bricolage]: expressive display voice for headlines and the punchy chrome — real character where
 *   Roboto Black only offered weight.
 * - [JetBrainsMono]: code voice, warmer and more legible than the default device monospace.
 */
val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

val Bricolage = FontFamily(
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_bold, FontWeight.Bold),
    // Bricolage tops out at ExtraBold (800); map heavier requests (ExtraBold/Black) here.
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
    Font(R.font.bricolage_extrabold, FontWeight.Black),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(R.font.jetbrainsmono_bold, FontWeight.Bold),
)
