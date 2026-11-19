package com.astranomia.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.sin

/**
 * Procedural Audio Synthesizer for Android:
 * - Plays Astronomia theme in Menu
 * - Plays Agro-Phonk bass & cowbells in-game
 * - Sound effects for Combine, Splat, Cannon
 */
class SoundManager {

    var isSoundEnabled: Boolean = true
    private var musicJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val sampleRate = 22050

    fun startMenuMusic() {
        stopMusic()
        if (!isSoundEnabled) return
        musicJob = scope.launch {
            while (isActive) {
                // Astronomia lead synth notes (G4, D5, C5, Bb4, A4, G4...)
                val notes = doubleArrayOf(392.0, 392.0, 392.0, 392.0, 587.33, 587.33, 587.33, 587.33, 523.25, 523.25, 466.16, 392.0)
                for (freq in notes) {
                    if (!isActive) break
                    playTone(freq, 120)
                    delay(125)
                }
                delay(300)
            }
        }
    }

    fun startGameMusic() {
        stopMusic()
        if (!isSoundEnabled) return
        musicJob = scope.launch {
            while (isActive) {
                // Agro-Phonk rhythm (808 Sub-bass kick + Cowbell stabs)
                playSubBass(110.0, 40.0, 150)
                playCowbell(587.33, 80)
                delay(220)
                playCowbell(698.46, 80)
                delay(220)
                playSubBass(120.0, 45.0, 150)
                playCowbell(880.0, 80)
                delay(220)
                playCowbell(783.99, 80)
                delay(220)
            }
        }
    }

    fun stopMusic() {
        musicJob?.cancel()
        musicJob = null
    }

    fun playShotgun() {
        if (!isSoundEnabled) return
        scope.launch {
            playNoise(100)
            playSubBass(140.0, 30.0, 180)
        }
    }

    fun playAlienSplat() {
        if (!isSoundEnabled) return
        scope.launch {
            playTone(280.0, 40)
            playTone(90.0, 50)
        }
    }

    fun playUfoCrash() {
        if (!isSoundEnabled) return
        scope.launch {
            playSubBass(160.0, 25.0, 400)
            playNoise(250)
        }
    }

    fun playNitroActivation() {
        if (!isSoundEnabled) return
        scope.launch {
            val stinger = doubleArrayOf(392.0, 587.33, 523.25, 466.16, 783.99)
            for (f in stinger) {
                playTone(f, 60)
                delay(65)
            }
            playSubBass(180.0, 30.0, 500)
        }
    }

    private fun playTone(freq: Double, durationMs: Int) {
        val count = (sampleRate * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val decay = 1.0 - (i.toDouble() / count)
            val wave = sin(2.0 * Math.PI * freq * t) * decay
            samples[i] = (wave * Short.MAX_VALUE * 0.4).toInt().toShort()
        }
        writeAudio(samples)
    }

    private fun playSubBass(startFreq: Double, endFreq: Double, durationMs: Int) {
        val count = (sampleRate * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val progress = i.toDouble() / count
            val curFreq = startFreq + (endFreq - startFreq) * progress
            val t = i.toDouble() / sampleRate
            val decay = (1.0 - progress)
            val wave = sin(2.0 * Math.PI * curFreq * t) * decay
            samples[i] = (wave * Short.MAX_VALUE * 0.6).toInt().toShort()
        }
        writeAudio(samples)
    }

    private fun playCowbell(freq: Double, durationMs: Int) {
        val count = (sampleRate * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val decay = 1.0 - (i.toDouble() / count)
            // Square wave approximation for 808 Cowbell
            val wave1 = if (sin(2.0 * Math.PI * freq * t) > 0) 1.0 else -1.0
            val wave2 = if (sin(2.0 * Math.PI * (freq * 1.48) * t) > 0) 0.7 else -0.7
            samples[i] = ((wave1 + wave2) * 0.5 * decay * Short.MAX_VALUE * 0.35).toInt().toShort()
        }
        writeAudio(samples)
    }

    private fun playNoise(durationMs: Int) {
        val count = (sampleRate * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val decay = 1.0 - (i.toDouble() / count)
            val noise = (Math.random() * 2.0 - 1.0) * decay
            samples[i] = (noise * Short.MAX_VALUE * 0.4).toInt().toShort()
        }
        writeAudio(samples)
    }

    private fun writeAudio(samples: ShortArray) {
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            track.play()
            scope.launch {
                delay(samples.size * 1000L / sampleRate + 50)
                track.release()
            }
        } catch (e: Exception) {}
    }

    fun release() {
        stopMusic()
        scope.cancel()
    }
}
