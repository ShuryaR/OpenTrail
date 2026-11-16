package com.example

import com.example.util.OemGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemGuideTest {
    @Test fun onePlusFamilyShareOneGuide() {
        listOf("OnePlus", "OPPO", "realme").forEach {
            assertEquals("OnePlus / OPPO / Realme", OemGuide.forManufacturer(it).brand)
        }
    }
    @Test fun samsungAndXiaomiSuggestFolderWatcher() {
        assertTrue(OemGuide.forManufacturer("samsung").suggestsFolderWatcher)
        assertTrue(OemGuide.forManufacturer("Xiaomi").suggestsFolderWatcher)
        assertFalse(OemGuide.forManufacturer("OnePlus").suggestsFolderWatcher)
    }
    @Test fun unknownBrandFallsBackToGenericSteps() {
        val g = OemGuide.forManufacturer("Nothing")
        assertEquals("Nothing", g.brand)
        assertTrue(g.steps.size >= 3)
    }
    @Test fun blankManufacturerStillReadable() {
        assertEquals("your phone", OemGuide.forManufacturer("  ").brand)
    }
}
