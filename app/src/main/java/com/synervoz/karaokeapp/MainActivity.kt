package com.synervoz.karaokeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.synervoz.karaokeapp.data.Song
import com.synervoz.karaokeapp.mix.MixScreen
import com.synervoz.karaokeapp.sing.SingScreen
import com.synervoz.karaokeapp.songlist.SongListScreen
import com.synervoz.karaokeapp.ui.theme.KaraokeAppTheme
import com.synervoz.switchboard.sdk.Switchboard
import com.synervoz.switchboardsuperpowered.SuperpoweredExtension

private enum class Screen { SONG_LIST, SING, MIX }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load Superpowered before initialize() so its license (from the extensions config) is
        // applied and its nodes can be instantiated.
        SuperpoweredExtension.load()
        Switchboard.initialize(
            this,
            "Your client ID",
            "Your client secret",
            mapOf("Superpowered" to mapOf("superpoweredLicenseKey" to "ExampleLicenseKey-WillExpire-OnNextUpdate")),
        )

        setContent {
            KaraokeAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KaraokeApp()
                }
            }
        }
    }
}

@Composable
private fun KaraokeApp() {
    var screen by remember { mutableStateOf(Screen.SONG_LIST) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var recordingPath by remember { mutableStateOf("") }
    var recordingOffsetMs by remember { mutableStateOf(0.0) }

    when (screen) {
        Screen.SONG_LIST -> SongListScreen(
            onSing = { song ->
                selectedSong = song
                screen = Screen.SING
            },
        )

        // A fresh screen instance is created per navigation so its audio engine is torn down
        // (via DisposableEffect) when navigating away.
        Screen.SING -> SingScreen(
            song = selectedSong!!,
            onFinish = { path, offsetMs ->
                recordingPath = path
                recordingOffsetMs = offsetMs
                screen = Screen.MIX
            },
            onBack = { screen = Screen.SONG_LIST },
        )

        Screen.MIX -> MixScreen(
            song = selectedSong!!,
            recordingPath = recordingPath,
            recordingOffsetMs = recordingOffsetMs,
            onBack = { screen = Screen.SONG_LIST },
        )
    }
}
