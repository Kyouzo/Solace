package dev.kyouzo.solace.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.kyouzo.solace.R
import dev.kyouzo.solace.data.local.AppDatabase
import dev.kyouzo.solace.data.remote.SupabaseAuthRepository
import dev.kyouzo.solace.ui.common.PrivacyCard
import dev.kyouzo.solace.ui.common.SolaceCard
import kotlinx.coroutines.launch

/** Everything account-related, shown at the top of Settings: configuration notice, sign-in form, or profile + partner. */
@Composable
fun AccountSection() {
    val context = LocalContext.current
    val auth = remember { SupabaseAuthRepository(context) }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(auth.savedSession()) }
    val current = session

    when {
        !auth.isConfigured -> PrivacyCard(stringResource(R.string.supabase_missing_title), stringResource(R.string.supabase_missing_body))
        current == null -> SignInPanel(auth) { session = it }
        else -> SolaceCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Text(current.email.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.signed_in), style = MaterialTheme.typography.titleMedium)
                        Text(current.email, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                PartnerPanel()
                TextButton(
                    onClick = {
                        auth.signOut()
                        scope.launch { AppDatabase.get(context).locationDao().clearRemote() }
                        session = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.action_sign_out), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
