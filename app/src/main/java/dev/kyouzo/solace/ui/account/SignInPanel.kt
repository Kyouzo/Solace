package dev.kyouzo.solace.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.kyouzo.solace.R
import dev.kyouzo.solace.data.remote.AuthSession
import dev.kyouzo.solace.data.remote.SupabaseAuthRepository
import dev.kyouzo.solace.ui.common.IconBadge
import dev.kyouzo.solace.ui.common.SolaceCard
import kotlinx.coroutines.launch

private data class Notice(val text: String, val isError: Boolean)

/** Sign in / create account. One form, two modes, with inline validation, a loading state and readable errors. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInPanel(auth: SupabaseAuthRepository, onSignedIn: (AuthSession) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var registering by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") } // deliberately not saved across process death
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Notice?>(null) }

    val emailError = attempted && !AuthValidation.isValidEmail(email)
    val passwordError = attempted && !AuthValidation.isValidPassword(password)

    fun submit() {
        attempted = true
        focus.clearFocus()
        if (loading || !AuthValidation.isValidEmail(email) || !AuthValidation.isValidPassword(password)) return
        scope.launch {
            loading = true
            notice = null
            val result = if (registering) auth.signUp(email.trim(), password) else auth.signIn(email.trim(), password)
            loading = false
            result
                .onSuccess { session ->
                    if (session.accessToken.isBlank()) {
                        notice = Notice(context.getString(R.string.auth_confirm_email), isError = false)
                        registering = false
                        password = ""
                        attempted = false
                    } else onSignedIn(session)
                }
                .onFailure { error ->
                    val friendly = AuthValidation.friendlyErrorRes(error.message)?.let { context.getString(it) }
                    notice = Notice(friendly ?: error.message ?: context.getString(R.string.auth_failed), isError = true)
                }
        }
    }

    SolaceCard {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(Icons.Outlined.Place, size = 64.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if (registering) R.string.auth_title_register else R.string.auth_title_login), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(stringResource(R.string.auth_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to R.string.action_sign_in, true to R.string.action_create_account).forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = registering == mode,
                        onClick = { registering = mode; notice = null; attempted = false },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)
                    ) { Text(stringResource(label)) }
                }
            }

            OutlinedTextField(
                value = email, onValueChange = { email = it; notice = null },
                label = { Text(stringResource(R.string.field_email)) },
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                singleLine = true, isError = emailError,
                supportingText = if (emailError) ({ Text(stringResource(R.string.auth_error_email)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it; notice = null },
                label = { Text(stringResource(R.string.field_password)) },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = stringResource(if (showPassword) R.string.auth_hide_password else R.string.auth_show_password))
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true, isError = passwordError,
                supportingText = if (passwordError || registering) ({ Text(stringResource(R.string.auth_password_hint)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth()
            )

            notice?.let { n ->
                Surface(shape = MaterialTheme.shapes.medium, color = if (n.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (n.isError) Icons.Outlined.Warning else Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(n.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Button(onClick = { submit() }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(stringResource(if (registering) R.string.action_create_account else R.string.action_sign_in))
            }
            Text(stringResource(R.string.auth_privacy_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
