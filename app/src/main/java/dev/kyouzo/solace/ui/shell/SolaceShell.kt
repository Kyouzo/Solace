package dev.kyouzo.solace.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.window.core.layout.WindowSizeClass
import dev.kyouzo.solace.R
import dev.kyouzo.solace.ui.history.HistoryScreen
import dev.kyouzo.solace.ui.map.MapScreen
import dev.kyouzo.solace.ui.places.PlacesScreen
import dev.kyouzo.solace.ui.settings.SettingsScreen

/** Coarse width bucket derived once, in the shell, from the window size class. */
enum class LayoutMode {
    COMPACT, MEDIUM, EXPANDED;

    companion object {
        fun from(atLeastMedium: Boolean, atLeastExpanded: Boolean): LayoutMode = when {
            atLeastExpanded -> EXPANDED
            atLeastMedium -> MEDIUM
            else -> COMPACT
        }
    }
}

/** Screens read this instead of querying window size themselves. */
val LocalLayoutMode = compositionLocalOf { LayoutMode.COMPACT }

@Composable
fun rememberLayoutMode(): LayoutMode {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return LayoutMode.from(
        atLeastMedium = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND),
        atLeastExpanded = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    )
}

/** Account and partner pairing live inside Settings, so there are four top-level destinations. */
enum class Destination(@StringRes val label: Int, val icon: ImageVector) {
    Map(R.string.nav_map, Icons.Outlined.Map),
    History(R.string.nav_history, Icons.Outlined.History),
    Places(R.string.nav_places, Icons.Outlined.Home),
    Settings(R.string.nav_settings, Icons.Outlined.Settings)
}

/** Navigation chrome (bar / rail / drawer) is chosen by the scaffold from the window size; screens never know. */
@Composable
fun SolaceShell() {
    var selected by rememberSaveable { mutableIntStateOf(Destination.Map.ordinal) }
    val mode = rememberLayoutMode()
    CompositionLocalProvider(LocalLayoutMode provides mode) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                Destination.entries.forEach { destination ->
                    item(
                        selected = selected == destination.ordinal,
                        onClick = { selected = destination.ordinal },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) }
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
                when (Destination.entries[selected]) {
                    Destination.Map -> MapScreen(onOpenSettings = { selected = Destination.Settings.ordinal })
                    Destination.History -> HistoryScreen()
                    Destination.Places -> PlacesScreen()
                    Destination.Settings -> SettingsScreen()
                }
            }
        }
    }
}
