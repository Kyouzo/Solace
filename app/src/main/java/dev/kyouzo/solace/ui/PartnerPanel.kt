package dev.kyouzo.solace.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kyouzo.solace.data.local.AppDatabase
import dev.kyouzo.solace.data.remote.CoupleStatus
import dev.kyouzo.solace.data.remote.SyncRepository
import dev.kyouzo.solace.data.remote.SyncScheduler
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Pairing, sharing toggle, manual sync and partner's last known location. Shown once signed in. */
@Composable
fun PartnerPanel() {
    val context = LocalContext.current
    val repo = remember { SyncRepository(context) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<CoupleStatus?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var invite by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var lastSync by remember { mutableStateOf(repo.lastSyncAt()) }
    val remote by AppDatabase.get(context).locationDao().observeLatestRemote().collectAsState(null)

    fun run(block: suspend () -> Unit) { scope.launch { busy = true; message = null; try { block() } finally { busy = false } } }
    suspend fun reload() { repo.coupleStatus().onSuccess { status = it }.onFailure { message = it.message ?: "Couldn't reach Supabase." } }
    LaunchedEffect(Unit) { reload() }

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val s = status
        when {
            s == null -> Text("Checking partner status…", color = muted)
            !s.paired -> {
                Text("No partner connected", fontWeight = FontWeight.SemiBold)
                Text("Create a one-time code and send it to your person, or enter the code they sent you. Codes expire after 15 minutes.", color = muted)
                Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { run { repo.createInvite().onSuccess { invite = it }.onFailure { message = it.message } } }) { Text("Create invitation code") }
                invite?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } }
                OutlinedTextField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(12) }, label = { Text("Enter partner's code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(enabled = !busy && code.length >= 8, modifier = Modifier.fillMaxWidth(), onClick = { run { repo.redeemInvite(code).onSuccess { code = ""; invite = null; message = "Connected."; reload() }.onFailure { message = it.message } } }) { Text("Join with code") }
                OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { run { reload() } }) { Text("Refresh") }
            }
            else -> {
                Text("Connected with ${s.partnerName ?: "your partner"}", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("Share my location"); Text(if (s.mySharing) "On — your phone uploads while recording" else "Off — nothing leaves your phone", style = MaterialTheme.typography.bodySmall, color = muted) }
                    Switch(checked = s.mySharing, enabled = !busy, onCheckedChange = { on -> run { repo.setSharing(on).onSuccess { reload(); if (on) SyncScheduler.kick(context, 0) }.onFailure { message = it.message } } })
                }
                Text(if (s.partnerSharing) "Your partner is sharing with you." else "Your partner hasn't turned sharing on. You'll only see each other when both are on.", style = MaterialTheme.typography.bodySmall, color = muted)
                remote?.let { r ->
                    Text("Partner last seen ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(r.recordedAt))} (±${r.accuracy.toInt()} m)")
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${r.latitude},${r.longitude}?q=${r.latitude},${r.longitude}(Partner)")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { message = "No maps app installed." } }) { Text("Open in Maps") }
                }
                Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { run { repo.syncNow().onSuccess { lastSync = repo.lastSyncAt(); message = "Synced — ${it.uploaded} point(s) uploaded." }.onFailure { message = it.message } } }) { Text(if (busy) "Working…" else "Sync now") }
                if (lastSync > 0) Text("Last sync ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastSync))}", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        message?.let { Text(it, color = muted) }
    }
}
