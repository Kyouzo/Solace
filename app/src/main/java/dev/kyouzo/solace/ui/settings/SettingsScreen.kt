package dev.kyouzo.solace.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.kyouzo.solace.R
import dev.kyouzo.solace.tracking.TrackingControl
import dev.kyouzo.solace.ui.account.AccountSection
import dev.kyouzo.solace.ui.common.BrandHeader
import dev.kyouzo.solace.ui.common.GroupDivider
import dev.kyouzo.solace.ui.common.ScreenColumn
import dev.kyouzo.solace.ui.common.SectionLabel
import dev.kyouzo.solace.ui.common.SettingRow
import dev.kyouzo.solace.ui.common.SettingsGroup
import dev.kyouzo.solace.ui.common.rememberLocationPermission
import dev.kyouzo.solace.ui.common.rememberSyncUiState

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val permission = rememberLocationPermission()
    var tracking by remember { mutableStateOf(TrackingControl.isActive(context)) }
    val sync by rememberSyncUiState()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    ScreenColumn(spacing = 4.dp) {
        item { BrandHeader(stringResource(R.string.settings_title)) }
        item { Text(stringResource(R.string.settings_subtitle), color = muted) }

        item { SectionLabel(stringResource(R.string.group_account)) }
        item { AccountSection() }

        item { SectionLabel(stringResource(R.string.group_recording)) }
        item {
            SettingsGroup {
                SettingRow(
                    icon = Icons.Outlined.RadioButtonChecked,
                    title = stringResource(R.string.rec_title),
                    subtitle = stringResource(if (!permission.granted) R.string.rec_needs_permission else if (tracking) R.string.rec_active else R.string.rec_stopped),
                    trailing = {
                        Switch(checked = tracking && permission.granted, onCheckedChange = { on ->
                            when {
                                !permission.granted -> permission.request()
                                on -> { TrackingControl.start(context); tracking = true }
                                else -> { TrackingControl.stop(context); tracking = false }
                            }
                        })
                    }
                )
                GroupDivider()
                PrecisionRow()
                GroupDivider()
                PermissionsRow()
            }
        }

        item { SectionLabel(stringResource(R.string.group_sharing)) }
        item {
            SettingsGroup {
                SettingRow(
                    icon = Icons.Outlined.Share,
                    title = stringResource(R.string.row_location_sharing),
                    subtitle = stringResource(when { sync.sharing -> R.string.sharing_on; sync.paired -> R.string.sharing_off_paired; else -> R.string.sharing_off_unpaired })
                )
                GroupDivider()
                SettingRow(
                    icon = Icons.Outlined.People,
                    title = stringResource(R.string.row_partner),
                    subtitle = stringResource(if (sync.paired) R.string.partner_connected else R.string.partner_not_connected)
                )
            }
        }

        item { SectionLabel(stringResource(R.string.group_privacy)) }
        item { SettingsGroup { RetentionRow() } }

        item { SectionLabel(stringResource(R.string.group_storage)) }
        item {
            SettingsGroup {
                OfflineMapRow()
                GroupDivider()
                SettingRow(Icons.Outlined.Map, stringResource(R.string.row_map_tiles), stringResource(R.string.map_tiles_value))
                GroupDivider()
                SettingRow(Icons.Outlined.PhoneAndroid, stringResource(R.string.row_local_history), stringResource(R.string.local_history_value))
            }
        }
    }
}
