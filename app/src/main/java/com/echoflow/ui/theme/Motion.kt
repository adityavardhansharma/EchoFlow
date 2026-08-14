package com.echoflow.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Test override for [rememberReducedMotion]; null means read the system animator scale. */
internal val LocalReducedMotionOverride = compositionLocalOf<Boolean?> { null }

/**
 * True when the user has turned animations off system-wide (Developer options → animator
 * duration scale, or the accessibility "remove animations" toggle, which sets the same value).
 *
 * EchoFlow leans hard on motion — dot fields, scanline reveals, shape morphs — and all of it
 * is decoration over information. Honouring this flag lets the signature degrade to stillness
 * rather than being ignored: reveals become crossfades, springs become snaps, looping
 * animations render at their resting frame.
 *
 * Read once per composition rather than observed: the platform restarts activities when the
 * developer setting changes, and polling a global setting every frame would cost more than the
 * animations it disables.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    LocalReducedMotionOverride.current?.let { return it }
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}
