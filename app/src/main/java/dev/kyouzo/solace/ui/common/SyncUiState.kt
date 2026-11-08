package dev.kyouzo.solace.ui.common

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

data class SyncUiState(val sharing: Boolean, val paired: Boolean)

private fun read(prefs: SharedPreferences) = SyncUiState(prefs.getBoolean("sharing", false), prefs.getBoolean("paired", false))

/** Live view of the sharing/pairing flags, so summaries stay correct while the user changes them on the same screen. */
@Composable
fun rememberSyncUiState(): State<SyncUiState> {
    val prefs = LocalContext.current.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)
    val state = remember { mutableStateOf(read(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, _ -> state.value = read(changed) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
