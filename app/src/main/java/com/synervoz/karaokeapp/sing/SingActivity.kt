package com.synervoz.karaokeapp.sing

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.synervoz.karaokeapp.data.Song
import com.synervoz.karaokeapp.databinding.ActivitySingBinding
import com.synervoz.karaokeapp.mix.MixerActivity
import com.synervoz.switchboard.sdk.Codec
import com.synervoz.switchboard.sdk.audiograph.AudioGraph
import com.synervoz.switchboard.sdk.audiograph.OfflineGraphRenderer
import com.synervoz.switchboard.sdk.audiographnodes.AudioPlayerNode
import com.synervoz.switchboard.sdk.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedList
import java.util.Timer
import java.util.TimerTask
import kotlin.math.roundToInt


class SingActivity : AppCompatActivity() {
    lateinit var binding: ActivitySingBinding
    lateinit var audioEngine: SingAudioEngine
    private var frameCallback: Choreographer.FrameCallback? = null
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private lateinit var currentSong: Song
    private var mLatencyUpdater: Timer? = null
    private val UPDATE_LATENCY_EVERY_MILLIS: Long = 1000
    private val roundTripLatencyList = LinkedList<Double>()
    private  var offsetMs: Double = 0.0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkMicrophonePermission()) {
            initialize()
        }
    }

    private fun initialize() {
        initAudioEngine()

        binding = ActivitySingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentSong = intent.getSerializableExtra("SONG") as Song
        binding.songTitle.text = currentSong.displayName
        binding.lyrics.text = currentSong.lyrics
        binding.playPauseButton.text = "Start"

        binding.playPauseButton.setOnClickListener {
            if (audioEngine.isPlaying()) {
                stopFrameCallback()
                stopLatencyUpdater()
                calculateOffset()
                audioEngine.finish()
                startMixer()
            } else {
                binding.playPauseButton.text = "Finish"
                audioEngine.playAndRecord()
            }
        }
    }

    private fun startMixer() {

//        val trimmedRecordingPath = trimOffset(audioEngine.recordingFilePath)

        val intent = Intent(this, MixerActivity::class.java)
        intent.putExtra("SONG", currentSong)
        intent.putExtra("RECORDING_PATH", audioEngine.recordingFilePath)
        intent.putExtra("RECORDING_OFFSET", offsetMs)
        startActivity(intent)
        finish()
    }

//    private fun trimOffset(origFilePath: String): String {
//        val offlineGraphRenderer = OfflineGraphRenderer()
//        val player = AudioPlayerNode()
//        val graph = AudioGraph()
//        player.load(origFilePath, Codec.createFromFileName(origFilePath))
//        player.startPosition
//
//        offlineGraphRenderer.close()
//        graph.close()
//        player.close()
//    }

    fun initAudioEngine() {
        audioEngine = SingAudioEngine(this)
        audioEngine.startAudioEngine()
        setupLatencyUpdater()

        uiScope.launch {
            binding.loadingIndicator.visibility = View.VISIBLE
            withContext(Dispatchers.IO) {
                audioEngine.loadSong(applicationContext, currentSong.fileName)
            }
            setupFrameCallback()
            binding.loadingIndicator.visibility = View.GONE
            checkIfWiredHeadsetIsConnected()
        }
    }

    fun checkIfWiredHeadsetIsConnected() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (!audioManager.isWiredHeadsetOn) {
            val dialog: AlertDialog = AlertDialog.Builder(this)
                .setTitle("Wired headset not connected")
                .setMessage("For the best experience please connect a wired headset!")
                .setPositiveButton("Ok") { _, _ ->
                }
                .create()
            dialog.show()
        }
    }


    private fun setupFrameCallback() {
        binding.duration.text = "${(audioEngine.getSongDurationInSeconds() / 60).toInt()}m " +
                "${(audioEngine.getSongDurationInSeconds() % 60).roundToInt()}s"

        binding.progress.max = ((audioEngine.getSongDurationInSeconds() * 10).toInt())

        frameCallback = Choreographer.FrameCallback {
            updateUI()
            Choreographer.getInstance().postFrameCallback(frameCallback!!)
        }
        Choreographer.getInstance().postFrameCallback(frameCallback!!)
    }

    fun stopFrameCallback() {
        if (frameCallback != null) {
            Choreographer.getInstance().removeFrameCallback(frameCallback!!)
            frameCallback = null
        }
    }

    private fun updateUI() {
        binding.progress.progress = (audioEngine.getProgress() * binding.progress.max).toInt()
        binding.progressLabel.text = "${(audioEngine.getPositionInSeconds() / 60).toInt()}m " +
                "${(audioEngine.getPositionInSeconds() % 60).roundToInt()}s"
        binding.rms.progress = (audioEngine.vuMeterNode.level * binding.rms.max).toInt()
        binding.peak.progress = (audioEngine.vuMeterNode.peak * binding.peak.max).toInt()
    }

    private fun setupLatencyUpdater() {
        // Update the latency every 1s
        val latencyUpdateTask: TimerTask = object : TimerTask() {
            override fun run() {
                if (audioEngine.isLatencyDetectionSupported()) {
                    val outputLatency: Double = audioEngine.getCurrentOutputLatencyMs().let { if (it >= 0) it else 0.0 }
                    val inputLatency: Double = audioEngine.getCurrentInputLatencyMs().let { if (it >= 0) it else 0.0 }

                    addRoundTripLatencyValue(inputLatency + outputLatency)
                } else {
                    Logger.debug("Only supported in AAudio (API 26+)")
                }
            }
        }
        mLatencyUpdater = Timer()
        mLatencyUpdater!!.schedule(latencyUpdateTask, 0, UPDATE_LATENCY_EVERY_MILLIS)
    }

    fun stopLatencyUpdater() {
        mLatencyUpdater?.cancel()
    }

    fun addRoundTripLatencyValue(latency: Double) {
        // for simplicity we only keep the last X values. We keep the last few instead the first,
        // since the latency might have changed after the audio engine have started because of a root
        // change (plugged in headset, etc. )
        latency.toString()
        if (roundTripLatencyList.size == 3) {
            roundTripLatencyList.removeFirst()
        }
        roundTripLatencyList.addLast(latency)
    }

    fun calculateAverageRoundTripLatency(): Double {
        val avg = if (roundTripLatencyList.isEmpty()) {
            0.0
        } else {
            roundTripLatencyList.sum().toDouble() / roundTripLatencyList.size
        }

        return avg
    }

    fun checkMicrophonePermission(): Boolean {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted, ask the user.
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
            return false
        }
        return true
    }

    private fun calculateOffset() {
        val avgRoundTripLatency = calculateAverageRoundTripLatency()
        val bufferLatency = audioEngine.getInputBufferSizeMs() + audioEngine.getOutputBufferSizeMs()
        offsetMs = avgRoundTripLatency + bufferLatency
        Logger.info("Buffer latency: $bufferLatency")
        Logger.info("Average round trip latency: $avgRoundTripLatency")
        Logger.info("Total recording offset ms: $offsetMs")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            0 -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    initialize()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFrameCallback()
        audioEngine.stopAudioEngine()
        audioEngine.close()
        mLatencyUpdater?.cancel()

    }
}