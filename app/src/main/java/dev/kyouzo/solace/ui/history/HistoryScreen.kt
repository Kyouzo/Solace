package dev.kyouzo.solace.ui.history

import android.content.Context
import android.location.Location
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.kyouzo.solace.R
import dev.kyouzo.solace.data.local.AppDatabase
import dev.kyouzo.solace.data.local.DaySummary
import dev.kyouzo.solace.ui.common.BrandHeader
import dev.kyouzo.solace.ui.common.IconBadge
import dev.kyouzo.solace.ui.common.ScreenColumn
import dev.kyouzo.solace.ui.common.SolaceCard
import dev.kyouzo.solace.ui.map.LiveMap
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.DateFormat
import java.util.Date

private const val MAX_ROUTE_POINTS = 2_000
private const val INITIAL_POINT_ROWS = 50

/**
 * List-detail: days on the left, the selected day's route on the right.
 * Compact windows show one pane at a time; the scaffold handles back navigation between them.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).locationDao() }
    val days by dao.observeDays().collectAsState(emptyList())
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>()
    val scope = rememberCoroutineScope()
    val selected = navigator.currentDestination?.contentKey ?: days.firstOrNull()?.firstAt

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                HistoryListPane(days, selected) { key -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key) } }
            }
        },
        detailPane = {
            AnimatedPane {
                if (selected == null) EmptyDetail() else DayDetail(selected)
            }
        }
    )
}

/** Stateless list of recorded days. */
@Composable
fun HistoryListPane(days: List<DaySummary>, selected: Long?, onSelect: (Long) -> Unit) {
    ScreenColumn(spacing = 12.dp) {
        item { BrandHeader(stringResource(R.string.history_title)) }
        if (days.isEmpty()) item { MessageCard(stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body)) }
        items(days.size, key = { days[it].day }) { index ->
            val day = days[index]
            val isSelected = day.firstAt == selected
            SolaceCard(
                onClick = { onSelect(day.firstAt) },
                container = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(Icons.Outlined.History)
                    Column(Modifier.weight(1f)) {
                        Text(dayLabel(day.firstAt), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.history_day_count, day.pointCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun dayLabel(millis: Long): String = when {
    DateUtils.isToday(millis) -> stringResource(R.string.today)
    DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> stringResource(R.string.yesterday)
    else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
}

@Composable
private fun DayDetail(dayKey: Long) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).locationDao() }
    val bounds = remember(dayKey) { dayBounds(dayKey) }
    val points by remember(bounds) { dao.observeBetween(bounds.first, bounds.second) }.collectAsState(emptyList())
    // A day recorded at 1 point/second can hold tens of thousands of fixes; draw a thinned route, but measure with all of them.
    val path = remember(points) { downsample(points, MAX_ROUTE_POINTS).map { GeoPoint(it.latitude, it.longitude) } }
    val meters = remember(points) {
        val out = FloatArray(1)
        points.zipWithNext().sumOf { (a, b) -> Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out); out[0].toDouble() }
    }
    val durationMs = if (points.size > 1) points.last().recordedAt - points.first().recordedAt else 0L
    var showAll by remember(dayKey) { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }

    ScreenColumn(spacing = 14.dp) {
        item { BrandHeader(DateFormat.getDateInstance(DateFormat.FULL).format(Date(bounds.first))) }
        if (points.isEmpty()) {
            item { MessageCard(stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body)) }
        } else {
            item {
                SolaceCard {
                    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp)) {
                        StatTile(stringResource(R.string.stat_distance), formatDistance(context, meters), Modifier.weight(1f))
                        StatTile(stringResource(R.string.stat_duration), formatDuration(context, durationMs), Modifier.weight(1f))
                        StatTile(stringResource(R.string.stat_points), points.size.toString(), Modifier.weight(1f))
                    }
                    Text(
                        stringResource(R.string.history_time_range, timeFormat.format(Date(points.first().recordedAt)), timeFormat.format(Date(points.last().recordedAt))),
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), style = MaterialTheme.typography.bodyMedium, color = muted
                    )
                }
            }
            item {
                LiveMap(
                    Modifier.fillMaxWidth().aspectRatio(1.3f).heightIn(min = dimensionResource(R.dimen.map_min_height), max = dimensionResource(R.dimen.map_max_height)).clip(RoundedCornerShape(24.dp)),
                    me = path.lastOrNull(), partner = null, path = path
                )
            }
            val visible = if (showAll) points.size else minOf(points.size, INITIAL_POINT_ROWS)
            items(visible) { index ->
                val point = points[index]
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(timeFormat.format(Date(point.recordedAt)))
                    Text(stringResource(R.string.accuracy_meters, point.accuracy.toInt()), color = muted)
                }
            }
            if (!showAll && points.size > INITIAL_POINT_ROWS) item {
                TextButton(onClick = { showAll = true }) { Text(stringResource(R.string.history_show_all, points.size)) }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyDetail() {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.history_pick_day), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageCard(title: String, body: String) {
    SolaceCard {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDistance(context: Context, meters: Double): String =
    if (meters < 1000) context.getString(R.string.distance_m, meters.toInt()) else context.getString(R.string.distance_km, meters / 1000)

private fun formatDuration(context: Context, millis: Long): String {
    val (hours, minutes, seconds) = durationParts(millis)
    return when {
        hours > 0 -> context.getString(R.string.duration_h_min, hours, minutes)
        minutes > 0 -> context.getString(R.string.duration_min, minutes)
        else -> context.getString(R.string.duration_s, seconds)
    }
}
