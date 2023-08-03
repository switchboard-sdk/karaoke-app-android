package com.synervoz.karaokeapp.sing

import android.content.Context
import com.synervoz.switchboard.sdk.Codec
import com.synervoz.switchboard.sdk.SwitchboardSDK
import com.synervoz.switchboard.sdk.audioengine.AudioEngine
import com.synervoz.switchboard.sdk.audiograph.AudioGraph
import com.synervoz.switchboard.sdk.audiographnodes.AudioPlayerNode
import com.synervoz.switchboard.sdk.audiographnodes.BusSplitterNode
import com.synervoz.switchboard.sdk.audiographnodes.MultiChannelToMonoNode
import com.synervoz.switchboard.sdk.audiographnodes.RecorderNode
import com.synervoz.switchboard.sdk.audiographnodes.SubgraphProcessorNode
import com.synervoz.switchboard.sdk.audiographnodes.VUMeterNode
import com.synervoz.switchboard.sdk.utils.AssetLoader

class SingAudioEngine {
    val audioEngine = AudioEngine(enableInput = true)
    val audioGraph = AudioGraph()
    val internalAudioGraph = AudioGraph()
    val subgraphNode = SubgraphProcessorNode()
    val audioPlayerNode = AudioPlayerNode()
    val recorderNode = RecorderNode()
    val splitterNode = BusSplitterNode()
    val multiChannelToMonoNode = MultiChannelToMonoNode()
    val vuMeterNode = VUMeterNode()

    init {
        vuMeterNode.smoothingDurationMs = 100.0f
        internalAudioGraph.addNode(audioPlayerNode)
        internalAudioGraph.addNode(recorderNode)
        internalAudioGraph.addNode(splitterNode)
        internalAudioGraph.addNode(multiChannelToMonoNode)
        internalAudioGraph.addNode(vuMeterNode)
        internalAudioGraph.connect(internalAudioGraph.inputNode, splitterNode)
        internalAudioGraph.connect(splitterNode, recorderNode)
        internalAudioGraph.connect(splitterNode, multiChannelToMonoNode)
        internalAudioGraph.connect(multiChannelToMonoNode, vuMeterNode)
        internalAudioGraph.connect(audioPlayerNode, internalAudioGraph.outputNode)
        subgraphNode.setAudioGraph(internalAudioGraph)

        audioGraph.addNode(subgraphNode)
        audioGraph.connect(audioGraph.inputNode, subgraphNode)
        audioGraph.connect(subgraphNode, audioGraph.outputNode)
    }

    var recordingFilePath = SwitchboardSDK.getTemporaryDirectoryPath() + "recording.wav"

    fun startAudioEngine() {
        internalAudioGraph.start()
        audioEngine.start(audioGraph)
    }

    fun stopAudioEngine() {
        audioEngine.stop()
    }

    fun playAndRecord() {
        audioPlayerNode.play()
        recorderNode.start()
    }

    fun loadSong(context: Context, songName: String) {
        audioPlayerNode.load(AssetLoader.load(context, songName), Codec.createFromFileName(songName))
    }

    fun getSongDurationInSeconds() : Double {
        return audioPlayerNode.getDuration()
    }

    fun getPositionInSeconds() : Double {
        return audioPlayerNode.position
    }

    fun getProgress(): Float {
        return (audioPlayerNode.position / audioPlayerNode.getDuration()).toFloat()
    }

    fun isPlaying() = audioPlayerNode.isPlaying


    fun finish() {
        audioEngine.stop()
        internalAudioGraph.stop()
        audioPlayerNode.stop()
        recorderNode.stop(recordingFilePath, Codec.WAV)
    }

    fun close() {
        audioEngine.close()
        audioGraph.close()
        audioPlayerNode.close()
        recorderNode.close()
        splitterNode.close()
        multiChannelToMonoNode.close()
        vuMeterNode.close()
    }
}