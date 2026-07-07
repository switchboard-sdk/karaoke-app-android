package com.synervoz.karaokeapp.sing

import android.content.Context
import com.synervoz.switchboard.sdk.Switchboard
import java.io.File

/**
 * Vocal-recording engine built on the SwitchboardSDK v3 JSON graph API.
 *
 * The microphone (`inputNode`, enabled via `microphoneEnabled`) is split: one branch is captured by
 * a `Recorder`, the other is downmixed to mono and metered by a `VUMeter`. The backing track plays
 * through a separate `AudioPlayer` to the output; the microphone is never routed to the speaker so
 * the singer only hears the backing track (best used with wired headphones).
 *
 * ```
 * inputNode --> inputSplitter --> recorder (Recorder)
 *                            \--> micToMono (MultiChannelToMono) --> vuMeter (VUMeter)
 * player (AudioPlayer) --> outputNode
 * ```
 *
 * The player and recorder are started together (within one audio callback) from [playAndRecord];
 * any residual input/output latency is measured via the engine's `inputLatency` / `outputLatency`
 * values and reported through [averageRoundTripLatencyMs] so the mixer can compensate.
 */
class SingAudioEngine(private val context: Context) {

    private var engineId: String = ""
    private val roundTripLatencyMs = ArrayDeque<Double>()

    /**
     * Where the recorded take lands. The Recorder's `outputFilePath` cannot be set from Kotlin
     * (the v3 string `setValue` path is not usable), so we rely on its default `@output/recording.wav`
     * and mirror the SDK's resolution here: `@output` maps to `<tempDir>/output`.
     */
    val recordingFilePath: String
        get() = File(File(sdkTempDir(), "output"), "recording.wav").absolutePath

    fun start() {
        val result = Switchboard.createEngine(GRAPH_JSON)
        if (result.isError) {
            throw RuntimeException("Failed to create audio engine: ${result.error}")
        }
        engineId = result.value!!
        Switchboard.callAction(engineId, "start")
    }

    private fun sdkTempDir(): String {
        val result = Switchboard.getValue("switchboard", "tempDirPath")
        val path = if (result.isError) "" else (result.value as? String ?: "")
        return path.ifEmpty { context.cacheDir.absolutePath }
    }

    fun stop() {
        if (engineId.isEmpty()) return
        Switchboard.callAction(engineId, "stop")
        Switchboard.destroyEngine(engineId)
        engineId = ""
    }

    fun loadSong(assetName: String) {
        val path = copyAssetToCache(assetName).absolutePath
        Switchboard.callAction(PLAYER, "load", mapOf("audioFilePath" to path))
    }

    fun playAndRecord() {
        Switchboard.callAction(PLAYER, "play")
        Switchboard.callAction(RECORDER, "start")
    }

    /** Stops playback and recording; the recorder saves the take to [recordingFilePath]. */
    fun finish() {
        Switchboard.callAction(PLAYER, "stop")
        Switchboard.callAction(RECORDER, "stop")
    }

    val isPlaying: Boolean
        get() = readBoolean(PLAYER, "isPlaying")

    fun getDurationInSeconds(): Double = readDouble(PLAYER, "duration")

    fun getPositionInSeconds(): Double = readDouble(PLAYER, "position")

    fun getProgress(): Float {
        val duration = getDurationInSeconds()
        return if (duration > 0.0) (getPositionInSeconds() / duration).toFloat() else 0f
    }

    val vuLevel: Float
        get() = readDouble(VU_METER, "level").toFloat()

    val vuPeak: Float
        get() = readDouble(VU_METER, "peak").toFloat()

    /** Samples the current round-trip latency; keeps a small rolling window (see [averageRoundTripLatencyMs]). */
    fun sampleLatency() {
        if (engineId.isEmpty()) return
        val inputLatency = readDouble(engineId, "inputLatency").let { if (it >= 0) it else 0.0 }
        val outputLatency = readDouble(engineId, "outputLatency").let { if (it >= 0) it else 0.0 }
        // Keep only the most recent few values; latency can change after the engine starts (e.g. a
        // headset is plugged in), so recent samples are more representative than the first ones.
        if (roundTripLatencyMs.size == 3) {
            roundTripLatencyMs.removeFirst()
        }
        roundTripLatencyMs.addLast(inputLatency + outputLatency)
    }

    fun averageRoundTripLatencyMs(): Double =
        if (roundTripLatencyMs.isEmpty()) 0.0 else roundTripLatencyMs.sum() / roundTripLatencyMs.size

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

    companion object {
        private const val PLAYER = "player"
        private const val RECORDER = "recorder"
        private const val VU_METER = "vuMeter"

        private val GRAPH_JSON = """
            {
              "type": "Realtime",
              "config": {
                "microphoneEnabled": true,
                "graph": {
                  "nodes": [
                    { "id": "player", "type": "AudioPlayer" },
                    { "id": "inputSplitter", "type": "BusSplitter" },
                    { "id": "recorder", "type": "Recorder" },
                    { "id": "micToMono", "type": "MultiChannelToMono" },
                    { "id": "vuMeter", "type": "VUMeter", "config": { "smoothingDurationMs": 100.0 } }
                  ],
                  "connections": [
                    { "sourceNode": "inputNode", "destinationNode": "inputSplitter" },
                    { "sourceNode": "inputSplitter", "destinationNode": "recorder" },
                    { "sourceNode": "inputSplitter", "destinationNode": "micToMono" },
                    { "sourceNode": "micToMono", "destinationNode": "vuMeter" },
                    { "sourceNode": "player", "destinationNode": "outputNode" }
                  ]
                }
              }
            }
        """.trimIndent()
    }
}
