package com.example

import com.example.util.PhoneNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNormalizerTest {

    @Test
    fun `strips spaces dashes parentheses and country codes`() {
        assertEquals("9876543210", PhoneNormalizer.normalize("+91 98765-43210"))
        assertEquals("9876543210", PhoneNormalizer.normalize("098765 43210"))
        assertEquals("9876543210", PhoneNormalizer.normalize("919876543210"))
        assertEquals("4155551234", PhoneNormalizer.normalize("+1 (415) 555-1234"))
        assertEquals("4155551234", PhoneNormalizer.normalize("0014155551234"))
    }

    @Test
    fun `formats call duration as mm_ss`() {
        assertEquals("00:00", PhoneNormalizer.formatDuration(0))
        assertEquals("01:05", PhoneNormalizer.formatDuration(65))
        assertEquals("12:30", PhoneNormalizer.formatDuration(750))
    }

    @Test
    fun `formats phone numbers into readable chunks`() {
        assertEquals("+91 98765 43210", PhoneNormalizer.formatDisplay("+919876543210"))
        assertEquals("98765 43210", PhoneNormalizer.formatDisplay("9876543210"))
        assertEquals("+1 (415) 555-1234", PhoneNormalizer.formatDisplay("+14155551234"))
    }

    @Test
    fun `sanitizes messy pasted dialer inputs liberally`() {
        assertEquals("+919876543210", PhoneNormalizer.sanitizeDialInput(" +91 (987) 654-3210 "))
        assertEquals("*123#", PhoneNormalizer.sanitizeDialInput("call *123# now"))
    }
}
