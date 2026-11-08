package dev.kyouzo.solace.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MapRegionTest {
    @Test fun parsesAWellFormedLine() {
        val region = MapRegion.parse("africa/egypt|Egypt|63")
        assertNotNull(region)
        assertEquals("Egypt", region!!.name)
        assertEquals(63, region.sizeMb)
        assertEquals("africa", region.group)
    }

    @Test fun buildsTheDownloadUrlAndAFlatFileName() {
        val region = MapRegion("europe/germany/bayern", "Germany · Bayern", 400)
        assertEquals("https://download.mapsforge.org/maps/v5/europe/germany/bayern.map", region.url)
        assertEquals("europe_germany_bayern.map", region.fileName)
    }

    @Test fun rejectsMalformedLines() {
        listOf("", "   ", "a|b", "a|b|notanumber", "|Name|5", "path||5", "a|b|1|extra").forEach { assertNull(it, MapRegion.parse(it)) }
    }

    @Test fun groupLabelsAreReadable() {
        assertEquals("Africa", MapRegion.groupLabel("africa"))
        assertEquals("Australia Oceania", MapRegion.groupLabel("australia-oceania"))
        assertEquals("North America", MapRegion.groupLabel("north-america"))
    }
}
