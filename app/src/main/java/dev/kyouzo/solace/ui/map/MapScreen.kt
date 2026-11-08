package dev.kyouzo.solace.ui.map

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kyouzo.solace.R
import dev.kyouzo.solace.data.local.AppDatabase
import dev.kyouzo.solace.data.local.LocationPointEntity
import dev.kyouzo.solace.data.local.RemoteLocationEntity
import dev.kyouzo.solace.data.remote.SupabaseAuthRepository
import dev.kyouzo.solace.tracking.TrackingControl
import dev.kyouzo.solace.ui.account.PartnerPanel
import dev.kyouzo.solace.ui.common.IconBadge
import dev.kyouzo.solace.ui.common.PrivacyCard
import dev.kyouzo.solace.ui.common.SectionTitle
import dev.kyouzo.solace.ui.common.SolaceCard
import dev.kyouzo.solace.ui.common.StatusCard
import dev.kyouzo.solace.ui.common.StatusPill
import dev.kyouzo.solace.ui.common.rememberLocationPermission
import dev.kyouzo.solace.ui.common.rememberSyncUiState
import dev.kyouzo.solace.ui.shell.LayoutMode
import dev.kyouzo.solace.ui.shell.LocalLayoutMode
import dev.kyouzo.solace.ui.theme.SolaceMint
import dev.kyouzo.solace.ui.theme.SolaceRose
import org.osmdroid.util.GeoPoint
import java.text.DateFormat
import java.util.Date

@Composable
fun MapScreen(onOpenSettings: () -> Unit) {
    when (LocalLayoutMode.current) {
        LayoutMode.COMPACT -> MapSurface(Modifier.fillMaxSize(), onOpenSettings)
        else -> ExpandedMap(onOpenSettings)
    }
}

/** Medium/expanded: the map is the main pane; account and partner controls sit in a supporting pane. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun ExpandedMap(onOpenSettings: () -> Unit) {
    val navigator = rememberSupportingPaneScaffoldNavigator()
    val context = LocalContext.current
    val signedIn = remember { SupabaseAuthRepository(context).savedSession() != null }
    SupportingPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        mainPane = { AnimatedPane { MapSurface(Modifier.fillMaxSize().padding(16.dp).clip(MaterialTheme.shapes.extraLarge), onOpenSettings) } },
        supportingPane = {
            AnimatedPane {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dimensionResource(R.dimen.screen_padding)), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusCard()
                    SectionTitle(stringResource(R.string.section_together))
                    if (signedIn) PartnerPanel()
                    else PrivacyCard(stringResource(R.string.supporting_sign_in_title), stringResource(R.string.supporting_sign_in_body), stringResource(R.string.supporting_sign_in_action), onOpenSettings)
                }
            }
        }
    )
}

/** Full-bleed map with a status pill, a locate button and a floating control card. */
@Composable
private fun MapSurface(modifier: Modifier, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val permission = rememberLocationPermission()
    var tracking by remember { mutableStateOf(TrackingControl.isActive(context)) }
    var follow by remember { mutableStateOf(true) }
    var focusId by remember { mutableIntStateOf(0) }
    var focus by remember { mutableStateOf<FocusRequest?>(null) }
    val dao = remember { AppDatabase.get(context).locationDao() }
    val mine by dao.observeLast().collectAsState(null)
    val partner by dao.observeLatestRemote().collectAsState(null)
    val sync by rememberSyncUiState()
    val myPoint = mine?.let { GeoPoint(it.latitude, it.longitude) }
    val partnerPoint = partner?.let { GeoPoint(it.latitude, it.longitude) }

    Box(modifier) {
        LiveMap(Modifier.fillMaxSize(), me = myPoint, partner = partnerPoint, follow = follow && tracking, focus = focus, onUserInteract = { follow = false }, showAttribution = false)

        StatusPill(
            text = stringResource(if (tracking) R.string.pill_recording else R.string.pill_paused),
            dot = if (tracking) SolaceMint else MaterialTheme.colorScheme.outline,
            pulse = tracking,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        )
        SmallFloatingActionButton(
            onClick = { myPoint?.let { follow = true; focusId++; focus = FocusRequest(focusId, it) } },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            containerColor = if (follow && tracking) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        ) { Icon(Icons.Outlined.MyLocation, contentDescription = stringResource(R.string.action_locate_me)) }

        ControlCard(
            tracking = tracking, granted = permission.granted, last = mine,
            partner = partner, paired = sync.paired,
            onRecordingAction = {
                when {
                    !permission.granted -> permission.request()
                    tracking -> { TrackingControl.stop(context); tracking = false }
                    else -> { TrackingControl.start(context); tracking = true; follow = true }
                }
            },
            onShowPartner = { partnerPoint?.let { focusId++; follow = false; focus = FocusRequest(focusId, it) } },
            onOpenSettings = onOpenSettings,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).widthIn(max = 560.dp)
        )
    }
}

/** Stateless floating card: recording status + one primary action, then a partner line. */
@Composable
fun ControlCard(
    tracking: Boolean,
    granted: Boolean,
    last: LocationPointEntity?,
    partner: RemoteLocationEntity?,
    paired: Boolean,
    onRecordingAction: () -> Unit,
    onShowPartner: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    SolaceCard(modifier = modifier, container = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (tracking) R.string.hero_recording_title else if (granted) R.string.hero_ready_title else R.string.hero_denied_title), style = MaterialTheme.typography.titleMedium)
                    Text(subtitle(tracking, granted, last), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (tracking && granted) {
                    FilledTonalButton(onClick = onRecordingAction) { Text(stringResource(R.string.action_stop)) }
                } else {
                    Button(onClick = onRecordingAction, colors = ButtonDefaults.buttonColors()) {
                        Text(stringResource(if (!granted) R.string.action_allow_location else R.string.action_start))
                    }
                }
            }
            PartnerLine(partner, paired, onShowPartner, onOpenSettings)
            Text(MapTiles.attribution, style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun subtitle(tracking: Boolean, granted: Boolean, last: LocationPointEntity?): String = when {
    !granted -> stringResource(R.string.hero_denied_body)
    tracking && last != null -> stringResource(R.string.map_last_fix, last.accuracy.toInt(), DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(last.recordedAt)))
    tracking -> stringResource(R.string.map_waiting_gps)
    else -> stringResource(R.string.hero_ready_body)
}

@Composable
private fun PartnerLine(partner: RemoteLocationEntity?, paired: Boolean, onShow: () -> Unit, onSetUp: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(Icons.Outlined.Person, container = MaterialTheme.colorScheme.tertiaryContainer, tint = MaterialTheme.colorScheme.onTertiaryContainer, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(if (paired) R.string.row_partner else R.string.partner_none_title), fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    partner != null -> stringResource(R.string.partner_seen, DateUtils.getRelativeTimeSpanString(partner.recordedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString())
                    paired -> stringResource(R.string.partner_waiting)
                    else -> stringResource(R.string.partner_none_body)
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when {
            partner != null -> TextButton(onClick = onShow) { Text(stringResource(R.string.action_show)) }
            !paired -> TextButton(onClick = onSetUp) { Text(stringResource(R.string.action_set_up)) }
        }
    }
}
