package com.synervoz.karaokeapp.mix

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.synervoz.karaokeapp.data.Song
import com.synervoz.karaokeapp.databinding.ActivityMixBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class MixerActivity : AppCompatActivity() {
    lateinit var song: Song
    lateinit var recordingPath: String
    lateinit var binding: ActivityMixBinding
    val mixerAudioEngine = MixerAudioEngine()
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private var frameCallback: Choreographer.FrameCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMixBinding.inflate(layoutInflater)
        setContentView(binding.root)

        song = intent.getSerializableExtra("SONG") as Song
        recordingPath = intent.getStringExtra("RECORDING_PATH")!!


        uiScope.launch {
            binding.loadingIndicator.visibility = View.VISIBLE
            withContext(Dispatchers.IO) {
                mixerAudioEngine.loadSong(this@MixerActivity, song.fileName)
                mixerAudioEngine.loadRecording(recordingPath)
            }
            binding.songTitle.text = song.displayName
            binding.duration.text =
                "${(mixerAudioEngine.getSongDurationInSeconds() / 60).toInt()}m " +
                        "${(mixerAudioEngine.getSongDurationInSeconds() % 60).roundToInt()}s"
            setupProgressBar()

            binding.loadingIndicator.visibility = View.GONE
        }

        binding.playPause.setOnClickListener {
            if (mixerAudioEngine.isPlaying()) {
                pause()
            } else {
                play()
            }
            binding.playPause.setImageDrawable(
                ContextCompat.getDrawable(
                    it.context,
                    if (mixerAudioEngine.isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
            )
        }


        setupExport()
        setupVolumeMixer()
        setupEffects()
    }

    fun play() {
        mixerAudioEngine.play()
        updateProgressBar()
        binding.playPause.setImageDrawable(
            ContextCompat.getDrawable(
                baseContext,
                android.R.drawable.ic_media_pause
            )
        )
    }

    fun pause() {
        stopProgressBarUpdate()
        mixerAudioEngine.pause()
        binding.playPause.setImageDrawable(
            ContextCompat.getDrawable(
                baseContext,
                android.R.drawable.ic_media_play
            )
        )
    }

    private fun setupEffects() {
        binding.reverb.setOnCheckedChangeListener { compoundButton, isEnabled ->
            mixerAudioEngine.enableReverb(isEnabled)
        }

        binding.compressor.setOnCheckedChangeListener { compoundButton, isEnabled ->
            mixerAudioEngine.enableCompressor(isEnabled)
        }

        binding.avpc.setOnCheckedChangeListener { compoundButton, isEnabled ->
            mixerAudioEngine.enableAutomaticVocalPitchCorrection(isEnabled)
        }
    }

    private fun setupVolumeMixer() {
        binding.musicVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                mixerAudioEngine.setMusicVolume(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.voiceVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                mixerAudioEngine.setVoiceVolume(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        mixerAudioEngine.setMusicVolume(binding.musicVolume.progress)
        mixerAudioEngine.setVoiceVolume(binding.voiceVolume.progress)
    }

    private fun setupExport() {
        binding.export.setOnClickListener {
            uiScope.launch {
                binding.loadingIndicator.visibility = View.VISIBLE
                pause()
                withContext(Dispatchers.IO) {
                    mixerAudioEngine.stopAudioEngine()
                    val filePath = mixerAudioEngine.renderMix()
                    export(this@MixerActivity, filePath)
                    mixerAudioEngine.voicePlayer.position = 0.0
                    mixerAudioEngine.musicPlayer.position = 0.0
                    pause()
                    mixerAudioEngine.startAudioEngine()
                }
                binding.loadingIndicator.visibility = View.GONE
            }
        }
    }

    private fun export(activityContext: Activity, filePath: String) {
        val file = File(filePath)
        val uri: Uri = FileProvider.getUriForFile(
            activityContext,
            activityContext.packageName + ".provider",
            file
        )

        val intent: Intent = ShareCompat.IntentBuilder.from(activityContext)
            .setType("*/*")
            .setStream(uri)
            .createChooserIntent()
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        activityContext.startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        mixerAudioEngine.stopAudioEngine()
        mixerAudioEngine.close()
        stopProgressBarUpdate()
    }

    fun setupProgressBar() {
        binding.progressSeekbar.max =
            ((mixerAudioEngine.getSongDurationInSeconds() * 10).toInt())
        var wasPlaying = false
        binding.progressSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                wasPlaying = mixerAudioEngine.isPlaying()
                if (wasPlaying) {
                    pause()
                }
            }

            override fun onStopTrackingTouch(seekbar: SeekBar?) {
                mixerAudioEngine.setPositionInSeconds((seekbar!!.progress / 10.0))
                if (wasPlaying) {
                    play()
                }
            }
        })
    }
    fun updateProgressBar() {
        frameCallback = Choreographer.FrameCallback {
            binding.progressSeekbar.progress =
                (mixerAudioEngine.getProgress() * binding.progressSeekbar.max).toInt()
            binding.progressLabel.text =
                "${(mixerAudioEngine.getPositionInSeconds() / 60).toInt()}m " +
                        "${(mixerAudioEngine.getPositionInSeconds() % 60).roundToInt()}s"

            Choreographer.getInstance().postFrameCallback(frameCallback!!)
        }
        Choreographer.getInstance().postFrameCallback(frameCallback!!)

    }

    fun stopProgressBarUpdate() {
        if (frameCallback != null) {
            Choreographer.getInstance().removeFrameCallback(frameCallback!!)
            frameCallback = null
        }
    }
}