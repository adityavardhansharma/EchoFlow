package com.echoflow.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Material 3 Expressive color system.
 *
 * Every palette is built through [expressiveLight] / [expressiveDark], which derive a full tonal
 * surface ladder (surfaceContainerLowest -> surfaceContainerHighest, surfaceBright/Dim) plus
 * neutral roles from a small set of brand seeds. Layering by tonal surface — rather than the
 * `.copy(alpha = ...)` translucency the old UI relied on — is what gives the redesign real depth.
 */

private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)

// Shared error roles (Expressive baseline) ------------------------------------------------------
private val ErrLight = Color(0xFFBA1A1A)
private val OnErrLight = Color(0xFFFFFFFF)
private val ErrContLight = Color(0xFFFFDAD6)
private val OnErrContLight = Color(0xFF410002)
private val ErrDark = Color(0xFFFFB4AB)
private val OnErrDark = Color(0xFF690005)
private val ErrContDark = Color(0xFF93000A)
private val OnErrContDark = Color(0xFFFFDAD6)

// Diff "added" signal --------------------------------------------------------------------------
// Removals reuse the theme's `error` role; additions have no Material slot, so — exactly like the
// shared error baseline above — they get a fixed semantic green (not a brand seed) so a `+N`
// always reads as "added" in every palette. The shade flips with surface luminance to stay legible
// on both light and dark bases, even under a manual (non-system) theme override.
private val AddedLight = Color(0xFF2E7D32)
private val AddedDark = Color(0xFF7BD88E)

/** The green a diff addition (`+N`) is tinted with, chosen for the current scheme's brightness. */
val ColorScheme.diffAdded: Color
    get() = if (surface.luminance() < 0.5f) AddedDark else AddedLight

private fun expressiveLight(
    primary: Color, onPrimary: Color, primaryContainer: Color, onPrimaryContainer: Color,
    secondary: Color, secondaryContainer: Color, onSecondaryContainer: Color,
    tertiary: Color, tertiaryContainer: Color, onTertiaryContainer: Color,
    /** Near-white, lightly brand-tinted base surface. */
    surfaceBase: Color,
    /** Near-black, lightly brand-tinted ink used for text and neutral derivation. */
    ink: Color,
): ColorScheme = lightColorScheme(
    primary = primary, onPrimary = onPrimary,
    primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
    inversePrimary = lerp(primary, White, 0.4f),
    secondary = secondary, onSecondary = White,
    secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary, onTertiary = White,
    tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
    background = surfaceBase, onBackground = ink,
    surface = surfaceBase, onSurface = ink,
    surfaceVariant = lerp(surfaceBase, ink, 0.08f),
    onSurfaceVariant = lerp(ink, surfaceBase, 0.36f),
    surfaceTint = primary,
    inverseSurface = lerp(ink, Black, 0.05f), inverseOnSurface = lerp(surfaceBase, White, 0.4f),
    outline = lerp(ink, surfaceBase, 0.55f),
    outlineVariant = lerp(ink, surfaceBase, 0.80f),
    surfaceBright = lerp(surfaceBase, White, 0.5f),
    surfaceDim = lerp(surfaceBase, ink, 0.11f),
    surfaceContainerLowest = White,
    surfaceContainerLow = lerp(surfaceBase, ink, 0.02f),
    surfaceContainer = lerp(surfaceBase, ink, 0.045f),
    surfaceContainerHigh = lerp(surfaceBase, ink, 0.07f),
    surfaceContainerHighest = lerp(surfaceBase, ink, 0.10f),
    error = ErrLight, onError = OnErrLight,
    errorContainer = ErrContLight, onErrorContainer = OnErrContLight,
    scrim = Black,
)

