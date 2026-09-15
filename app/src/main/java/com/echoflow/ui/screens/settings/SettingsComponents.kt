@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.Spacing

@Composable
internal fun SettingsPageScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = subtitle?.let { { Text(it) } },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.base, vertical = Spacing.s),
        ) {
            content()
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/**
 * Wraps a detail page's content. Standalone pages get the full scaffold (top app bar +
 * scroll); when [embedded] (e.g. inside the Models toggle) the body is emitted directly so
 * the host page owns the app bar and scrolling — no nested bar, no double scroll.
 */
@Composable
internal fun SettingsPageBody(
    embedded: Boolean,
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (embedded) {
        Column(Modifier.fillMaxWidth(), content = content)
    } else {
        SettingsPageScaffold(title = title, subtitle = subtitle, onBack = onBack, content = content)
    }
}

/** Section header used on every page: title plus optional supporting line. */
@Composable
internal fun PageSection(title: String, supporting: String? = null) {
    Column(Modifier.padding(start = Spacing.xs, bottom = Spacing.m)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (supporting != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l), content = content)
    }
}

@Composable
internal fun SavedKeyBadge(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * The M3 Expressive connected button group: ToggleButtons that share a slab and morph
 * shape on press/selection.
 */
@Composable
internal fun ConnectedToggleRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector>? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (value, label) ->
            val checked = selected == value
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (it) onSelect(value) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                when {
                    checked -> Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                    icons != null -> Icon(icons[index], null, Modifier.size(16.dp))
                }
                if (checked || icons != null) Spacer(Modifier.width(Spacing.xs))
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
        }
    }
}

/** Last row of a grouped list that expands/collapses the rest of the group. */
@Composable
internal fun ShowMoreRow(
    expanded: Boolean,
    expandText: String,
    collapseText: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "showMoreChevron",
    )
    Surface(
        onClick = onClick,
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                if (expanded) collapseText else expandText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                Icons.Default.ExpandMore, null,
                Modifier.size(18.dp).rotate(chevron),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
