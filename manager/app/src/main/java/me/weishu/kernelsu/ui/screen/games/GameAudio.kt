package me.weishu.kernelsu.ui.screen.games

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tiny square-wave chiptune player for the dashboard mini-games.
 *
 * Notes are plain MIDI note numbers, synthesised into short PCM jingles with
 * [AudioTrack]; that keeps the easter egg self-contained (no assets, no
 * permissions) while still sounding like a minimal retro soundtrack.
 */
object GameAudio {
    private const val SAMPLE_RATE = 22050

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var enabled: Boolean = true

    fun start() = play(listOf(72 to 90, 76 to 90, 79 to 150))

    fun score() = play(listOf(84 to 60))

    fun gameOver() = play(listOf(67 to 110, 64 to 110, 60 to 180))

    private fun play(notes: List<Pair<Int, Int>>) {
        if (!enabled || notes.isEmpty()) return
        scope.launch {
            val totalMs = notes.sumOf { it.second }
            val samples = ShortArray(SAMPLE_RATE * (totalMs + notes.size * 12 + 60) / 1000)
            var writeIndex = 0
            for ((note, durationMs) in notes) {
                val count = (SAMPLE_RATE * durationMs / 1000).coerceAtMost(samples.size - writeIndex)
                if (count <= 0) break
                val frequency = 440.0 * 2.0.pow((note - 69) / 12.0)
                for (i in 0 until count) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = envelopeAt(i, count)
                    val wave = if (sin(2.0 * PI * frequency * t) >= 0.0) 1.0 else -1.0
                    samples[writeIndex + i] =
                        (wave * envelope * Short.MAX_VALUE * 0.16).toInt().toShort()
                }
                val silence = minOf(SAMPLE_RATE / 100, samples.size - writeIndex - count)
                writeIndex += count + silence
            }
            if (writeIndex <= 0) return@launch

            val track = try {
                buildTrack(samples.size)
            } catch (_: Exception) {
                return@launch
            }

            try {
                track.write(samples, 0, samples.size)
                track.play()
                delay(totalMs.toLong() + 80L)
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }
    }

    private fun buildTrack(bytes: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(bytes * 2, minBuffer))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
    }

    // Short attack and release so the square wave does not click.
    private fun envelopeAt(index: Int, count: Int): Double {
        val attack = (count * 0.04f).toInt().coerceAtLeast(1)
        val release = (count * 0.22f).toInt().coerceAtLeast(1)
        return when {
            index < attack -> index.toDouble() / attack
            index > count - release -> (count - index).toDouble() / release
            else -> 1.0
        }.coerceIn(0.0, 1.0)
    }
}
