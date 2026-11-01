package com.heedar.tv.player

import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.heedar.tv.R

class PlayerActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var tvChannelName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        tvChannelName = findViewById(R.id.tvChannelName)

        val streamUrl = intent.getStringExtra("STREAM_URL") ?: ""
        val channelName = intent.getStringExtra("CHANNEL_NAME") ?: "بث مباشر"

        tvChannelName.text = channelName

        if (streamUrl.isNotEmpty()) {
            initializePlayer(streamUrl)
        }
    }

    private fun initializePlayer(url: String) {
        exoPlayer = ExoPlayer.Builder(this).build().also { player ->
            playerView.player = player
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun releasePlayer() {
        exoPlayer?.let {
            it.release()
            exoPlayer = null
        }
    }
}
