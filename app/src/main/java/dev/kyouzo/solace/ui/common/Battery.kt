package dev.kyouzo.solace.ui.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.kyouzo.solace.R

data class BatteryInfo(val percent: Int, val charging: Boolean)

private fun readBattery(intent: Intent?): BatteryInfo? {
    intent ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return BatteryInfo(level * 100 / scale, status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
}

/** This phone's live battery level and charging state. */
@Composable
fun rememberBattery(): BatteryInfo? {
    val context = LocalContext.current.applicationContext
    var info by remember { mutableStateOf(readBattery(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context, intent: Intent) { info = readBattery(intent) } }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return info
}

/** Small pill showing a battery percentage; turns red at 15% or less when not charging. */
@Composable
fun BatteryChip(percent: Int, modifier: Modifier = Modifier, charging: Boolean = false) {
    val low = percent <= 15 && !charging
    val tint = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f), shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                if (charging) Icons.Outlined.BatteryChargingFull else if (low) Icons.Outlined.BatteryAlert else Icons.Outlined.BatteryFull,
                contentDescription = stringResource(R.string.battery_cd), tint = tint, modifier = Modifier.size(16.dp)
            )
            Text(stringResource(R.string.battery_percent, percent), style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}
