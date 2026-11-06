package com.heedar.tv.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.heedar.tv.R
import com.heedar.tv.data.StreamUrlBuilder
import com.heedar.tv.databinding.ActivityPlayerBinding
import com.heedar.tv.domain.Channel
import com.heedar.tv.domain.ServerConfig

/**
 * Hosts the ExoPlayer instance.
 *
 * Lifecycle contract:
 *  - onStart creates the player and restores the last position/play state,
 *  - onStop saves position/play state and releases the player so the audio focus
 *    and the surface are not leaked when the user backgrounds the app.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var streamUrl: String = ""
    private var resumePosition: Long = 0L
    private var resumePlayWhenReady: Boolean = true

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            showError()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        streamUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
        binding.tvChannelName.text =
            intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.player_title_fallback)

        if (streamUrl.isBlank()) {
            showError()
        }
    }

    override fun onStart() {
        super.onStart()
        if (streamUrl.isNotBlank()) {
            initializePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun initializePlayer() {
        if (player != null) return

        val exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.addListener(playerListener)
        binding.playerView.player = exoPlayer
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
        exoPlayer.prepare()
        exoPlayer.seekTo(resumePosition)
        exoPlayer.playWhenReady = resumePlayWhenReady

        player = exoPlayer
        binding.tvPlayerError.visibility = View.GONE
    }

    private fun releasePlayer() {
        val exoPlayer = player ?: return
        resumePosition = exoPlayer.currentPosition
        resumePlayWhenReady = exoPlayer.playWhenReady
        exoPlayer.removeListener(playerListener)
        binding.playerView.player = null
        exoPlayer.release()
        player = null
    }

    private fun showError() {
        binding.tvPlayerError.visibility = View.VISIBLE
    }

    companion object {
        private const val EXTRA_STREAM_URL = "com.heedar.tv.extra.STREAM_URL"
        private const val EXTRA_TITLE = "com.heedar.tv.extra.TITLE"

        fun newIntent(context: Context, streamUrl: String, title: String): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_STREAM_URL, streamUrl)
                .putExtra(EXTRA_TITLE, title)

        fun newIntent(context: Context, config: ServerConfig, channel: Channel): Intent =
            newIntent(
                context = context,
                streamUrl = StreamUrlBuilder.liveStream(config, channel.id),
                title = channel.name
            )
    }
}
