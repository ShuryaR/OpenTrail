package com.example

import com.example.util.PhoneNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class DialableNumberTest {
    private fun d(raw: String?) = PhoneNormalizer.toDialable(raw)

    @Test fun crmStyleNumberWithoutPlusGetsOne() = assertEquals("+917904183832", d("917904183832"))
    @Test fun plain10DigitMobileGetsCountryCode() = assertEquals("+919876543210", d("9876543210"))
    @Test fun leadingZeroNationalNumber() = assertEquals("+919876543210", d("09876543210"))
    @Test fun alreadyInternationalIsKept() = assertEquals("+917904183832", d("+91 79041 83832"))
    @Test fun otherCountriesWithPlusAreKept() = assertEquals("+442079460958", d("+44 20 7946 0958"))
    @Test fun doubleZeroExitCodeBecomesPlus() = assertEquals("+442079460958", d("00442079460958"))
    @Test fun messyFormattingIsCleaned() = assertEquals("+919876543210", d("(+91) 98765-43210"))
    @Test fun landlineWithStdCode() = assertEquals("+914423456789", d("044 2345 6789"))
    @Test fun ussdAndShortCodesAreUntouched() {
        assertEquals("*123#", d("*123#"))
        assertEquals("112", d("112"))
    }
    @Test fun blankAndNullGiveNothing() {
        assertEquals("", d(null))
        assertEquals("", d("  "))
        assertEquals("", d("abc"))
    }
    @Test fun placeholderRepNumberIsLeftAlone() = assertEquals("0000000000", d("0000000000"))
}
