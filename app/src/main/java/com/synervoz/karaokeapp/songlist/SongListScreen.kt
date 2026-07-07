package com.synervoz.karaokeapp.songlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.synervoz.karaokeapp.data.Song
import com.synervoz.karaokeapp.data.SongListData
import com.synervoz.karaokeapp.ui.SwitchboardLogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Song list — lets the user preview each backing track and pick one to sing over.
 * Owns a [SongListAudioEngine] for the whole time it is on screen.
 */
@Composable
fun SongListScreen(onSing: (Song) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val songs = remember { SongListData.getList() }
    val engine = remember { SongListAudioEngine(context.applicationContext) }

    var loadedSong by remember { mutableStateOf<Song?>(null) }
    var playingSong by remember { mutableStateOf<Song?>(null) }
    var loading by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        engine.start()
        onDispose { engine.stop() }
    }

    fun onPlayPause(song: Song) {
        scope.launch {
            if (song != loadedSong) {
                loadedSong = song
                loading = true
                withContext(Dispatchers.IO) { engine.loadSong(song.fileName) }
                loading = false
                engine.play()
                playingSong = song
            } else if (engine.isPlaying) {
                engine.pause()
                playingSong = null
            } else {
                engine.play()
                playingSong = song
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 20.dp),
        ) {
            SwitchboardLogo()
            LazyColumn(modifier = Modifier.padding(top = 10.dp)) {
                items(songs) { song ->
                    SongRow(
                        song = song,
                        isPlaying = playingSong == song,
                        onPlayPause = { onPlayPause(song) },
                        onSelect = { onSing(song) },
                    )
                    HorizontalDivider()
                }
            }
        }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(50.dp))
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = song.displayName, fontSize = 15.sp)
            Text(text = song.duration, fontSize = 13.sp)
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                painter = painterResource(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                ),
                contentDescription = if (isPlaying) "Pause" else "Play",
            )
        }
        Button(onClick = onSelect, modifier = Modifier.padding(start = 16.dp)) {
            Text("Select")
        }
    }
}
