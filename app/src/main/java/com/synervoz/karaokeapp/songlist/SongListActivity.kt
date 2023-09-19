package com.synervoz.karaokeapp.songlist

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.synervoz.karaokeapp.data.Song
import com.synervoz.karaokeapp.data.SongListData
import com.synervoz.karaokeapp.databinding.SongListBinding
import com.synervoz.karaokeapp.sing.SingActivity
import com.synervoz.switchboard.sdk.SwitchboardSDK
import com.synervoz.switchboardsuperpowered.SuperpoweredExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SongListActivity : AppCompatActivity() {
    private lateinit var binding: SongListBinding
    lateinit var engine: SongListAudioEngine
    private var currentSong: SongItem? = null
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val songList: List<Song>
        get() = SongListData.getList()

    private lateinit var adapter: SongAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SongListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SwitchboardSDK.initialize("Your client ID", "Your client secret")
        SuperpoweredExtension.initialize("ExampleLicenseKey-WillExpire-OnNextUpdate")

        adapter = SongAdapter(
            songList.map { SongItem(it, false) }, { song: SongItem -> playPauseSong(song) },
        ) { song: Song -> sing(song) }
        binding.recyclerView.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        currentSong = null
        initAudioEngine()
    }

    private fun initAudioEngine() {
        engine = SongListAudioEngine(this)
        engine.start()
    }

    private fun playPauseSong(song: SongItem) {
        uiScope.launch {
            if (song.song != currentSong?.song) {
                currentSong = song
                binding.loadingIndicator.visibility = View.VISIBLE
                withContext(Dispatchers.IO) {
                    engine.loadSong(applicationContext, song.song.fileName)
                }
                binding.loadingIndicator.visibility = View.GONE
            }
            if (engine.audioPlayerNode.isPlaying) {
                engine.audioPlayerNode.pause()
            } else {
                engine.audioPlayerNode.play()
            }
            val newSongList = songList.map {
                SongItem(
                    it,
                    it == currentSong?.song && engine.audioPlayerNode.isPlaying
                )
            }
            adapter.songList = newSongList
        }
    }

    private fun sing(song: Song) {
        stopAudioEngine()
        val intent = Intent(this, SingActivity::class.java)
        intent.putExtra("SONG", song)
        startActivity(intent)
    }

    fun stopAudioEngine() {
        engine.stop()
        engine.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioEngine()
    }
}