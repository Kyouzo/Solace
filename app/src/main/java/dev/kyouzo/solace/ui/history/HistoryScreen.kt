package dev.kyouzo.solace.ui.history

import android.content.Context
import android.location.Location
import android.text.format.DateUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import dev.kyouzo.solace.ui.common.IconBadge
import dev.kyouzo.solace.ui.common.SolaceCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kyouzo.solace.R
import dev.kyouzo.solace.data.local.AppDatabase
import dev.kyouzo.solace.data.local.DaySummary
import dev.kyouzo.solace.ui.common.BrandHeader
import dev.kyouzo.solace.ui.common.ScreenColumn
import dev.kyouzo.solace.ui.map.LiveMap
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.DateFormat
import java.util.Date

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
    val path = remember(points) { points.map { GeoPoint(it.latitude, it.longitude) } }
    val meters = remember(points) {
        val out = FloatArray(1)
        points.zipWithNext().sumOf { (a, b) -> Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out); out[0].toDouble() }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ScreenColumn(spacing = 14.dp) {
        item { BrandHeader(DateFormat.getDateInstance(DateFormat.FULL).format(Date(bounds.first))) }
        item { MessageCard(if (points.isEmpty()) stringResource(R.string.history_empty_title) else stringResource(R.string.history_summary, points.size, formatDistance(context, meters)), stringResource(if (points.isEmpty()) R.string.history_empty_body else R.string.history_note)) }
        if (path.size > 1) item {
            LiveMap(
                Modifier.fillMaxWidth().aspectRatio(1.3f).heightIn(min = dimensionResource(R.dimen.map_min_height), max = dimensionResource(R.dimen.map_max_height)).clip(RoundedCornerShape(24.dp)),
                me = path.last(), partner = null, path = path
            )
        }
        items(points.size) { index ->
            val point = points[index]
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(point.recordedAt)))
                Text(stringResource(R.string.accuracy_meters, point.accuracy.toInt()), color = muted)
            }
        }
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
