package com.synervoz.karaokeapp.sing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.synervoz.karaokeapp.data.Song
import com.synervoz.karaokeapp.ui.SwitchboardLogo
import com.synervoz.karaokeapp.ui.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Sing screen — plays the backing track while recording the microphone, showing playback progress
 * and a live input-level meter. On finish it saves the take and forwards it (plus the measured
 * latency offset) to the mixer. Owns a [SingAudioEngine] for the whole time it is on screen.
 */
@Composable
fun SingScreen(song: Song, onFinish: (recordingPath: String, offsetMs: Double) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    MicrophonePermissionGate(onDenied = onBack) {
        SingContent(song, onFinish)
    }
}

@Composable
private fun SingContent(song: Song, onFinish: (String, Double) -> Unit) {
    val context = LocalContext.current
    val engine = remember { SingAudioEngine(context.applicationContext) }

    var loading by remember { mutableStateOf(true) }
    var isRecording by remember { mutableStateOf(false) }
    var durationSec by remember { mutableStateOf(0.0) }
    var positionSec by remember { mutableStateOf(0.0) }
    var progress by remember { mutableStateOf(0f) }
    var level by remember { mutableStateOf(0f) }
    var peak by remember { mutableStateOf(0f) }
    var showHeadsetDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        engine.start()
        onDispose { engine.stop() }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { engine.loadSong(song.fileName) }
        durationSec = engine.getDurationInSeconds()
        loading = false
        showHeadsetDialog = !isWiredHeadsetConnected(context)
    }

    // Live playback progress + input-level meter, paced to the display refresh rate.
    LaunchedEffect(Unit) {
        while (true) {
            positionSec = engine.getPositionInSeconds()
            progress = engine.getProgress()
            level = engine.vuLevel
            peak = engine.vuPeak
            withFrameMillis { it }
        }
    }

    // Periodically sample the round-trip latency so we can compensate for it in the mix.
    LaunchedEffect(Unit) {
        while (true) {
            engine.sampleLatency()
            delay(1000)
        }
    }

    if (showHeadsetDialog) {
        AlertDialog(
            onDismissRequest = { showHeadsetDialog = false },
            confirmButton = { TextButton(onClick = { showHeadsetDialog = false }) { Text("Ok") } },
            title = { Text("Wired headset not connected") },
            text = { Text("For the best experience please connect a wired headset!") },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            SwitchboardLogo()
            Text(
                text = song.displayName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp),
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
            Text(
                text = "${formatTime(positionSec)} / ${formatTime(durationSec)}",
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.End),
            )

            Text(text = "Input level", fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
            Text(text = "Input peak", fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            LinearProgressIndicator(progress = { peak }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp))

            Button(
                onClick = {
                    if (isRecording) {
                        isRecording = false
                        val offsetMs = engine.averageRoundTripLatencyMs()
                        engine.finish()
                        onFinish(engine.recordingFilePath, offsetMs)
                    } else {
                        isRecording = true
                        engine.playAndRecord()
                    }
                },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            ) {
                Text(if (isRecording) "Finish" else "Start")
            }

            Text(
                text = song.lyrics,
                fontSize = 17.sp,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(50.dp))
        }
    }
}

@Composable
private fun MicrophonePermissionGate(onDenied: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var requested by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
        requested = true
        if (!isGranted) onDenied()
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    if (granted) {
        content()
    }
}

private fun isWiredHeadsetConnected(context: Context): Boolean {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    return devices.any {
        it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
    }
}