private fun expressiveDark(
    primary: Color, onPrimary: Color, primaryContainer: Color, onPrimaryContainer: Color,
    secondary: Color, secondaryContainer: Color, onSecondaryContainer: Color,
    tertiary: Color, tertiaryContainer: Color, onTertiaryContainer: Color,
    /** Near-black, lightly brand-tinted base surface. */
    surfaceBase: Color,
    /** Near-white, lightly brand-tinted paper used for text and neutral derivation. */
    paper: Color,
): ColorScheme = darkColorScheme(
    primary = primary, onPrimary = onPrimary,
    primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
    inversePrimary = lerp(primary, Black, 0.35f),
    secondary = secondary, onSecondary = lerp(surfaceBase, Black, 0.2f),
    secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary, onTertiary = lerp(surfaceBase, Black, 0.2f),
    tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
    background = surfaceBase, onBackground = paper,
    surface = surfaceBase, onSurface = paper,
    surfaceVariant = lerp(surfaceBase, paper, 0.12f),
    onSurfaceVariant = lerp(paper, surfaceBase, 0.42f),
    surfaceTint = primary,
    inverseSurface = paper, inverseOnSurface = lerp(surfaceBase, Black, 0.1f),
    outline = lerp(paper, surfaceBase, 0.58f),
    outlineVariant = lerp(paper, surfaceBase, 0.78f),
    surfaceBright = lerp(surfaceBase, paper, 0.16f),
    surfaceDim = surfaceBase,
    surfaceContainerLowest = lerp(surfaceBase, Black, 0.4f),
    surfaceContainerLow = lerp(surfaceBase, paper, 0.03f),
    surfaceContainer = lerp(surfaceBase, paper, 0.05f),
    surfaceContainerHigh = lerp(surfaceBase, paper, 0.08f),
    surfaceContainerHighest = lerp(surfaceBase, paper, 0.11f),
    error = ErrDark, onError = OnErrDark,
    errorContainer = ErrContDark, onErrorContainer = OnErrContDark,
    scrim = Black,
)

// 1. Monochrome (Crisp Minimalist Space) --------------------------------------------------------
val MonochromeLight = expressiveLight(
    primary = Color(0xFF1B1B1F), onPrimary = White,
    primaryContainer = Color(0xFFE2E2E9), onPrimaryContainer = Color(0xFF101014),
    secondary = Color(0xFF5A5A66), secondaryContainer = Color(0xFFE6E6EE), onSecondaryContainer = Color(0xFF191920),
    tertiary = Color(0xFF6F5575), tertiaryContainer = Color(0xFFF8D8FE), onTertiaryContainer = Color(0xFF28132F),
    surfaceBase = Color(0xFFFCFCFF), ink = Color(0xFF1B1B1F),
)
val MonochromeDark = expressiveDark(
    primary = Color(0xFFE3E2E9), onPrimary = Color(0xFF1B1B1F),
    primaryContainer = Color(0xFF3A3A40), onPrimaryContainer = Color(0xFFF1F0F7),
    secondary = Color(0xFFC6C5D0), secondaryContainer = Color(0xFF3A3A42), onSecondaryContainer = Color(0xFFE3E1EC),
    tertiary = Color(0xFFDCBCE1), tertiaryContainer = Color(0xFF563E5C), onTertiaryContainer = Color(0xFFF8D8FE),
    surfaceBase = Color(0xFF0D0D10), paper = Color(0xFFE5E2E9),
)

// 2. Ocean --------------------------------------------------------------------------------------
val OceanLight = expressiveLight(
    primary = Color(0xFF1660A8), onPrimary = White,
    primaryContainer = Color(0xFFD3E4FF), onPrimaryContainer = Color(0xFF001C39),
    secondary = Color(0xFF006783), secondaryContainer = Color(0xFFBDE9FF), onSecondaryContainer = Color(0xFF001F2A),
    tertiary = Color(0xFF5D5B7D), tertiaryContainer = Color(0xFFE3DFFF), onTertiaryContainer = Color(0xFF1A1836),
    surfaceBase = Color(0xFFF8FAFF), ink = Color(0xFF101C28),
)
val OceanDark = expressiveDark(
    primary = Color(0xFFA1C9FF), onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF004883), onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFF65D3FF), secondaryContainer = Color(0xFF004D63), onSecondaryContainer = Color(0xFFBDE9FF),
    tertiary = Color(0xFFC6C2EA), tertiaryContainer = Color(0xFF454364), onTertiaryContainer = Color(0xFFE3DFFF),
    surfaceBase = Color(0xFF0A1119), paper = Color(0xFFDFE3EB),
)

