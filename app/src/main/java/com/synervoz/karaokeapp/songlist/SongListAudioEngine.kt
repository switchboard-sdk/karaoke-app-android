package com.synervoz.karaokeapp.songlist

import android.content.Context
import com.synervoz.switchboard.sdk.Codec
import com.synervoz.switchboard.sdk.audioengine.AudioEngine
import com.synervoz.switchboard.sdk.audioengine.PerformanceMode
import com.synervoz.switchboard.sdk.audiograph.AudioGraph
import com.synervoz.switchboard.sdk.audiographnodes.AudioPlayerNode
import com.synervoz.switchboard.sdk.utils.AssetLoader

class SongListAudioEngine(context: Context) {
    val audioEngine = AudioEngine(context = context, performanceMode = PerformanceMode.LOW_LATENCY)
    val audioGraph = AudioGraph()
    val audioPlayerNode = AudioPlayerNode()

    init {
        audioGraph.addNode(audioPlayerNode)
        audioGraph.connect(audioPlayerNode, audioGraph.outputNode)
    }

    fun start() {
        audioEngine.start(audioGraph)
    }

    fun stop() {
        audioEngine.stop()
    }

    fun loadSong(context: Context, songName: String) {
        audioPlayerNode.load(AssetLoader.load(context, songName), Codec.createFromFileName(songName))
    }

    fun close() {
        audioEngine.close()
        audioGraph.close()
        audioPlayerNode.close()
    }
}