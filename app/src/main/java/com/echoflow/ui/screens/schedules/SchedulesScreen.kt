@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.schedules

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.DefaultChatModels
import com.echoflow.data.ScheduleManager
import com.echoflow.data.ScheduleTask
import com.echoflow.data.ScheduleText
import com.echoflow.data.toDraft
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.screens.settings.ConnectedToggleRow
import com.echoflow.ui.theme.Spacing
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where the Schedules surface is: the home list, or one schedule's conversation. */
sealed interface ScheduleRoute {
    data object Home : ScheduleRoute
    /** [fromHome] decides whether Back returns to the list or leaves Schedules altogether. */
    data class Conversation(
        val scheduleId: String,
        val threadId: String? = null,
        val seed: String = "",
        val fromHome: Boolean = false,
    ) : ScheduleRoute

    companion object {
        fun new(seed: String = "", fromHome: Boolean = true) = Conversation(UUID.randomUUID().toString(), seed = seed, fromHome = fromHome)
    }
}

@Composable
fun SchedulesScreen(
    settingsViewModel: SettingsViewModel,
    route: ScheduleRoute,
    onRouteChange: (ScheduleRoute?) -> Unit,
    onManageModels: () -> Unit,
) {
    val selectedModel by settingsViewModel.selectedModel.collectAsState()
    val customModels by settingsViewModel.customModels.collectAsState()
    val customProviderModels by settingsViewModel.customProviderModels.collectAsState()
    val localModels by settingsViewModel.localModels.collectAsState()
    val localEnabled by settingsViewModel.localModelsEnabled.collectAsState()
    val models = remember(customModels, customProviderModels) {
        (DefaultChatModels.BUILT_IN + customModels.map { it.id to it.name } +
            customProviderModels.filterNot { it.isLocalLike }.map { it.id to "${it.group}: ${it.name}" })
            .distinctBy { it.first }
    }
    val localEntries = remember(localModels, localEnabled, customProviderModels) {
        ((if (localEnabled) localModels.map { it.id to it.name } else emptyList()) +
            customProviderModels.filter { it.isLocalLike }.map { it.id to "${it.group}: ${it.name}" })
            .distinctBy { it.first }
    }
    val back = {
        when (route) {
            is ScheduleRoute.Conversation -> onRouteChange(if (route.fromHome) ScheduleRoute.Home else null)
            ScheduleRoute.Home -> onRouteChange(null)
        }
    }
    BackHandler { back() }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val forward = targetState is ScheduleRoute.Conversation
            (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
        },
        contentKey = { (it as? ScheduleRoute.Conversation)?.scheduleId ?: "home" },
        label = "schedules-route",
    ) { current ->
        when (current) {
            ScheduleRoute.Home -> SchedulesHome(
                onBack = back,
                onOpen = { task -> onRouteChange(ScheduleRoute.Conversation(task.id, task.threadId, fromHome = true)) },
                onNew = { seed -> onRouteChange(ScheduleRoute.new(seed)) },
            )
            is ScheduleRoute.Conversation -> ScheduleChatScreen(
                scheduleId = current.scheduleId, threadId = current.threadId,
                defaultModel = selectedModel, models = models, localModels = localEntries,
                onBack = back, onManageModels = onManageModels, seed = current.seed,
            )
        }
    }
}

