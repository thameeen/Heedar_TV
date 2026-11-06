package com.heedar.tv.ui.details

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.heedar.tv.HeedarApp
import com.heedar.tv.R
import com.heedar.tv.data.FavoritesStore
import com.heedar.tv.data.StreamUrlBuilder
import com.heedar.tv.databinding.ActivitySeriesDetailBinding
import com.heedar.tv.domain.Episode
import com.heedar.tv.domain.FavoriteItem
import com.heedar.tv.domain.MediaKind
import com.heedar.tv.domain.SeriesDetail
import com.heedar.tv.domain.ServerConfig
import com.heedar.tv.domain.XtreamFailure
import com.heedar.tv.ui.player.PlayerActivity
import kotlinx.coroutines.launch

/** Series metadata plus every season/episode; tapping an episode plays it. */
class SeriesDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySeriesDetailBinding
    private lateinit var serverConfig: ServerConfig
    private lateinit var favorites: FavoritesStore

    private var detail: SeriesDetail? = null

    private val seriesId: Int get() = intent.getIntExtra(EXTRA_ID, 0)

    private val episodeAdapter = EpisodeAdapter { episode -> play(episode) }

    private val viewModel: SeriesDetailViewModel by viewModels {
        val container = (application as HeedarApp).container
        viewModelFactory {
            initializer {
                SeriesDetailViewModel(container.createRepository(serverConfig), seriesId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as HeedarApp).container
        val config = container.playlistStore.load()
        if (config == null || seriesId <= 0) {
            finish()
            return
        }
        serverConfig = config
        favorites = container.favoritesStore

        binding = ActivitySeriesDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        binding.rvEpisodes.layoutManager = LinearLayoutManager(this)
        binding.rvEpisodes.adapter = episodeAdapter

        binding.btnRetry.setOnClickListener { viewModel.load() }
        binding.btnFavorite.setOnClickListener { toggleFavorite() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        binding.rvEpisodes.adapter = null
        super.onDestroy()
    }

    private fun render(state: SeriesDetailViewModel.State) {
        binding.progressDetail.visibility = if (state.loading) View.VISIBLE else View.GONE

        val failure = state.failure
        binding.groupError.visibility = if (failure == null) View.GONE else View.VISIBLE
        failure?.let { binding.tvError.setText(it.toMessageRes()) }

        val series = state.detail
        detail = series
        binding.btnFavorite.isEnabled = series != null
        if (series == null) {
            episodeAdapter.submit(emptyList())
            return
        }

        binding.tvTitle.text = series.name
        binding.ivCover.load(series.coverUrl)
        binding.tvMeta.text = meta(series)
        binding.tvPlot.text = series.plot.orEmpty()
        binding.tvPlot.visibility =
            if (series.plot.isNullOrBlank()) View.GONE else View.VISIBLE

        episodeAdapter.submit(series.seasons)
        refreshFavoriteLabel()
    }

    private fun meta(series: SeriesDetail): String = buildList {
        series.genre?.takeIf { it.isNotBlank() }
            ?.let { add("${getString(R.string.details_genre)}: $it") }
        series.rating?.takeIf { it.isNotBlank() && it != "0" }
            ?.let { add("${getString(R.string.details_rating)}: $it") }
        series.releaseDate?.takeIf { it.isNotBlank() }
            ?.let { add("${getString(R.string.details_release)}: $it") }
        add("${getString(R.string.episodes_title)}: ${series.episodeCount}")
    }.joinToString("  •  ")

    private fun play(episode: Episode) {
        val series = detail ?: return
        startActivity(
            PlayerActivity.newIntent(
                context = this,
                streamUrl = StreamUrlBuilder.seriesStream(
                    config = serverConfig,
                    episodeId = episode.id,
                    extension = episode.containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"
                ),
                title = "$seriesTitle · S${episode.season}E${episode.number}"
            )
        )
    }

    private val seriesTitle: String
        get() = detail?.name ?: title?.toString().orEmpty()

    private fun toggleFavorite() {
        val series = detail ?: return
        val nowFavorite = favorites.toggle(
            FavoriteItem(
                kind = MediaKind.SERIES,
                id = series.id,
                title = series.name,
                imageUrl = series.coverUrl
            )
        )
        Toast.makeText(
            this,
            if (nowFavorite) R.string.favorites_added else R.string.favorites_removed,
            Toast.LENGTH_SHORT
        ).show()
        refreshFavoriteLabel()
    }

    private fun refreshFavoriteLabel() {
        val series = detail ?: return
        val isFavorite = favorites.contains(MediaKind.SERIES, series.id)
        binding.btnFavorite.setText(
            if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite
        )
    }

    private fun XtreamFailure.toMessageRes(): Int = when (this) {
        XtreamFailure.AUTH -> R.string.error_auth
        XtreamFailure.NETWORK -> R.string.error_network
        XtreamFailure.PARSE, XtreamFailure.UNKNOWN -> R.string.error_unknown
    }

    companion object {
        private const val EXTRA_ID = "com.heedar.tv.extra.SERIES_ID"
        private const val EXTRA_TITLE = "com.heedar.tv.extra.TITLE"

        fun newIntent(context: Context, seriesId: Int, title: String): Intent =
            Intent(context, SeriesDetailActivity::class.java)
                .putExtra(EXTRA_ID, seriesId)
                .putExtra(EXTRA_TITLE, title)
    }
}
