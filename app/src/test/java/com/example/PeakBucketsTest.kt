package com.example

import com.example.service.PeakBuckets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeakBucketsTest {
    @Test fun loudestBucketIsOneAndQuietOnesScaleToIt() {
        val b = PeakBuckets(4, 4_000_000)
        b.add(0, 8_000); b.add(1_000_000, 16_000); b.add(2_000_000, 32_000); b.add(3_000_000, 16_000)
        assertEquals(listOf(0.25f, 0.5f, 1f, 0.5f), b.result())
    }

    @Test fun keepsOnlyThePeakWithinABucket() {
        val b = PeakBuckets(2, 2_000_000)
        b.add(0, 1_000); b.add(500_000, 20_000); b.add(1_000_000, 10_000)
        assertEquals(listOf(1f, 0.5f), b.result())
    }

    @Test fun silenceStillDrawsAVisibleFloor() {
        val b = PeakBuckets(3, 3_000_000)
        b.add(0, 100); b.add(1_000_000, 0); b.add(2_000_000, 0)
        assertTrue(b.result().all { it >= 0.06f })
    }

    @Test fun undecodedTailTakesTheAverage() {
        val b = PeakBuckets(4, 4_000_000)
        b.add(0, 32_000); b.add(1_000_000, 16_000)
        val r = b.result()
        assertEquals(0.75f, r[2], 0.001f)
        assertEquals(0.75f, r[3], 0.001f)
    }

    @Test fun nothingDecodedMeansNoWaveform() {
        assertTrue(PeakBuckets(4, 4_000_000).result().isEmpty())
    }
}