@Composable
private fun SchedulesHome(
    onBack: () -> Unit,
    onOpen: (ScheduleTask) -> Unit,
    onNew: (String) -> Unit,
) {
    val context = LocalContext.current
    val manager = remember(context) { ScheduleManager(context.applicationContext) }
    val tasks by manager.tasks.collectAsState(initial = null)
    val running by manager.runningTaskIds.collectAsState(initial = emptyList())
    val use24h = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val scope = rememberCoroutineScope()
    var filter by rememberSaveableFilter()
    var showInfo by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    val all = tasks.orEmpty()
    val counts = remember(all) { all.groupingBy { it.status }.eachCount() }
    val visible = remember(all, filter) {
        all.filter { it.status == filter }.sortedWith(compareBy(nullsLast()) { it.nextRunAt })
    }
    val upNext = if (filter == ScheduleTask.ACTIVE) visible.firstOrNull { it.nextRunAt != null } else null

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    if (showInfo) HowSchedulesWork(onDismiss = { showInfo = false })

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Schedules") },
                subtitle = {
                    Text(when {
                        tasks == null -> ""
                        all.isEmpty() -> "Things EchoFlow does for you, on time"
                        upNext?.nextRunAt != null -> "${counts[ScheduleTask.ACTIVE] ?: 0} active · next ${ScheduleText.relative(upNext.nextRunAt, now, upNext.zoneId)}"
                        else -> "${counts[ScheduleTask.ACTIVE] ?: 0} active"
                    })
                },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { showInfo = true }, modifier = Modifier.testTag("schedules_info")) {
                        Icon(Icons.Outlined.Info, "How schedules work")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            if (all.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = { onNew("") },
                expanded = fabExpanded,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New schedule") },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { padding ->
        if (tasks != null && all.isEmpty()) {
            FirstSchedule(Modifier.padding(padding), onNew)
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.base, end = Spacing.base, top = Spacing.s, bottom = 120.dp),
        ) {
            item(key = "filter") {
                ConnectedToggleRow(
                    options = listOf(
                        ScheduleTask.ACTIVE to label("Active", counts[ScheduleTask.ACTIVE]),
                        ScheduleTask.PAUSED to label("Paused", counts[ScheduleTask.PAUSED]),
                        ScheduleTask.COMPLETED to label("Ended", counts[ScheduleTask.COMPLETED]),
                    ),
                    selected = filter, onSelect = { filter = it },
                    modifier = Modifier.padding(bottom = Spacing.l),
                )
            }
            upNext?.let { task ->
                item(key = "up-next") {
                    UpNextCard(task, now, use24h, running = task.id in running, onClick = { onOpen(task) },
                        modifier = Modifier.padding(bottom = Spacing.xl).animateItem())
                }
            }
            if (visible.isEmpty()) item(key = "empty") {
                Text(
                    when (filter) {
                        ScheduleTask.PAUSED -> "Nothing is paused. Pause a schedule to stop its runs without losing it."
                        ScheduleTask.COMPLETED -> "Schedules you end, and one-time schedules that have run, rest here."
                        else -> "Nothing is running right now."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.huge),
                )
            } else {
                item(key = "section") {
                    Text(if (filter == ScheduleTask.ACTIVE) "All active" else if (filter == ScheduleTask.PAUSED) "Paused" else "Ended",
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.s))
                }
                itemsIndexed(visible, key = { _, t -> t.id }) { index, task ->
                    ScheduleRow(
                        task = task, index = index, count = visible.size, now = now, use24h = use24h,
                        running = task.id in running,
                        onClick = { onOpen(task) },
                        onToggle = { active ->
                            scope.launch { runCatching { manager.setStatus(task.id, if (active) ScheduleTask.ACTIVE else ScheduleTask.PAUSED) } }
                        },
                        modifier = Modifier.padding(bottom = 2.dp).animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberSaveableFilter() = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(ScheduleTask.ACTIVE) }

private fun label(name: String, count: Int?) = if (count == null || count == 0) name else "$name $count"

/** The next thing that will happen, big enough to read at a glance: what, and how soon. */
@Composable
private fun UpNextCard(task: ScheduleTask, now: Long, use24h: Boolean, running: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val at = task.nextRunAt ?: return
    Surface(onClick = onClick, shape = RoundedCornerShape(28.dp), color = colors.primaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (running) LoadingIndicator(Modifier.size(40.dp), color = colors.onPrimaryContainer)
                    else ScheduleMark(size = 40.dp, tint = colors.onSecondaryContainer, container = colors.secondaryContainer)
                }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(if (running) "Running now" else "Up next", style = MaterialTheme.typography.labelLarge,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f))
                    Text(task.title, style = MaterialTheme.typography.titleMedium, color = colors.onPrimaryContainer,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(Spacing.base))
            Text(ScheduleText.relative(at, now, task.zoneId).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.headlineMedium, color = colors.onPrimaryContainer)
            Text(ScheduleText.occurrence(at, task.zoneId, use24h), style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimaryContainer.copy(alpha = 0.8f))
        }
    }
}

