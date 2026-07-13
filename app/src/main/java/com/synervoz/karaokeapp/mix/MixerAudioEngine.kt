package com.synervoz.karaokeapp.mix

import android.content.Context
import com.synervoz.switchboard.sdk.Switchboard
import java.io.File

/**
 * Mixing / rendering engine built on the SwitchboardSDK v3 JSON graph API.
 *
 * Realtime preview graph — the backing track and the recorded vocals each get their own gain, the
 * vocals are additionally run through pitch-correction, compression and reverb, and the two are
 * summed by a `Mixer`:
 *
 * ```
 * musicPlayer --> musicGain -------------------------------> mixer --> outputNode
 * voicePlayer --> voiceGain --> avpc --> compressor --> reverb --> mixer
 * ```
 *
 * Effects start disabled and are toggled from the UI. Exporting renders the same graph offline (to a
 * file) via a separate `Offline` engine — see [renderMix].
 */
class MixerAudioEngine(private val context: Context) {

    private var engineId: String = ""

    private var musicPath: String = ""
    private var voicePath: String = ""

    // Current mix parameters, mirrored so the offline render reproduces the live preview.
    private var musicGain: Float = 1f
    private var voiceGain: Float = 1f
    private var voiceOffsetSeconds: Double = 0.0
    private var reverbEnabled: Boolean = false
    private var compressorEnabled: Boolean = false
    private var avpcEnabled: Boolean = false

    private val mixFilePath: String = File(context.cacheDir, "mix.wav").absolutePath

    fun start() {
        val result = Switchboard.createEngine(realtimeGraphJson())
        if (result.isError) {
            throw RuntimeException("Failed to create audio engine: ${result.error}")
        }
        engineId = result.value!!
        Switchboard.callAction(engineId, "start")
    }

    fun stop() {
        if (engineId.isEmpty()) return
        Switchboard.callAction(engineId, "stop")
        Switchboard.destroyEngine(engineId)
        engineId = ""
    }

    fun loadSong(assetName: String) {
        musicPath = copyAssetToCache(assetName).absolutePath
        Switchboard.callAction(MUSIC_PLAYER, "load", mapOf("audioFilePath" to musicPath))
    }

    fun loadRecording(recordingPath: String) {
        voicePath = recordingPath
        Switchboard.callAction(VOICE_PLAYER, "load", mapOf("audioFilePath" to recordingPath))
    }

    /**
     * Compensates for the record/playback latency measured on the sing screen by advancing the
     * vocal track so it lines up with the backing track. `position` is used because the v3 JSON API
     * does not expose the player's start position.
     */
    fun setRecordingOffset(offsetMs: Double) {
        voiceOffsetSeconds = offsetMs / 1000.0
        Switchboard.setValue(VOICE_PLAYER, "position", voiceOffsetSeconds)
    }

    val isPlaying: Boolean
        get() = readBoolean(MUSIC_PLAYER, "isPlaying")

    fun play() {
        Switchboard.callAction(MUSIC_PLAYER, "play")
        Switchboard.callAction(VOICE_PLAYER, "play")
    }

    fun pause() {
        Switchboard.callAction(MUSIC_PLAYER, "pause")
        Switchboard.callAction(VOICE_PLAYER, "pause")
    }

    fun getDurationInSeconds(): Double = readDouble(MUSIC_PLAYER, "duration")

    fun getPositionInSeconds(): Double = readDouble(MUSIC_PLAYER, "position")

    fun getProgress(): Float {
        val duration = getDurationInSeconds()
        return if (duration > 0.0) (getPositionInSeconds() / duration).toFloat() else 0f
    }

    fun setPositionInSeconds(position: Double) {
        Switchboard.setValue(MUSIC_PLAYER, "position", position)
        if (readDouble(VOICE_PLAYER, "duration") > position) {
            Switchboard.setValue(VOICE_PLAYER, "position", position)
        }
    }

    /** volume is 0..100 (from the seek bars), matching the original UI. */
    fun setMusicVolume(volume: Int) {
        musicGain = volume / 100.0f
        Switchboard.setValue(MUSIC_GAIN, "gain", musicGain)
    }

    fun setVoiceVolume(volume: Int) {
        voiceGain = volume / 100.0f
        Switchboard.setValue(VOICE_GAIN, "gain", voiceGain)
    }

    fun enableReverb(enable: Boolean) {
        reverbEnabled = enable
        Switchboard.setValue(REVERB, "enabled", enable)
    }

    fun enableCompressor(enable: Boolean) {
        compressorEnabled = enable
        Switchboard.setValue(COMPRESSOR, "enabled", enable)
    }

    fun enableAutomaticVocalPitchCorrection(enable: Boolean) {
        avpcEnabled = enable
        Switchboard.setValue(AVPC, "enabled", enable)
    }

