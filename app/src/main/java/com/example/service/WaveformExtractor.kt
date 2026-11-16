package com.example.service

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import kotlin.math.abs

/** Collects per-time-bucket peak levels and turns them into 0..1 bar heights. */
class PeakBuckets(private val bucketCount: Int, private val durationUs: Long) {
    private val peaks = FloatArray(bucketCount)
    private val seen = BooleanArray(bucketCount)

    /** [peak] is the largest absolute sample (0..32768) in a decoded block that starts at [timeUs]. */
    fun add(timeUs: Long, peak: Int) {
        if (durationUs <= 0L) return
        val idx = ((timeUs.coerceAtLeast(0L).toDouble() / durationUs) * bucketCount).toInt()
            .coerceIn(0, bucketCount - 1)
        peaks[idx] = maxOf(peaks[idx], peak / 32768f)
        seen[idx] = true
    }

    /**
     * Normalised so the loudest bar is 1.0, with a visible floor. Buckets that were never decoded
     * (long calls are only sampled from the start) take the average so the bar is not blank.
     */
    fun result(): List<Float> {
        val decoded = peaks.filterIndexed { i, _ -> seen[i] }
        if (decoded.isEmpty()) return emptyList()
        val max = decoded.max().coerceAtLeast(0.0001f)
        val avg = decoded.average().toFloat() / max
        return List(bucketCount) { i ->
            val v = if (seen[i]) peaks[i] / max else avg
            v.coerceIn(0.06f, 1f)
        }
    }
}

object WaveformExtractor {
    private const val MAX_DECODE_US = 6L * 60 * 1_000_000

    /** Decodes [file] to PCM and returns [buckets] bar heights, or empty if it cannot be decoded. */
    fun extract(file: File, buckets: Int = 56): List<Float> = runCatching {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return emptyList()
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        if (durationUs <= 0L) return emptyList()
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        val acc = PeakBuckets(buckets, durationUs)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0 || extractor.sampleTime > MAX_DECODE_US) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val out = codec.getOutputBuffer(outIdx)!!
                    out.position(info.offset)
                    out.limit(info.offset + info.size)
                    val shorts = out.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    var peak = 0
                    while (shorts.hasRemaining()) peak = maxOf(peak, abs(shorts.get().toInt()))
                    acc.add(info.presentationTimeUs, peak)
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            extractor.release()
        }
        acc.result()
    }.getOrDefault(emptyList())
}