/** One schedule in the grouped list: its own watch mark, name, cadence, when next — and a pause switch. */
@Composable
private fun ScheduleRow(
    task: ScheduleTask,
    index: Int,
    count: Int,
    now: Long,
    use24h: Boolean,
    running: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val draft = remember(task) { task.toDraft() }
    val detail = when {
        running -> "Running now"
        task.status == ScheduleTask.PAUSED -> "Paused"
        task.status == ScheduleTask.COMPLETED -> "Ended · ${ScheduleText.occurrence(task.updatedAt, task.zoneId, use24h)}"
        task.nextRunAt != null -> "Next ${ScheduleText.relative(task.nextRunAt, now, task.zoneId)}"
        else -> null
    }
    Surface(
        onClick = onClick,
        shape = groupedItemShape(index, count, large = 28.dp, small = 6.dp),
        color = colors.surfaceContainer,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "${task.title}, ${ScheduleText.cadence(draft, use24h)}${detail?.let { ", $it" } ?: ""}" },
    ) {
        Row(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (running) LoadingIndicator(Modifier.size(48.dp), color = colors.tertiary)
                else ScheduleMark(size = 44.dp,
                    tint = if (task.status == ScheduleTask.ACTIVE) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    container = if (task.status == ScheduleTask.ACTIVE) colors.secondaryContainer else colors.surfaceContainerHighest)
            }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary(draft, use24h), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium,
                    color = if (running) colors.tertiary else colors.primary)
            }
            if (task.status != ScheduleTask.COMPLETED) {
                Spacer(Modifier.width(Spacing.s))
                Switch(checked = task.status == ScheduleTask.ACTIVE, onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = if (task.status == ScheduleTask.ACTIVE) "Pause ${task.title}" else "Resume ${task.title}" })
            }
        }
    }
}

/** No schedules yet: show what the feature is for, with ideas that start a conversation. */
@Composable
private fun FirstSchedule(modifier: Modifier, onNew: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.base),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            ScheduleMark(Modifier.padding(top = Spacing.xl), size = 72.dp, tint = colors.onSecondaryContainer, container = colors.secondaryContainer)
            Spacer(Modifier.height(Spacing.l))
            Text("Let EchoFlow keep time for you", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = colors.onSurface)
            Spacer(Modifier.height(Spacing.s))
            Text("Briefings, reminders, practice and check-ins that arrive on their own — set up in a conversation.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xl))
        }
        itemsIndexed(ScheduleTemplates) { index, template ->
            Surface(onClick = { onNew(template.prompt) }, shape = groupedItemShape(index, ScheduleTemplates.size, large = 28.dp),
                color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(16.dp), color = colors.tertiaryContainer) {
                        Icon(template.icon, null, Modifier.padding(10.dp).size(20.dp), tint = colors.onTertiaryContainer)
                    }
                    Spacer(Modifier.width(Spacing.base))
                    Column(Modifier.weight(1f)) {
                        Text(template.label, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                        Text(template.prompt, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(Spacing.xl))
            Button(onClick = { onNew("") }, contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.m)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(Spacing.s))
                Text("Describe your own")
            }
        }
    }
}

@Composable
private fun HowSchedulesWork(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScheduleMark(size = 40.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer, container = MaterialTheme.colorScheme.secondaryContainer)
                Spacer(Modifier.width(Spacing.m))
                Text("How schedules work", style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(Spacing.l))
            listOf<Triple<ImageVector, String, String>>(
                Triple(Icons.AutoMirrored.Filled.Chat, "It all happens in a conversation", "Describe a schedule to create it, ask to change it, and read every run's answer in the same thread — or edit the card by hand."),
                Triple(Icons.Default.Memory, "Runs with the model you pick", "Each schedule remembers its model. On-device models run on this phone."),
                Triple(Icons.Default.TravelExplore, "Uses the web when it needs to", "If web search is set up in Settings, a run looks things up only when the task calls for current information."),
                Triple(Icons.Default.NotificationsActive, "Tells you when it's done", "You get a notification with the first line of the answer. Tap it to open the conversation."),
                Triple(Icons.Default.BatterySaver, "Android picks the exact moment", "Background work can be delayed a little to save battery. Anything missed is noted in the conversation."),
            ).forEachIndexed { index, (icon, title, body) ->
                Surface(shape = groupedItemShape(index, 5, large = 24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                    Row(Modifier.padding(Spacing.base)) {
                        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Spacing.base))
                        Column {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
