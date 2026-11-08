package dev.kyouzo.solace.ui.places

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector
import dev.kyouzo.solace.R

/** Built-in place templates. [name] is what gets stored locally and in the shared cloud table. */
enum class PlaceKind(val icon: ImageVector, @StringRes val label: Int) {
    HOME(Icons.Outlined.Home, R.string.place_home),
    SCHOOL(Icons.Outlined.School, R.string.place_school),
    WORK(Icons.Outlined.Work, R.string.place_work),
    CUSTOM(Icons.Outlined.Place, R.string.place_custom);

    companion object {
        fun from(key: String): PlaceKind = entries.firstOrNull { it.name == key } ?: CUSTOM
    }
}
