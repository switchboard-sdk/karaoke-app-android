package com.synervoz.karaokeapp.sing

import android.content.Context
import com.synervoz.switchboard.sdk.Codec
import com.synervoz.switchboard.sdk.SwitchboardSDK
import com.synervoz.switchboard.sdk.audioengine.AudioEngine
import com.synervoz.switchboard.sdk.audioengine.MicInputPreset
import com.synervoz.switchboard.sdk.audioengine.PerformanceMode
import com.synervoz.switchboard.sdk.audiograph.AudioGraph
import com.synervoz.switchboard.sdk.audiographnodes.AudioPlayerNode
import com.synervoz.switchboard.sdk.audiographnodes.BusSplitterNode
import com.synervoz.switchboard.sdk.audiographnodes.MultiChannelToMonoNode
import com.synervoz.switchboard.sdk.audiographnodes.RecorderNode
import com.synervoz.switchboard.sdk.audiographnodes.SubgraphProcessorNode
import com.synervoz.switchboard.sdk.audiographnodes.VUMeterNode
import com.synervoz.switchboard.sdk.utils.AssetLoader

class SingAudioEngine(context: Context) {
    val audioEngine = AudioEngine(context = context, microphoneEnabled = true,
        // Use these presets in order to achieve the lowest latency
        performanceMode = PerformanceMode.LOW_LATENCY,
        micInputPreset = MicInputPreset.VOICE_RECOGNITION)
    val audioGraph = AudioGraph()
    val internalAudioGraph = AudioGraph()
    val subgraphNode = SubgraphProcessorNode()
    val audioPlayerNode = AudioPlayerNode()
    val recorderNode = RecorderNode()
    val splitterNode = BusSplitterNode()
    val multiChannelToMonoNode = MultiChannelToMonoNode()
    val vuMeterNode = VUMeterNode()
    val fileFormat = Codec.WAV

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

    var recordingFilePath = SwitchboardSDK.getTemporaryDirectoryPath() + "recording." + fileFormat.fileExtension

    fun startAudioEngine() {
        audioEngine.start(audioGraph)
    }

    fun stopAudioEngine() {
        audioEngine.stop()
    }

    fun playAndRecord() {
        audioPlayerNode.play()
        recorderNode.start()
        internalAudioGraph.start()
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

    fun isLatencyDetectionSupported(): Boolean {
        return audioEngine.isLatencyDetectionSupported()
    }

    fun getCurrentOutputLatencyMs(): Double {
        return audioEngine.getCurrentOutputLatencyMs()
    }

    fun getCurrentInputLatencyMs(): Double {
        return audioEngine.getCurrentInputLatencyMs()
    }

    fun getInputBufferSizeMs() = audioEngine.getInputBufferSizeMs()

    fun getOutputBufferSizeMs() = audioEngine.getOutputBufferSizeMs()


}