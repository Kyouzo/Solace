package dev.kyouzo.solace.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.kyouzo.solace.R

/**
 * The permissions Solace actually uses. Storage and activity-recognition permissions are deliberately absent:
 * downloaded maps live in the app's own folder, and nothing here reads motion sensors.
 */
enum class AppPerm(val icon: ImageVector, @StringRes val title: Int, @StringRes val body: Int) {
    LOCATION(Icons.Outlined.LocationOn, R.string.perm_location, R.string.perm_location_body),
    PRECISE(Icons.Outlined.GpsFixed, R.string.perm_precise, R.string.perm_precise_body),
    ALWAYS(Icons.Outlined.MyLocation, R.string.perm_always, R.string.perm_always_body),
    NOTIFICATIONS(Icons.Outlined.Notifications, R.string.perm_notifications, R.string.perm_notifications_body),
    BATTERY(Icons.Outlined.BatteryChargingFull, R.string.perm_battery, R.string.perm_battery_body)
}

object AppPermissions {
    private fun has(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isGranted(context: Context, perm: AppPerm): Boolean = when (perm) {
        AppPerm.LOCATION -> has(context, Manifest.permission.ACCESS_FINE_LOCATION) || has(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        AppPerm.PRECISE -> has(context, Manifest.permission.ACCESS_FINE_LOCATION)
        AppPerm.ALWAYS ->
            if (Build.VERSION.SDK_INT < 29) isGranted(context, AppPerm.LOCATION) else has(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        AppPerm.NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled()
        AppPerm.BATTERY -> context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    fun missingCount(context: Context): Int = AppPerm.entries.count { !isGranted(context, it) }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