// 3. Forest -------------------------------------------------------------------------------------
val ForestLight = expressiveLight(
    primary = Color(0xFF1C6E2E), onPrimary = White,
    primaryContainer = Color(0xFFA5F5A6), onPrimaryContainer = Color(0xFF002106),
    secondary = Color(0xFF52634F), secondaryContainer = Color(0xFFD5E8CF), onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFF38656A), tertiaryContainer = Color(0xFFBCEBF0), onTertiaryContainer = Color(0xFF002023),
    surfaceBase = Color(0xFFF7FBF1), ink = Color(0xFF111F11),
)
val ForestDark = expressiveDark(
    primary = Color(0xFF8AD88C), onPrimary = Color(0xFF003910),
    primaryContainer = Color(0xFF00531A), onPrimaryContainer = Color(0xFFA5F5A6),
    secondary = Color(0xFFB9CCB4), secondaryContainer = Color(0xFF3A4B38), onSecondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFFA0CFD3), tertiaryContainer = Color(0xFF1E4D52), onTertiaryContainer = Color(0xFFBCEBF0),
    surfaceBase = Color(0xFF0C140C), paper = Color(0xFFDFE4DA),
)

// 4. Sunset -------------------------------------------------------------------------------------
val SunsetLight = expressiveLight(
    primary = Color(0xFF9A4500), onPrimary = White,
    primaryContainer = Color(0xFFFFDBC9), onPrimaryContainer = Color(0xFF341100),
    secondary = Color(0xFF765848), secondaryContainer = Color(0xFFFFDBC9), onSecondaryContainer = Color(0xFF2B160A),
    tertiary = Color(0xFF656032), tertiaryContainer = Color(0xFFECE5AB), onTertiaryContainer = Color(0xFF1F1C00),
    surfaceBase = Color(0xFFFFFBFF), ink = Color(0xFF211A15),
)
val SunsetDark = expressiveDark(
    primary = Color(0xFFFFB68C), onPrimary = Color(0xFF532200),
    primaryContainer = Color(0xFF753400), onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFE6BEAB), secondaryContainer = Color(0xFF5C4032), onSecondaryContainer = Color(0xFFFFDBC9),
    tertiary = Color(0xFFD0C991), tertiaryContainer = Color(0xFF4C481D), onTertiaryContainer = Color(0xFFECE5AB),
    surfaceBase = Color(0xFF181210), paper = Color(0xFFEDE0DA),
)

// 5. Lavender -----------------------------------------------------------------------------------
val LavenderLight = expressiveLight(
    primary = Color(0xFF6750A4), onPrimary = White,
    primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF625B71), secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7E5260), tertiaryContainer = Color(0xFFFFD9E3), onTertiaryContainer = Color(0xFF31101D),
    surfaceBase = Color(0xFFFEF7FF), ink = Color(0xFF1D1B20),
)
val LavenderDark = expressiveDark(
    primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC), secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8), tertiaryContainer = Color(0xFF633B48), onTertiaryContainer = Color(0xFFFFD9E3),
    surfaceBase = Color(0xFF141218), paper = Color(0xFFE6E0E9),
)

// 6. Rose ---------------------------------------------------------------------------------------
val RoseLight = expressiveLight(
    primary = Color(0xFFB01B49), onPrimary = White,
    primaryContainer = Color(0xFFFFD9DF), onPrimaryContainer = Color(0xFF3F0017),
    secondary = Color(0xFF75565B), secondaryContainer = Color(0xFFFFD9DF), onSecondaryContainer = Color(0xFF2B151A),
    tertiary = Color(0xFF7C5635), tertiaryContainer = Color(0xFFFFDCBE), onTertiaryContainer = Color(0xFF2E1500),
    surfaceBase = Color(0xFFFFF8F7), ink = Color(0xFF22191A),
)
val RoseDark = expressiveDark(
    primary = Color(0xFFFFB2BD), onPrimary = Color(0xFF67002A),
    primaryContainer = Color(0xFF8E1539), onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Color(0xFFE5BDC2), secondaryContainer = Color(0xFF5B3F44), onSecondaryContainer = Color(0xFFFFD9DF),
    tertiary = Color(0xFFEFBD94), tertiaryContainer = Color(0xFF623F1F), onTertiaryContainer = Color(0xFFFFDCBE),
    surfaceBase = Color(0xFF1A1112), paper = Color(0xFFEDE0E0),
)
