package com.synervoz.karaokeapp.mix

import android.content.Context
import com.synervoz.switchboard.sdk.Codec
import com.synervoz.switchboard.sdk.SwitchboardSDK
import com.synervoz.switchboard.sdk.audioengine.AudioEngine
import com.synervoz.switchboard.sdk.audioengine.PerformanceMode
import com.synervoz.switchboard.sdk.audiograph.AudioGraph
import com.synervoz.switchboard.sdk.audiograph.OfflineGraphRenderer
import com.synervoz.switchboard.sdk.audiographnodes.AudioPlayerNode
import com.synervoz.switchboard.sdk.audiographnodes.GainNode
import com.synervoz.switchboard.sdk.audiographnodes.MixerNode
import com.synervoz.switchboard.sdk.utils.AssetLoader
import com.synervoz.switchboardsuperpowered.audiographnodes.AutomaticVocalPitchCorrectionNode
import com.synervoz.switchboardsuperpowered.audiographnodes.CompressorNode
import com.synervoz.switchboardsuperpowered.audiographnodes.ReverbNode
import kotlin.math.max

class MixerAudioEngine(context: Context) {

    val audioEngine = AudioEngine(context = context, performanceMode = PerformanceMode.LOW_LATENCY)

    val audioGraphToRender = AudioGraph()

    val musicPlayer = AudioPlayerNode()
    val voicePlayer = AudioPlayerNode()
    val mixerNode = MixerNode()
    val musicGainNode = GainNode()
    val voiceGainNode = GainNode()
    val reverbNode = ReverbNode()
    val compressorNode = CompressorNode()
    val avpcNode = AutomaticVocalPitchCorrectionNode()
    val fileFormat = Codec.WAV

    private var mixedFilePath = SwitchboardSDK.getTemporaryDirectoryPath() + "mix." + fileFormat.fileExtension

    init {
        audioGraphToRender.addNode(musicPlayer)
        audioGraphToRender.addNode(voicePlayer)
        audioGraphToRender.addNode(mixerNode)
        audioGraphToRender.addNode(musicGainNode)
        audioGraphToRender.addNode(voiceGainNode)
        audioGraphToRender.addNode(reverbNode)
        audioGraphToRender.addNode(compressorNode)
        audioGraphToRender.addNode(avpcNode)
        audioGraphToRender.connect(musicPlayer, musicGainNode)
        audioGraphToRender.connect(musicGainNode, mixerNode)
        audioGraphToRender.connect(voicePlayer, voiceGainNode)
        audioGraphToRender.connect(voiceGainNode, avpcNode)
        audioGraphToRender.connect(avpcNode, compressorNode)
        audioGraphToRender.connect(compressorNode, reverbNode)
        audioGraphToRender.connect(reverbNode, mixerNode)
        audioGraphToRender.connect(mixerNode, audioGraphToRender.outputNode)
        audioEngine.start(audioGraphToRender)
        audioGraphToRender.stop()
    }

    fun isPlaying() = musicPlayer.isPlaying

    fun renderMix(): String {
        val sampleRate =
            max(musicPlayer.getSourceSampleRate(), voicePlayer.getSourceSampleRate())
        musicPlayer.stop()
        voicePlayer.stop()
        musicPlayer.play()
        voicePlayer.play()
        val offlineGraphRenderer = OfflineGraphRenderer()
        offlineGraphRenderer.setSampleRate(sampleRate)
        offlineGraphRenderer.setMaxNumberOfSecondsToRender(musicPlayer.getDuration())
        offlineGraphRenderer.processGraph(audioGraphToRender, mixedFilePath, fileFormat)
        offlineGraphRenderer.close()

        return mixedFilePath
    }

    fun play() {
        musicPlayer.play()
        voicePlayer.play()
        audioGraphToRender.start()
    }

    fun pause() {
        audioGraphToRender.stop()
        musicPlayer.pause()
        voicePlayer.pause()
    }

    fun stopAudioEngine() {
        audioEngine.stop()
    }

    fun startAudioEngine() {
        audioEngine.start(audioGraphToRender)
    }

    fun close() {
        audioGraphToRender.close()
        musicPlayer.close()
        voicePlayer.close()
        mixerNode.close()
        audioEngine.close()
    }

    fun loadSong(context: Context, songName: String) {
        musicPlayer.load(AssetLoader.load(context, songName), Codec.createFromFileName(songName))
    }

    fun loadRecording(recordingPath: String) {
        voicePlayer.load(recordingPath, Codec.createFromFileName(recordingPath))
    }

    fun getSongDurationInSeconds() : Double {
        return musicPlayer.getDuration()
    }

    fun getPositionInSeconds() : Double {
        return musicPlayer.position
    }

    fun setPositionInSeconds(position: Double) {
        musicPlayer.position = position
        if (voicePlayer.getDuration() > position) {
            voicePlayer.position = position
        }
    }

    fun getProgress(): Float {
        return (musicPlayer.position / musicPlayer.getDuration()).toFloat()
    }

    fun setMusicVolume(volume: Int) {
        musicGainNode.gain = volume / 100.0f
    }

    fun setVoiceVolume(volume: Int) {
        voiceGainNode.gain = volume / 100.0f
    }

    fun enableReverb(enable: Boolean) {
        reverbNode.isEnabled = enable
    }

    fun enableCompressor(enable: Boolean) {
        compressorNode.isEnabled = enable
    }

    fun enableAutomaticVocalPitchCorrection(enable: Boolean) {
        avpcNode.isEnabled = enable
    }
}