    /**
     * Renders the current mix (with all current volumes / effects / offset) to a WAV file offline
     * and returns its path. Runs synchronously; call it off the main thread.
     */
    fun renderMix(): String {
        val durationSeconds = getDurationInSeconds()

        val result = Switchboard.createEngine(offlineGraphJson(durationSeconds))
        if (result.isError) {
            throw RuntimeException("Failed to create offline renderer: ${result.error}")
        }
        val offlineId = result.value!!

        Switchboard.callAction(OFFLINE_MUSIC_PLAYER, "load", mapOf("audioFilePath" to musicPath))
        Switchboard.callAction(OFFLINE_VOICE_PLAYER, "load", mapOf("audioFilePath" to voicePath))
        // Gain is not settable from graph config, so apply the current volumes here; the effect
        // enabled flags are baked into the graph JSON (see offlineGraphJson).
        Switchboard.setValue(OFFLINE_MUSIC_GAIN, "gain", musicGain)
        Switchboard.setValue(OFFLINE_VOICE_GAIN, "gain", voiceGain)
        Switchboard.setValue(OFFLINE_VOICE_PLAYER, "position", voiceOffsetSeconds)
        Switchboard.callAction(OFFLINE_MUSIC_PLAYER, "play")
        Switchboard.callAction(OFFLINE_VOICE_PLAYER, "play")

        Switchboard.callAction(offlineId, "process")
        Switchboard.destroyEngine(offlineId)

        return mixFilePath
    }

    private fun readBoolean(objectId: String, key: String): Boolean {
        val result = Switchboard.getValue(objectId, key)
        return if (result.isError) false else (result.value as? Boolean ?: false)
    }

    private fun readDouble(objectId: String, key: String): Double {
        val result = Switchboard.getValue(objectId, key)
        return if (result.isError) 0.0 else (result.value as? Number)?.toDouble() ?: 0.0
    }

    private fun copyAssetToCache(assetName: String): File {
        val outFile = File(context.cacheDir, assetName)
        if (!outFile.exists()) {
            context.assets.open(assetName).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return outFile
    }

    /** Builds the mixing graph shared by the realtime and offline engines (node ids are parameterised). */
    private fun graphNodesAndConnections(
        music: String,
        voice: String,
        musicGainId: String,
        voiceGainId: String,
        avpcId: String,
        compressorId: String,
        reverbId: String,
        mixerId: String,
    ): String = """
        "nodes": [
          { "id": "$music", "type": "AudioPlayer" },
          { "id": "$voice", "type": "AudioPlayer" },
          { "id": "$musicGainId", "type": "Gain" },
          { "id": "$voiceGainId", "type": "Gain" },
          { "id": "$avpcId", "type": "Superpowered.AutomaticVocalPitchCorrection", "config": { "enabled": $avpcEnabled } },
          { "id": "$compressorId", "type": "Superpowered.Compressor", "config": { "enabled": $compressorEnabled } },
          { "id": "$reverbId", "type": "Superpowered.Reverb", "config": { "enabled": $reverbEnabled } },
          { "id": "$mixerId", "type": "Mixer" }
        ],
        "connections": [
          { "sourceNode": "$music", "destinationNode": "$musicGainId" },
          { "sourceNode": "$musicGainId", "destinationNode": "$mixerId" },
          { "sourceNode": "$voice", "destinationNode": "$voiceGainId" },
          { "sourceNode": "$voiceGainId", "destinationNode": "$avpcId" },
          { "sourceNode": "$avpcId", "destinationNode": "$compressorId" },
          { "sourceNode": "$compressorId", "destinationNode": "$reverbId" },
          { "sourceNode": "$reverbId", "destinationNode": "$mixerId" },
          { "sourceNode": "$mixerId", "destinationNode": "outputNode" }
        ]
    """.trimIndent()

    private fun realtimeGraphJson(): String = """
        {
          "type": "Realtime",
          "config": {
            "graph": {
              ${graphNodesAndConnections(MUSIC_PLAYER, VOICE_PLAYER, MUSIC_GAIN, VOICE_GAIN, AVPC, COMPRESSOR, REVERB, MIXER)}
            }
          }
        }
    """.trimIndent()

    private fun offlineGraphJson(durationSeconds: Double): String = """
        {
          "type": "Offline",
          "config": {
            "sampleRate": $RENDER_SAMPLE_RATE,
            "maxNumberOfSecondsToRender": $durationSeconds,
            "outputFiles": [
              { "filePath": "$mixFilePath", "numberOfChannels": 2 }
            ],
            "graph": {
              ${graphNodesAndConnections(OFFLINE_MUSIC_PLAYER, OFFLINE_VOICE_PLAYER, OFFLINE_MUSIC_GAIN, OFFLINE_VOICE_GAIN, OFFLINE_AVPC, OFFLINE_COMPRESSOR, OFFLINE_REVERB, OFFLINE_MIXER)}
            }
          }
        }
    """.trimIndent()

    companion object {
        private const val RENDER_SAMPLE_RATE = 48000

        private const val MUSIC_PLAYER = "musicPlayer"
        private const val VOICE_PLAYER = "voicePlayer"
        private const val MUSIC_GAIN = "musicGain"
        private const val VOICE_GAIN = "voiceGain"
        private const val AVPC = "avpc"
        private const val COMPRESSOR = "compressor"
        private const val REVERB = "reverb"
        private const val MIXER = "mixer"

        private const val OFFLINE_MUSIC_PLAYER = "offlineMusicPlayer"
        private const val OFFLINE_VOICE_PLAYER = "offlineVoicePlayer"
        private const val OFFLINE_MUSIC_GAIN = "offlineMusicGain"
        private const val OFFLINE_VOICE_GAIN = "offlineVoiceGain"
        private const val OFFLINE_AVPC = "offlineAvpc"
        private const val OFFLINE_COMPRESSOR = "offlineCompressor"
        private const val OFFLINE_REVERB = "offlineReverb"
        private const val OFFLINE_MIXER = "offlineMixer"
    }
}
