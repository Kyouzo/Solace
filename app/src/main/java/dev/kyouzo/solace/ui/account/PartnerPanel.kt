package dev.kyouzo.solace.ui.account

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kyouzo.solace.R
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
    suspend fun reload() { repo.coupleStatus().onSuccess { status = it }.onFailure { message = it.message ?: context.getString(R.string.supabase_unreachable) } }
    LaunchedEffect(Unit) { reload() }

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dateTime = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val s = status
        when {
            s == null -> Text(stringResource(R.string.partner_checking), color = muted)
            !s.paired -> {
                Text(stringResource(R.string.partner_none_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.partner_invite_hint), color = muted)
                Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { run { repo.createInvite().onSuccess { invite = it }.onFailure { message = it.message } } }) { Text(stringResource(R.string.action_create_invite)) }
                invite?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } }
                OutlinedTextField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(12) }, label = { Text(stringResource(R.string.field_partner_code)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(enabled = !busy && code.length >= 8, modifier = Modifier.fillMaxWidth(), onClick = { run { repo.redeemInvite(code).onSuccess { code = ""; invite = null; message = context.getString(R.string.partner_connected_msg); reload() }.onFailure { message = it.message } } }) { Text(stringResource(R.string.action_join)) }
                OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { run { reload() } }) { Text(stringResource(R.string.action_refresh)) }
            }
            else -> {
                Text(stringResource(R.string.partner_connected_with, s.partnerName ?: stringResource(R.string.partner_default_name)), fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.share_title))
                        Text(stringResource(if (s.mySharing) R.string.share_on else R.string.share_off), style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                    Switch(checked = s.mySharing, enabled = !busy, onCheckedChange = { on -> run { repo.setSharing(on).onSuccess { reload(); if (on) SyncScheduler.kick(context, 0) }.onFailure { message = it.message } } })
                }
                Text(stringResource(if (s.partnerSharing) R.string.partner_sharing else R.string.partner_not_sharing), style = MaterialTheme.typography.bodySmall, color = muted)
                remote?.let { r ->
                    Text(stringResource(R.string.partner_last_seen, dateTime.format(Date(r.recordedAt)), r.accuracy.toInt()))
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${r.latitude},${r.longitude}?q=${r.latitude},${r.longitude}(Partner)")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { message = context.getString(R.string.maps_missing) }
                    }) { Text(stringResource(R.string.action_open_maps)) }
                }
                Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    run { repo.syncNow().onSuccess { lastSync = repo.lastSyncAt(); message = context.getString(R.string.sync_done, it.uploaded) }.onFailure { message = it.message } }
                }) { Text(stringResource(if (busy) R.string.action_working else R.string.action_sync)) }
                if (lastSync > 0) Text(stringResource(R.string.sync_last, dateTime.format(Date(lastSync))), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        message?.let { Text(it, color = muted) }
    }
}
