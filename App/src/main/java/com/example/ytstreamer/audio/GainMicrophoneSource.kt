package com.example.ytstreamer.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.library.util.sources.audio.AudioSource

/**
 * Custom microphone source with:
 *  - Manual software gain (volume boost, 0x - 4x) — jaisa Viacast/Wirecast me "audio gain" hota hai
 *  - Hardware Noise Suppressor toggle
 *  - Hardware Acoustic Echo Canceler toggle
 *
 * IMPORTANT (please read before building):
 * RootEncoder ke alag-alag versions me `com.pedro.library.util.sources.audio.AudioSource`
 * abstract class ke method names thode change hue hain (create/init, start, stop, release).
 * Agar Android Studio me compile error aaye "method does not override anything", to:
 *   1. `AudioSource` class name par Ctrl+Click (ya Cmd+Click) karke uski actual definition kholein.
 *   2. Wahan diye gaye abstract method names is file ke override methods se match kar lein
 *      (logic same rahega, sirf method signature theek karna hoga).
 * Yeh normal hai kyunki yeh library active development me hai.
 */
class GainMicrophoneSource(
    private val audioSource: Int = MediaRecorder.AudioSource.CAMCORDER
) : AudioSource() {

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var recordingThread: Thread? = null
    private var running = false
    private var bufferSize = 0

    @Volatile private var gain: Float = 1.0f // 1.0 = normal volume
    @Volatile private var noiseSuppressionWanted = true
    @Volatile private var echoCancelWanted = false

    /** UI se gain set karne ke liye. Range: 0.0 (mute) se 4.0 (4x loud) tak. */
    fun setGain(value: Float) {
        gain = value.coerceIn(0f, 4f)
    }

    fun getGain(): Float = gain

    fun setNoiseSuppression(enabled: Boolean) {
        noiseSuppressionWanted = enabled
        noiseSuppressor?.enabled = enabled
    }

    fun setEchoCancel(enabled: Boolean) {
        echoCancelWanted = enabled
        echoCanceler?.enabled = enabled
    }

    override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
        noiseSuppressionWanted = noiseSuppressor
        echoCancelWanted = echoCanceler
        val channelConfig = if (isStereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        bufferSize = minBuffer * 2

        return try {
            audioRecord = AudioRecord(
                audioSource, sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT, bufferSize
            )
            val sessionId = audioRecord?.audioSessionId ?: 0
            if (noiseSuppressionWanted && NoiseSuppressor.isAvailable()) {
                this.noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
            if (echoCancelWanted && AcousticEchoCanceler.isAvailable()) {
                this.echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
            audioRecord?.state == AudioRecord.STATE_INITIALIZED
        } catch (e: Exception) {
            false
        }
    }

    override fun start(getMicrophoneData: GetMicrophoneData) {
        setGetMicrophoneData(getMicrophoneData)
        audioRecord?.startRecording()
        running = true
        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (running) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    applyGain(buffer, read, gain)
                    getMicrophoneData.onDataCaptured(buffer.copyOf(read))
                }
            }
        }.also { it.start() }
    }

    override fun stop() {
        running = false
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        recordingThread?.join(200)
        recordingThread = null
    }

    override fun release() {
        noiseSuppressor?.release()
        echoCanceler?.release()
        audioRecord?.release()
        audioRecord = null
    }

    override fun isRunning(): Boolean = running

    /** 16-bit PCM samples ko gain factor se multiply karke clip-safe boost/attenuate karta hai. */
    private fun applyGain(buffer: ByteArray, length: Int, gain: Float) {
        if (gain == 1.0f) return
        var i = 0
        while (i < length - 1) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = (high shl 8) or low
            var amplified = (sample * gain).toInt()
            if (amplified > Short.MAX_VALUE) amplified = Short.MAX_VALUE.toInt()
            if (amplified < Short.MIN_VALUE) amplified = Short.MIN_VALUE.toInt()
            buffer[i] = (amplified and 0xFF).toByte()
            buffer[i + 1] = ((amplified shr 8) and 0xFF).toByte()
            i += 2
        }
    }
}
