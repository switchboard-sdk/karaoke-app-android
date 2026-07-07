package com.synervoz.karaokeapp.songlist

import android.content.Context
import com.synervoz.switchboard.sdk.Switchboard
import java.io.File

/**
 * Song-list preview engine built on the SwitchboardSDK v3 JSON graph API.
 *
 * The graph is a single `AudioPlayer` routed straight to the output node:
 *
 * ```
 * player (AudioPlayer) -> outputNode
 * ```
 */
class SongListAudioEngine(private val context: Context) {

    private var engineId: String = ""

    fun start() {
        val result = Switchboard.createEngine(GRAPH_JSON)
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

    val isPlaying: Boolean
        get() {
            val result = Switchboard.getValue(PLAYER, "isPlaying")
            return if (result.isError) false else (result.value as? Boolean ?: false)
        }

    fun loadSong(assetName: String) {
        val path = copyAssetToCache(assetName).absolutePath
        Switchboard.callAction(PLAYER, "load", mapOf("audioFilePath" to path))
    }

    fun play() {
        Switchboard.callAction(PLAYER, "play")
    }

    fun pause() {
        Switchboard.callAction(PLAYER, "pause")
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

        private val GRAPH_JSON = """
            {
              "type": "Realtime",
              "config": {
                "graph": {
                  "nodes": [
                    { "id": "player", "type": "AudioPlayer" }
                  ],
                  "connections": [
                    { "sourceNode": "player", "destinationNode": "outputNode" }
                  ]
                }
              }
            }
        """.trimIndent()
    }
}
