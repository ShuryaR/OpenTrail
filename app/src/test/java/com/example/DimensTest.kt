package com.example

import com.example.ui.theme.LayoutDensity
import com.example.ui.theme.resolveDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensTest {
    @Test fun autoIsComfortableOnATallScreenWithNormalText() =
        assertFalse(resolveDimens(LayoutDensity.AUTO, 900f, 1.0f).compact)

    @Test fun autoIsCompactOnAShortScreen() =
        assertTrue(resolveDimens(LayoutDensity.AUTO, 700f, 1.0f).compact)

    @Test fun autoIsCompactWhenTextIsLarge() {
        assertTrue(resolveDimens(LayoutDensity.AUTO, 900f, 1.15f).compact)
        assertTrue(resolveDimens(LayoutDensity.AUTO, 900f, 1.5f).compact)
    }

    @Test fun smallTextOnATallScreenStaysComfortable() =
        assertFalse(resolveDimens(LayoutDensity.AUTO, 856f, 0.9f).compact)

    @Test fun explicitChoiceAlwaysWins() {
        assertTrue(resolveDimens(LayoutDensity.COMPACT, 2000f, 0.8f).compact)
        assertFalse(resolveDimens(LayoutDensity.COMFORTABLE, 400f, 2.0f).compact)
    }

    @Test fun compactIsTighterThanComfortable() {
        val c = resolveDimens(LayoutDensity.COMPACT, 800f, 1f)
        val f = resolveDimens(LayoutDensity.COMFORTABLE, 800f, 1f)
        assertTrue(c.cardPad < f.cardPad && c.listGap < f.listGap)
    }

    @Test fun unknownSavedValueFallsBackToAuto() {
        assertEquals(LayoutDensity.AUTO, LayoutDensity.fromName("nonsense"))
        assertEquals(LayoutDensity.AUTO, LayoutDensity.fromName(null))
    }
}
