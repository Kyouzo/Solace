package dev.kyouzo.solace.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutModeTest {
    @Test fun compactWhenNarrow() = assertEquals(LayoutMode.COMPACT, LayoutMode.from(atLeastMedium = false, atLeastExpanded = false))
    @Test fun mediumWhenAtLeastMediumOnly() = assertEquals(LayoutMode.MEDIUM, LayoutMode.from(atLeastMedium = true, atLeastExpanded = false))
    @Test fun expandedWins() = assertEquals(LayoutMode.EXPANDED, LayoutMode.from(atLeastMedium = true, atLeastExpanded = true))
    @Test fun expandedEvenIfMediumFlagMissing() = assertEquals(LayoutMode.EXPANDED, LayoutMode.from(atLeastMedium = false, atLeastExpanded = true))
}
