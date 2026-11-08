package dev.kyouzo.solace.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.kyouzo.solace.R
import dev.kyouzo.solace.ui.common.IconBadge
import dev.kyouzo.solace.ui.common.SolaceCard

private val GrantedGreen = Color(0xFF2E9E69)

/** Bumps whenever the activity resumes, so permission state is re-read after the user returns from system settings. */
@Composable
fun rememberResumeTick(): Int {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val lifecycle = (context.findActivity() as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    return tick
}

private fun Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Full-screen permissions page. Tap a card to allow it; if it's already allowed, tapping opens the system page to change it. */
@Composable
fun PermissionsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val prefs = remember { context.getSharedPreferences("permission_requests", Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }
    val resumed = rememberResumeTick()
    val statuses = remember(refresh, resumed) { AppPerm.entries.associateWith { AppPermissions.isGranted(context, it) } }
    val missing = statuses.count { !it.value }

    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    /** Android silently ignores a request once the user has refused twice, so after one try send them to settings instead. */
    fun requestOrSettings(permission: String, launch: () -> Unit) {
        val askedBefore = prefs.getBoolean(permission, false)
        if (askedBefore && activity?.shouldShowRequestPermissionRationale(permission) == false) context.openAppSettings()
        else { prefs.edit().putBoolean(permission, true).apply(); launch() }
    }

    fun onTap(perm: AppPerm) {
        if (statuses[perm] == true) { context.openAppSettings(); return }
        when (perm) {
            AppPerm.LOCATION, AppPerm.PRECISE ->
                requestOrSettings(Manifest.permission.ACCESS_FINE_LOCATION) { multiple.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
            AppPerm.ALWAYS ->
                if (statuses[AppPerm.LOCATION] != true) requestOrSettings(Manifest.permission.ACCESS_FINE_LOCATION) { multiple.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
                else if (Build.VERSION.SDK_INT >= 29) requestOrSettings(Manifest.permission.ACCESS_BACKGROUND_LOCATION) { single.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
                else context.openAppSettings()
            AppPerm.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= 33) requestOrSettings(Manifest.permission.POST_NOTIFICATIONS) { single.launch(Manifest.permission.POST_NOTIFICATIONS) }
                else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            AppPerm.BATTERY ->
                runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { context.openAppSettings() } }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = dimensionResource(R.dimen.content_max_width)).fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close)) }
                        Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.titleLarge)
                    }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = dimensionResource(R.dimen.screen_padding), vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Text(
                                if (missing == 0) stringResource(R.string.perm_all_set) else stringResource(R.string.perm_need_attention, missing),
                                color = if (missing == 0) GrantedGreen else MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleMedium
                            )
                        }
                        items(AppPerm.entries.toList(), key = { it.name }) { perm ->
                            PermissionCard(perm, statuses[perm] == true) { onTap(perm) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(perm: AppPerm, granted: Boolean, onClick: () -> Unit) {
    SolaceCard(onClick = onClick) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadge(perm.icon, size = 40.dp)
                Text(stringResource(perm.title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(granted)
            }
            Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
            Text(stringResource(perm.body), modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusChip(granted: Boolean) {
    Surface(shape = RoundedCornerShape(10.dp), color = if (granted) GrantedGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.tertiaryContainer) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (granted) Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.perm_allowed), tint = GrantedGreen, modifier = Modifier.size(20.dp))
            else Text(stringResource(R.string.perm_allow), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}
