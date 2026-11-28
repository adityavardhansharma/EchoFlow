package com.echoflow.weave

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.AppDatabase
import com.echoflow.ui.components.MarkdownExtensionBlock
import com.echoflow.ui.theme.EchoFlowTheme
import com.echoflow.weave.compose.WeaveBlock
import com.echoflow.weave.compose.render.Registry
import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.CatalogExamples
import com.echoflow.weave.runtime.WeaveRuntime
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T-408: every non-popup component in the theme matrix. One image per catalog group, built from
 * the `weave_docs` examples (which compile at tier 4), for each palette × light/dark × font scale
 * 1.0/1.5.
 *
 * The full matrix (7 themes × 2 × 2 × 11 groups) is the release run:
 * `./gradlew :app:recordRoborazziDebug -Dweave.themeMatrix=true --tests '*WeaveThemeMatrix*'`.
 * An ordinary test run renders monochrome light at 1.0 only, so every group still renders on
 * every build.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h2400dp-xxhdpi")
class WeaveThemeMatrixScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val runtime = WeaveRuntime(db, Registry.names)

    @After fun close() { runtime.close(); db.close() }

    private val themes = listOf("monochrome", "ocean", "forest", "sunset", "lavender", "rose", "dynamic")

    /** Popups live in their own window (T-410); Map, Model3D, Video and Audio need the network or native code. */
    private val skip = setOf("Sheet", "Dialog", "Popover", "Menu", "Map", "Model3D", "Video", "Audio", "Lottie", "Image", "Gallery", "Markdown", "Code")

    /** One block per catalog group: every example's state and data lines, then a column of usages. */
    private fun groupBlock(group: String): String? {
        val comps = BuiltinCatalog.components.filter { it.group == group && it.name !in skip && it.name in Registry.names }
        if (comps.isEmpty()) return null
        val decls = LinkedHashMap<String, String>()
        val uses = ArrayList<String>()
        comps.forEachIndexed { i, c ->
            val lines = CatalogExamples.component(c.name)?.lines() ?: return@forEachIndexed
            lines.dropLast(1).forEach { l -> decls[l.substringBefore(" =")] = l }
            decls["ex$i"] = "ex$i = ${lines.last()}"
            uses += "ex$i"
        }
        return "@weave 1 id: ${group.lowercase()}\n" + decls.values.joinToString("\n") + "\nroot = Column(gap: l) [${uses.joinToString(", ")}]"
    }

    private var current by mutableStateOf<Triple<Int, String, Pair<String, Pair<Boolean, Float>>>?>(null)

    @Test fun `T-408 every non-popup component across the theme matrix`() {
        val full = System.getProperty("weave.themeMatrix") == "true"
        val matrix = if (full) themes.flatMap { t -> listOf(false, true).flatMap { d -> listOf(1f, 1.5f).map { f -> Triple(t, d, f) } } }
        else listOf(Triple("monochrome", false, 1f))
        val groups = BuiltinCatalog.components.map { it.group }.distinct().mapNotNull { g -> groupBlock(g)?.let { g to it } }
        assertTrue(groups.size >= 10)
        compose.setContent {
            current?.let { (key, src, look) ->
                val (theme, mode) = look
                val (dark, font) = mode
                androidx.compose.runtime.key(key) {
                    EchoFlowTheme(darkTheme = dark, themeName = theme) {
                        val d = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(d.density, font)) {
                            Box(Modifier.background(MaterialTheme.colorScheme.surface).width(411.dp).padding(16.dp)) {
                                WeaveBlock(runtime.controller(null, null, key), MarkdownExtensionBlock(key, "weave", "weave", src, true))
                            }
                        }
                    }
                }
            }
        }
        // some components animate forever (Shader, Blob, Clock, wavy Progress): drive the clock by hand
        compose.mainClock.autoAdvance = false
        var key = 0
        for ((theme, dark, font) in matrix) for ((group, src) in groups) {
            current = Triple(++key, src, theme to (dark to font))
            compose.mainClock.advanceTimeBy(1_500)
            val name = "weave_${group.lowercase()}_${theme}_${if (dark) "dark" else "light"}_${if (font == 1f) "1x" else "1_5x"}"
            compose.onRoot().captureRoboImage("src/test/snapshots/weave/$name.png")
        }
    }
}
