package com.echoflow.weave.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.echoflow.MainActivity
import com.echoflow.data.AppDatabase
import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.eval.EvalContext
import com.echoflow.weave.core.eval.Formatter
import com.echoflow.weave.core.eval.PlainText
import com.echoflow.weave.core.eval.Session
import com.echoflow.weave.core.eval.WAction
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.program.Compiler
import com.echoflow.weave.core.program.StateCodec
import com.echoflow.weave.runtime.storage.WeaveLibraryRow
import java.util.concurrent.TimeUnit

/**
 * A kept block as a home-screen widget (01 §23.6). Renders the widget profile: Column, Row, Box,
 * Text, Title, Metric, Progress, Button (local state actions only), List (≤ 10 items); anything
 * else shows as "Open in EchoFlow".
 */
class WeaveWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val row = WeaveWidgets.rowFor(context, appWidgetId)
        val tree = row?.let { WeaveWidgets.evaluate(it) }
        provideContent {
            GlanceTheme {
                Column(
                    GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(14.dp),
                ) {
                    if (row == null || tree == null) {
                        Text("Pin a block from Made for you", style = TextStyle(color = GlanceTheme.colors.onSurface))
                        return@Column
                    }
                    Text(row.title, style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Medium, fontSize = 13.sp))
                    Spacer(GlanceModifier.height(6.dp))
                    Node(tree.first, tree.second, row.id, "")
                }
            }
        }
    }

    @Composable
    private fun Node(n: WNode, f: Formatter, libraryId: String, path: String) {
        val text = TextStyle(color = GlanceTheme.colors.onSurface)
        fun fmt(k: String) = n.args[k]?.let { f.auto(it) } ?: ""
        when (n.type) {
            "Column", "Card", "Section", "Surface", "Stat" -> Column(GlanceModifier.fillMaxWidth()) {
                n.children.forEachIndexed { i, c -> Node(c, f, libraryId, "$path/$i"); Spacer(GlanceModifier.height(4.dp)) }
            }
            "Row" -> Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                n.children.forEachIndexed { i, c -> Node(c, f, libraryId, "$path/$i"); Spacer(GlanceModifier.width(8.dp)) }
            }
            "Box" -> Box { n.children.forEachIndexed { i, c -> Node(c, f, libraryId, "$path/$i") } }
            "Text", "Markdown" -> Text(fmt("content").replace(Regex("[*`]"), ""), style = text, maxLines = 4)
            "Title" -> Text(fmt("content"), style = text.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp))
            "Metric" -> Column {
                Text(fmt("label"), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                Text(PlainText.metricValue(n, f), style = text.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold))
            }
            "Progress" -> LinearProgressIndicator(((n.args["value"] as? WNum)?.v ?: 0.0).toFloat().coerceIn(0f, 1f), GlanceModifier.fillMaxWidth())
            "List" -> Column {
                ((n.args["items"] as? WList)?.items ?: emptyList()).take(10).forEach { Text("• " + f.auto(it), style = text, maxLines = 1) }
            }
            "Button" -> {
                val a = n.args["onTap"]
                val local = a is WAction && a.kind in WeaveWidgets.WIDGET_ACTIONS
                Button(
                    fmt("label"),
                    if (local) actionRunCallback<WidgetTap>(actionParametersOf(WidgetTap.LIBRARY to libraryId, WidgetTap.PATH to path))
                    else actionStartActivity<MainActivity>(),
                )
            }
            else -> Text("Open in EchoFlow", style = TextStyle(color = GlanceTheme.colors.primary), modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()))
        }
    }
}

class WeaveWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeaveWidget()
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WeaveWidgets.scheduleRefresh(context)
    }
}

/** A widget Button tap: runs its local state action through the block's Session and saves. */
class WidgetTap : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[LIBRARY] ?: return
        val path = parameters[PATH] ?: return
        val dao = AppDatabase.getDatabase(context).weaveLibraryDao()
        val row = dao.get(id) ?: return
        val program = Compiler.compile(row.source, BuiltinCatalog)
        val session = Session(program, EvalContext())
        session.restore(StateCodec.decodeState(program, row.stateJson))
        var node: WNode? = session.root()
        path.split('/').filter { it.isNotEmpty() }.forEach { node = node?.children?.getOrNull(it.toInt()) }
        val action = node?.args?.get("onTap") ?: return
        session.fire(action)
        dao.updateState(id, StateCodec.encodeState(program, session.state), System.currentTimeMillis())
        WeaveWidget().updateAll(context)
    }

    companion object {
        val LIBRARY = ActionParameters.Key<String>("library")
        val PATH = ActionParameters.Key<String>("path")
    }
}

/** Refreshes widgets every 15 minutes (the `every(≥ 15m)` cadence, 01 §23.6). */
class WeaveWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WeaveWidget().updateAll(applicationContext)
        return Result.success()
    }
}

object WeaveWidgets {
    val WIDGET_ACTIONS = setOf("set", "inc", "dec", "toggle", "push", "reset", "resetAll")
    private const val PREFS = "weave_widgets"

    /**
     * The kept block behind a widget. Assignments are stored per widget (`w:<id>` → library id), so
     * pinning the same block twice gives two widgets that both find it; the row's single
     * `pinnedWidgetId` is only a fallback for widgets pinned before this map existed.
     */
    suspend fun rowFor(context: Context, appWidgetId: Int): WeaveLibraryRow? {
        val dao = AppDatabase.getDatabase(context).weaveLibraryDao()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString("w:$appWidgetId", null)?.let { id -> dao.get(id)?.let { return it } }
        dao.forWidget(appWidgetId)?.let { row ->
            prefs.edit().putString("w:$appWidgetId", row.id).apply()
            return row
        }
        // a freshly pinned widget takes the block the user asked to pin
        val pending = prefs.getString("pending", null) ?: return null
        prefs.edit().remove("pending").putString("w:$appWidgetId", pending).apply()
        dao.setWidget(pending, appWidgetId)
        return dao.get(pending)
    }

    fun evaluate(row: WeaveLibraryRow): Pair<WNode, Formatter>? = runCatching {
        val program = Compiler.compile(row.source, BuiltinCatalog)
        val session = Session(program, EvalContext())
        session.restore(StateCodec.decodeState(program, row.stateJson))
        session.root()?.let { it to session.ctx.formatter }
    }.getOrNull()

    /** Asks the launcher to pin a widget for a kept block (API 26+). */
    fun requestPin(context: Context, libraryId: String) {
        val mgr = AppWidgetManager.getInstance(context)
        if (android.os.Build.VERSION.SDK_INT < 26 || !mgr.isRequestPinAppWidgetSupported) {
            android.widget.Toast.makeText(context, "Add the EchoFlow block widget from your home screen", android.widget.Toast.LENGTH_LONG).show()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("pending", libraryId).apply()
            return
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("pending", libraryId).apply()
        mgr.requestPinAppWidget(ComponentName(context, WeaveWidgetReceiver::class.java), null, null)
    }

    fun scheduleRefresh(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "weave-widgets", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WeaveWidgetWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

}
