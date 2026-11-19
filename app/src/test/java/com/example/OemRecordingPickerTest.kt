package com.example

import com.example.service.OemFile
import com.example.service.OemRecordingPicker
import com.example.data.local.oemRecorderDefault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OemRecordingPickerTest {
    private val start = 1_000_000L
    private val end = 1_060_000L
    private fun f(name: String, mod: Long, size: Long = 50_000) = OemFile(name, size, mod, File(name))

    @Test fun picksTheFileClosedDuringTheCall() =
        assertEquals("a.m4a", OemRecordingPicker.pick(listOf(f("a.m4a", end - 1_000)), start, end)!!.name)

    @Test fun ignoresOlderCalls() =
        assertNull(OemRecordingPicker.pick(listOf(f("old.m4a", start - 600_000)), start, end))

    @Test fun ignoresFilesFromAfterTheCall() =
        assertNull(OemRecordingPicker.pick(listOf(f("next.m4a", end + 120_000)), start, end))

    @Test fun toleratesTheFileFinishingJustAfterHangUp() =
        assertEquals("a.m4a", OemRecordingPicker.pick(listOf(f("a.m4a", end + 8_000)), start, end)!!.name)

    @Test fun ignoresTinyAndNonAudioFiles() {
        assertNull(OemRecordingPicker.pick(listOf(f("a.m4a", end, 100), f("a.txt", end)), start, end))
    }

    @Test fun newestMatchingFileWins() =
        assertEquals("b.m4a", OemRecordingPicker.pick(listOf(f("a.m4a", end - 20_000), f("b.m4a", end - 2_000)), start, end)!!.name)

    @Test fun samsungUsesItsOwnRecorderByDefaultOthersDoNot() {
        assertTrue(oemRecorderDefault("samsung"))
        assertTrue(oemRecorderDefault("Samsung "))
        assertTrue(oemRecorderDefault("OPPO"))
        assertTrue(oemRecorderDefault("Xiaomi"))
        assertFalse(oemRecorderDefault("OnePlus"))
        assertFalse(oemRecorderDefault("Google"))
    }
}
