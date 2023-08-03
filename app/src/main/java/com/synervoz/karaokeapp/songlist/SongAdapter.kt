package com.synervoz.karaokeapp.songlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.synervoz.karaokeapp.R
import com.synervoz.karaokeapp.data.Song
import kotlin.math.roundToInt

data class SongItem(
    val song: Song,
    val isPlaying: Boolean
)

class SongAdapter(
    songList: List<SongItem>,
    private val playPauseSongCallback: (SongItem) -> Unit,
    private val singCallback: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    var songList: List<SongItem> = songList
        set(value) {
            val prev = field
            field = value
            // This doesn't account for dataset size change, but it won't change in this example
            prev.forEachIndexed { index, songItem ->
                if (songItem != value[index]) {
                    notifyItemChanged(index)
                }
            }
        }

    class SongViewHolder(
        itemView: View,
        playPauseSongCallback: (SongItem) -> Unit,
        singCallback: (Song) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val songTextView: TextView = itemView.findViewById(R.id.song_text)
        private val songLengthTextView: TextView = itemView.findViewById(R.id.song_length)
        private val songPlayPauseButton: ImageButton = itemView.findViewById(R.id.play_pause_button)
        private val singOverSongButton: TextView = itemView.findViewById(R.id.sing_button)
        private var currentSong: SongItem? = null

        init {
            songPlayPauseButton.setOnClickListener {
                playPauseSongCallback(currentSong!!)
            }

            singOverSongButton.setOnClickListener {
                singCallback(currentSong!!.song)
            }
        }

        fun bind(song: SongItem) {
            currentSong = song
            songTextView.text = song.song.displayName
            songLengthTextView.text = song.song.duration
            songPlayPauseButton.setImageDrawable(
                ContextCompat.getDrawable(
                    itemView.context,
                    if (song.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.song_item, parent, false)

        return SongViewHolder(view, playPauseSongCallback, singCallback)
    }

    override fun getItemCount(): Int {
        return songList.size
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(songList[position])
    }
}