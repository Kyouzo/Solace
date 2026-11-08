package dev.kyouzo.solace.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.kyouzo.solace.R
import dev.kyouzo.solace.tracking.Retention
import dev.kyouzo.solace.tracking.TrackingControl
import dev.kyouzo.solace.tracking.TrackingPrecision
import dev.kyouzo.solace.ui.common.SettingRow
import kotlinx.coroutines.launch

/** Tap to cycle how long location history is kept on this phone. */
@Composable
fun RetentionRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by remember { mutableIntStateOf(Retention.days(context)) }
    SettingRow(
        icon = Icons.Outlined.Schedule,
        title = stringResource(R.string.retention_title),
        subtitle = stringResource(R.string.retention_hint),
        onClick = {
            val next = Retention.options[(Retention.options.indexOf(days) + 1) % Retention.options.size]
            days = next
            Retention.set(context, next)
            scope.launch { Retention.purge(context) }
        },
        trailing = { Text(if (days <= 0) stringResource(R.string.retention_forever) else stringResource(R.string.retention_days, days), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
    )
}

/** Tap to switch between maximum precision (every metre) and balanced (battery-friendly). Applies immediately. */
@Composable
fun PrecisionRow() {
    val context = LocalContext.current
    var precision by remember { mutableStateOf(TrackingPrecision.current(context)) }
    SettingRow(
        icon = Icons.Outlined.GpsFixed,
        title = stringResource(R.string.precision_title),
        subtitle = stringResource(if (precision == TrackingPrecision.MAXIMUM) R.string.precision_max_hint else R.string.precision_balanced_hint),
        onClick = {
            val next = precision.next()
            precision = next
            TrackingPrecision.set(context, next)
            TrackingControl.restartIfActive(context)
        },
        trailing = { Text(stringResource(if (precision == TrackingPrecision.MAXIMUM) R.string.precision_max else R.string.precision_balanced), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
    )
}
