package dev.kyouzo.solace.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Stable
class LocationPermission(val granted: Boolean, val request: () -> Unit)

private fun hasLocation(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Shared by every screen that can start recording, so none of them starts the location service without permission. */
@Composable
fun rememberLocationPermission(): LocationPermission {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocation(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    return remember(granted) {
        LocationPermission(granted) { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)) }
    }
}
