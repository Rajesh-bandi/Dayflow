package com.dayflow.domain

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random
import kotlin.math.sin

/**
 * Calming offline ambient soundscapes for deep focus sessions.
 * Synthesized directly on-device so it requires zero downloads, zero network, and works offline.
 */
object FocusAmbientSound {

    enum class Soundscape(val label: String, val detail: String) {
        OFF("Off", "Quiet sanctuary"),
        GENTLE_RAIN("Gentle Rain", "Soft rain drops and distant mist"),
        BROWN_NOISE("Deep Flow", "Low-frequency steady warmth for deep concentration"),
        FOCUS_PULSE("Binaural Calming", "Subtle alpha-wave rhythm for calm flow")
    }

    private var activeTrack: AudioTrack? = null
    private var currentSoundscape: Soundscape = Soundscape.OFF

    @Synchronized
    fun play(soundscape: Soundscape) {
        if (currentSoundscape == soundscape && activeTrack != null) return
        stop()
        if (soundscape == Soundscape.OFF) return

        currentSoundscape = soundscape
        val sampleRate = 22050
        val durationSeconds = 4
        val totalSamples = sampleRate * durationSeconds
        val samples = ShortArray(totalSamples)
        val rng = Random(42)

        when (soundscape) {
            Soundscape.GENTLE_RAIN -> {
                // Filtered pink/rain noise with gentle high-frequency raindrops
                var b0 = 0.0; var b1 = 0.0; var b2 = 0.0
                for (i in 0 until totalSamples) {
                    val white = rng.nextDouble() * 2.0 - 1.0
                    b0 = 0.99 * b0 + white * 0.05
                    b1 = 0.96 * b1 + white * 0.11
                    b2 = 0.86 * b2 + white * 0.25
                    val pink = (b0 + b1 + b2) * 0.25
                    val drop = if (rng.nextDouble() < 0.003) (rng.nextDouble() * 0.4) else 0.0
                    val sample = (pink * 0.6 + drop).coerceIn(-1.0, 1.0)
                    samples[i] = (sample * 16000).toInt().toShort()
                }
            }
            Soundscape.BROWN_NOISE -> {
                // Integrated brown noise: deeply soothing low-frequency rumble
                var last = 0.0
                for (i in 0 until totalSamples) {
                    val white = rng.nextDouble() * 2.0 - 1.0
                    last = (last + (0.02 * white)) / 1.02
                    val sample = (last * 3.5).coerceIn(-1.0, 1.0)
                    samples[i] = (sample * 18000).toInt().toShort()
                }
            }
            Soundscape.FOCUS_PULSE -> {
                // Soft dual harmonic tone with gentle 10Hz alpha wave pulse
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val carrier = sin(2.0 * Math.PI * 160.0 * t) * 0.5 + sin(2.0 * Math.PI * 170.0 * t) * 0.5
                    val pulse = (1.0 + sin(2.0 * Math.PI * 10.0 * t)) * 0.5
                    val sample = (carrier * (0.3 + 0.7 * pulse) * 0.35).coerceIn(-1.0, 1.0)
                    samples[i] = (sample * 14000).toInt().toShort()
                }
            }
            Soundscape.OFF -> return
        }

        // Apply smooth crossfade at loop boundary to prevent clicking
        val fadeSamples = sampleRate / 20
        for (i in 0 until fadeSamples) {
            val weight = i.toDouble() / fadeSamples
            samples[i] = (samples[i] * weight).toInt().toShort()
            val endIdx = totalSamples - 1 - i
            samples[endIdx] = (samples[endIdx] * weight).toInt().toShort()
        }

        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(totalSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, totalSamples)
            track.setLoopPoints(0, totalSamples, -1)
            track.play()
            activeTrack = track
        } catch (_: Throwable) {
            stop()
        }
    }

    @Synchronized
    fun stop() {
        runCatching {
            activeTrack?.stop()
            activeTrack?.release()
        }
        activeTrack = null
        currentSoundscape = Soundscape.OFF
    }

    fun isPlaying(): Boolean = activeTrack != null && currentSoundscape != Soundscape.OFF

    fun current(): Soundscape = currentSoundscape
}
