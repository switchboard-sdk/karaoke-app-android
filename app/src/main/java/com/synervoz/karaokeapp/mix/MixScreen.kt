package com.synervoz.karaokeapp.mix

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.synervoz.karaokeapp.data.Song
import com.synervoz.karaokeapp.ui.SwitchboardLogo
import com.synervoz.karaokeapp.ui.formatTime
import com.synervoz.switchboard.sdk.utils.FileExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mixer screen — balance the vocals against the backing track, toggle vocal effects, scrub through
 * the mix, and export the rendered result. Owns a [MixerAudioEngine] for the whole time it is on
 * screen.
 */
@Composable
fun MixScreen(
    song: Song,
    recordingPath: String,
    recordingOffsetMs: Double,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { MixerAudioEngine(context.applicationContext) }

    var loading by remember { mutableStateOf(true) }
    var exporting by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationSec by remember { mutableStateOf(0.0) }
    var positionSec by remember { mutableStateOf(0.0) }
    var progress by remember { mutableStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0f) }

    var musicVolume by remember { mutableStateOf(100f) }
    var voiceVolume by remember { mutableStateOf(100f) }
    var reverb by remember { mutableStateOf(false) }
    var compressor by remember { mutableStateOf(false) }
    var avpc by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        engine.start()
        onDispose { engine.stop() }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            engine.loadSong(song.fileName)
            engine.loadRecording(recordingPath)
            engine.setRecordingOffset(recordingOffsetMs)
        }
        durationSec = engine.getDurationInSeconds()
        engine.setMusicVolume(musicVolume.toInt())
        engine.setVoiceVolume(voiceVolume.toInt())
        loading = false
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!isSeeking) {
                positionSec = engine.getPositionInSeconds()
                progress = engine.getProgress()
            }
            withFrameMillis { it }
        }
    }

    fun togglePlay() {
        if (isPlaying) engine.pause() else engine.play()
        isPlaying = !isPlaying
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SwitchboardLogo()
            Text(
                text = song.displayName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { togglePlay() }, enabled = !loading) {
                    Icon(
                        painter = painterResource(
                            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                    )
                }
                Slider(
                    value = if (isSeeking) seekValue else progress,
                    onValueChange = { isSeeking = true; seekValue = it },
                    onValueChangeFinished = {
                        val target = seekValue.toDouble() * durationSec
                        engine.setPositionInSeconds(target)
                        positionSec = target
                        progress = seekValue
                        isSeeking = false
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "${formatTime(positionSec)} / ${formatTime(durationSec)}",
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.End),
            )

            Text(text = "Mixer", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            Text(text = "Voice volume", fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
            Slider(
                value = voiceVolume,
                onValueChange = { voiceVolume = it; engine.setVoiceVolume(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(text = "Music volume", fontSize = 17.sp)
            Slider(
                value = musicVolume,
                onValueChange = { musicVolume = it; engine.setMusicVolume(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(text = "Effects", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            EffectRow("Reverb", reverb) { reverb = it; engine.enableReverb(it) }
            EffectRow("Compressor", compressor) { compressor = it; engine.enableCompressor(it) }
            EffectRow("Automatic Vocal Pitch Correction", avpc) { avpc = it; engine.enableAutomaticVocalPitchCorrection(it) }

            Button(
                onClick = {
                    scope.launch {
                        exporting = true
                        if (isPlaying) {
                            engine.pause()
                            isPlaying = false
                        }
                        val path = withContext(Dispatchers.IO) { engine.renderMix() }
                        engine.setPositionInSeconds(0.0)
                        positionSec = 0.0
                        progress = 0f
                        (context as? Activity)?.let { FileExporter.export(it, path) }
                        exporting = false
                    }
                },
                enabled = !loading && !exporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            ) {
                Text("Export")
            }
        }

        if (loading || exporting) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(50.dp))
        }
    }
}

@Composable
private fun EffectRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
