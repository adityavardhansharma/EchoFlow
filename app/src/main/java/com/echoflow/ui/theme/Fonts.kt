package com.echoflow.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.echoflow.R

/**
 * Bundled type families for EchoFlow.
 *
 * The goal here is a look that is unmistakably EchoFlow while staying calm over long, streamed
 * answers. Identity comes from the display voice and small consistent details — never from bending
 * the reading font into something quirky, which is what made the previous pairing feel soft and
 * generic on-device.
 *
 * - [HankenGrotesk]: reading / UI voice for body, labels and titles. A crisp neutral grotesque with
 *   subtly warm, designed letterforms — tighter and sharper than the old rounded default so long
 *   answers read premium instead of juvenile, without shouting.
 * - [SpaceGrotesk]: expressive display voice for headlines and the punchy chrome. Its signature
 *   geometric cuts give the biggest moments a recognizable EchoFlow character.
 * - [JetBrainsMono]: code voice, warmer and more legible than the default device monospace.
 */
val HankenGrotesk = FontFamily(
    Font(R.font.hankengrotesk_regular, FontWeight.Normal),
    Font(R.font.hankengrotesk_medium, FontWeight.Medium),
    Font(R.font.hankengrotesk_semibold, FontWeight.SemiBold),
    Font(R.font.hankengrotesk_bold, FontWeight.Bold),
)

val SpaceGrotesk = FontFamily(
    Font(R.font.spacegrotesk_medium, FontWeight.Medium),
    Font(R.font.spacegrotesk_semibold, FontWeight.SemiBold),
    Font(R.font.spacegrotesk_bold, FontWeight.Bold),
    // Space Grotesk tops out at Bold (700); map heavier requests (ExtraBold/Black) here.
    Font(R.font.spacegrotesk_bold, FontWeight.ExtraBold),
    Font(R.font.spacegrotesk_bold, FontWeight.Black),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(R.font.jetbrainsmono_bold, FontWeight.Bold),
)
