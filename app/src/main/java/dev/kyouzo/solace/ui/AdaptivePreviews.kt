package dev.kyouzo.solace.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.kyouzo.solace.data.local.DaySummary
import dev.kyouzo.solace.ui.common.SettingRow
import dev.kyouzo.solace.ui.common.SettingsGroup
import dev.kyouzo.solace.ui.history.HistoryListPane
import dev.kyouzo.solace.ui.map.ControlCard
import dev.kyouzo.solace.ui.theme.SolaceTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share

/** Renders a preview at the three window-size buckets the app adapts to. */
@Preview(name = "Compact", widthDp = 360, heightDp = 780, showBackground = true)
@Preview(name = "Medium", widthDp = 700, heightDp = 900, showBackground = true)
@Preview(name = "Expanded", widthDp = 1100, heightDp = 900, showBackground = true)
annotation class AdaptivePreviews

private val sampleDays = listOf(
    DaySummary("2026-11-08", 1420, 1_794_000_000_000),
    DaySummary("2026-11-07", 960, 1_793_900_000_000),
    DaySummary("2026-11-06", 12, 1_793_800_000_000)
)

@AdaptivePreviews
@Composable
private fun HistoryListPreview() = SolaceTheme { HistoryListPane(sampleDays, selected = sampleDays.first().firstAt, onSelect = {}) }

@AdaptivePreviews
@Composable
private fun HistoryListEmptyPreview() = SolaceTheme { HistoryListPane(emptyList(), selected = null, onSelect = {}) }

@AdaptivePreviews
@Composable
private fun ControlCardReadyPreview() = SolaceTheme {
    ControlCard(tracking = false, granted = true, last = null, partner = null, paired = false, onRecordingAction = {}, onShowPartner = {}, onOpenSettings = {})
}

@AdaptivePreviews
@Composable
private fun ControlCardDeniedPreview() = SolaceTheme {
    ControlCard(tracking = false, granted = false, last = null, partner = null, paired = false, onRecordingAction = {}, onShowPartner = {}, onOpenSettings = {})
}

@AdaptivePreviews
@Composable
private fun SettingsGroupPreview() = SolaceTheme {
    SettingsGroup { SettingRow(Icons.Outlined.Share, "Location sharing", "Off — pair with your partner first") }
}
