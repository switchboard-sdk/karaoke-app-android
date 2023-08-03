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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt


class SingActivity : AppCompatActivity() {
    lateinit var binding: ActivitySingBinding
    lateinit var audioEngine: SingAudioEngine
    private var frameCallback: Choreographer.FrameCallback? = null
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private lateinit var currentSong: Song

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
                audioEngine.finish()
                val intent = Intent(this, MixerActivity::class.java)
                intent.putExtra("SONG", currentSong)
                intent.putExtra("RECORDING_PATH", audioEngine.recordingFilePath)
                startActivity(intent)
                finish()
            } else {
                binding.playPauseButton.text = "Finish"
                audioEngine.playAndRecord()            }
        }
    }

    fun initAudioEngine() {
        audioEngine = SingAudioEngine()
        audioEngine.startAudioEngine()

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
    }
}