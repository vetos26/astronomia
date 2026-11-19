package com.astranomia.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.sin

/**
 * Procedural Audio Synthesizer for Android:
 * - Single persistent AudioTrack stream with WRITE_NON_BLOCKING (zero audio deadlocks)
 * - Astronomia theme in Menu & Agro-Phonk bass/cowbells in game
 * - Sound effects for Combine, Corn harvest, Alien splat, Klaxon horn
 */
class SoundManager {

    @Volatile var isSoundEnabled: Boolean = true

    private val sampleRate = 22050
    @Volatile private var audioTrack: AudioTrack? = null

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("SoundManager", "Audio coroutine error", throwable)
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + coroutineExceptionHandler)
    private var musicJob: Job? = null

    @Volatile private var lastHarvestSoundTime = 0L

    init {
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            audioTrack = AudioTrack.Builder()
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
                .setBufferSizeInBytes(minBufSize * 2)
                .build()

            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.play()
            } else {
                Log.w("SoundManager", "AudioTrack state not initialized")
                audioTrack = null
            }
        } catch (t: Throwable) {
            Log.e("SoundManager", "Failed to initialize AudioTrack", t)
            audioTrack = null
        }
    }

    fun startMenuMusic() {
        stopMusic()
        if (!isSoundEnabled || audioTrack == null) return
        musicJob = scope.launch {
            try {
                val notes = doubleArrayOf(
                    392.0, 392.0, 392.0, 392.0,
                    587.33, 587.33, 587.33, 587.33,
                    523.25, 523.25, 466.16, 392.0
                )
                while (isActive && isSoundEnabled) {
                    for (freq in notes) {
                        if (!isActive || !isSoundEnabled) break
                        playTone(freq, 110)
                        delay(125)
                    }
                    delay(300)
                }
            } catch (t: Throwable) {
                Log.e("SoundManager", "Menu music error", t)
            }
        }
    }

    fun startGameMusic() {
        stopMusic()
        if (!isSoundEnabled || audioTrack == null) return
        musicJob = scope.launch {
            try {
                while (isActive && isSoundEnabled) {
                    // Agro-Phonk rhythm (808 Sub-bass kick + Cowbell stabs)
                    playSubBass(110.0, 40.0, 120)
                    playCowbell(587.33, 60)
                    delay(200)
                    if (!isActive || !isSoundEnabled) break
                    playCowbell(698.46, 60)
                    delay(200)
                    if (!isActive || !isSoundEnabled) break
                    playSubBass(120.0, 45.0, 120)
                    playCowbell(880.0, 60)
                    delay(200)
                    if (!isActive || !isSoundEnabled) break
                    playCowbell(783.99, 60)
                    delay(200)
                }
            } catch (t: Throwable) {
                Log.e("SoundManager", "Game music error", t)
            }
        }
    }

    fun stopMusic() {
        try {
            musicJob?.cancel()
            musicJob = null
            audioTrack?.pause()
        } catch (t: Throwable) {
            Log.e("SoundManager", "stopMusic error", t)
        }
    }

    fun playHorn() {
        if (!isSoundEnabled || audioTrack == null) return
        scope.launch {
            try {
                playDualTone(370.0, 440.0, 280)
            } catch (t: Throwable) {}
        }
    }

    fun playHarvestCrop() {
        if (!isSoundEnabled || audioTrack == null) return
        val now = System.currentTimeMillis()
        if (now - lastHarvestSoundTime < 80) return
        lastHarvestSoundTime = now
        scope.launch {
            try {
                playNoise(30)
                playTone(520.0, 25)
            } catch (t: Throwable) {}
        }
    }

    fun playShotgun() {
        if (!isSoundEnabled || audioTrack == null) return
        scope.launch {
            try {
                playNoise(70)
                playSubBass(140.0, 30.0, 120)
            } catch (t: Throwable) {}
        }
    }

    fun playAlienSplat() {
        if (!isSoundEnabled || audioTrack == null) return
        scope.launch {
            try {
                playTone(280.0, 30)
                playTone(90.0, 35)
            } catch (t: Throwable) {}
        }
    }

    fun playUfoCrash() {
        if (!isSoundEnabled || audioTrack == null) return
        scope.launch {
            try {
                playSubBass(160.0, 25.0, 250)
                playNoise(180)
            } catch (t: Throwable) {}
        }
    }

    fun playNitroActivation() {
        if (!isSoundEnabled || audioTrack == null) return
        scope.launch {
            try {
                val stinger = doubleArrayOf(392.0, 587.33, 523.25, 466.16, 783.99)
                for (f in stinger) {
                    playTone(f, 45)
                    delay(50)
                }
                playSubBass(180.0, 30.0, 300)
            } catch (t: Throwable) {}
        }
    }

    private fun playTone(freq: Double, durationMs: Int) {
        val count = (sampleRate * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val decay = 1.0 - (i.toDouble() / count)
            val wave = sin(2.0 * Math.PI * freq * t) * decay
            samples[i] = (wave * Short.MAX_VALUE * 0.35).toInt().toShort()
        }
        writeAudio(samples)
    }

    private fun playDualTone(freq1: Double, freq2: Double, durationMs: Int) {
        val count = (sampleRate * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val decay = 1.0 - (i.toDouble() / count) * 0.15
            val wave = (sin(2.0 * Math.PI * freq1 * t) + sin(2.0 * Math.PI * freq2 * t)) * 0.5 * decay
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
            samples[i] = (wave * Short.MAX_VALUE * 0.5).toInt().toShort()
        }
        writeAudio(samples)
    }

    private fun playCowbell(freq: Double, durationMs: Int) {
        val count = (sampleRate * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val decay = 1.0 - (i.toDouble() / count)
            val wave1 = if (sin(2.0 * Math.PI * freq * t) > 0) 1.0 else -1.0
            val wave2 = if (sin(2.0 * Math.PI * (freq * 1.48) * t) > 0) 0.7 else -0.7
            samples[i] = ((wave1 + wave2) * 0.5 * decay * Short.MAX_VALUE * 0.3).toInt().toShort()
        }
        writeAudio(samples)
    }

    private fun playNoise(durationMs: Int) {
        val count = (sampleRate * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val decay = 1.0 - (i.toDouble() / count)
            val noise = (Math.random() * 2.0 - 1.0) * decay
            samples[i] = (noise * Short.MAX_VALUE * 0.35).toInt().toShort()
        }
        writeAudio(samples)
    }

    @Synchronized
    private fun writeAudio(samples: ShortArray) {
        val track = audioTrack ?: return
        if (!isSoundEnabled) return
        try {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }
            track.write(samples, 0, samples.size, AudioTrack.WRITE_NON_BLOCKING)
        } catch (t: Throwable) {
            Log.e("SoundManager", "writeAudio error", t)
        }
    }

    fun release() {
        stopMusic()
        try {
            scope.cancel()
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (t: Throwable) {
            Log.e("SoundManager", "release error", t)
        }
    }
}
