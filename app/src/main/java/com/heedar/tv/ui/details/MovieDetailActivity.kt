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
import coil.load
import com.heedar.tv.HeedarApp
import com.heedar.tv.R
import com.heedar.tv.data.FavoritesStore
import com.heedar.tv.data.StreamUrlBuilder
import com.heedar.tv.databinding.ActivityMovieDetailBinding
import com.heedar.tv.domain.FavoriteItem
import com.heedar.tv.domain.MediaKind
import com.heedar.tv.domain.MovieDetail
import com.heedar.tv.domain.ServerConfig
import com.heedar.tv.domain.XtreamFailure
import com.heedar.tv.ui.player.PlayerActivity
import kotlinx.coroutines.launch

/** Full movie metadata plus the two real actions: play and favourite. */
class MovieDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMovieDetailBinding
    private lateinit var serverConfig: ServerConfig
    private lateinit var favorites: FavoritesStore

    private var detail: MovieDetail? = null

    private val movieId: Int get() = intent.getIntExtra(EXTRA_ID, 0)

    private val viewModel: MovieDetailViewModel by viewModels {
        val container = (application as HeedarApp).container
        viewModelFactory {
            initializer {
                MovieDetailViewModel(container.createRepository(serverConfig), movieId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as HeedarApp).container
        val config = container.playlistStore.load()
        if (config == null || movieId <= 0) {
            finish()
            return
        }
        serverConfig = config
        favorites = container.favoritesStore

        binding = ActivityMovieDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        binding.btnRetry.setOnClickListener { viewModel.load() }
        binding.btnPlay.setOnClickListener { play() }
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

    private fun render(state: MovieDetailViewModel.State) {
        binding.progressDetail.visibility = if (state.loading) View.VISIBLE else View.GONE

        val failure = state.failure
        binding.groupError.visibility = if (failure == null) View.GONE else View.VISIBLE
        failure?.let { binding.tvError.setText(it.toMessageRes()) }

        val movie = state.detail
        detail = movie
        if (movie == null) {
            binding.btnPlay.isEnabled = false
            binding.btnFavorite.isEnabled = false
            return
        }
        binding.btnPlay.isEnabled = true
        binding.btnFavorite.isEnabled = true

        binding.tvTitle.text = movie.name
        binding.ivPoster.load(movie.posterUrl)
        binding.tvMeta.text = meta(movie)
        binding.tvPlot.text = movie.plot.orEmpty()
        binding.tvCast.text = movie.cast.orEmpty()
        binding.tvDirector.text = movie.director.orEmpty()
        binding.tvPlotLabel.visibility = visibleIfNotBlank(movie.plot)
        binding.tvCastLabel.visibility = visibleIfNotBlank(movie.cast)
        binding.tvDirectorLabel.visibility = visibleIfNotBlank(movie.director)

        refreshFavoriteLabel()
    }

    private fun meta(movie: MovieDetail): String = buildList {
        movie.genre?.takeIf { it.isNotBlank() }
            ?.let { add("${getString(R.string.details_genre)}: $it") }
        movie.rating?.takeIf { it.isNotBlank() && it != "0" }
            ?.let { add("${getString(R.string.details_rating)}: $it") }
        movie.duration?.takeIf { it.isNotBlank() }
            ?.let { add("${getString(R.string.details_duration)}: $it") }
        movie.releaseDate?.takeIf { it.isNotBlank() }
            ?.let { add("${getString(R.string.details_release)}: $it") }
    }.joinToString("  •  ")

    private fun play() {
        val movie = detail ?: return
        startActivity(
            PlayerActivity.newIntent(
                context = this,
                streamUrl = StreamUrlBuilder.vodStream(
                    config = serverConfig,
                    streamId = movie.id,
                    extension = movie.containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"
                ),
                title = movie.name
            )
        )
    }

    private fun toggleFavorite() {
        val movie = detail ?: return
        val nowFavorite = favorites.toggle(
            FavoriteItem(
                kind = MediaKind.MOVIE,
                id = movie.id,
                title = movie.name,
                imageUrl = movie.posterUrl
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
        val movie = detail ?: return
        val isFavorite = favorites.contains(MediaKind.MOVIE, movie.id)
        binding.btnFavorite.setText(
            if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite
        )
    }

    private fun visibleIfNotBlank(value: String?): Int =
        if (value.isNullOrBlank()) View.GONE else View.VISIBLE

    private fun XtreamFailure.toMessageRes(): Int = when (this) {
        XtreamFailure.AUTH -> R.string.error_auth
        XtreamFailure.NETWORK -> R.string.error_network
        XtreamFailure.PARSE, XtreamFailure.UNKNOWN -> R.string.error_unknown
    }

    companion object {
        private const val EXTRA_ID = "com.heedar.tv.extra.MOVIE_ID"
        private const val EXTRA_TITLE = "com.heedar.tv.extra.TITLE"

        fun newIntent(context: Context, movieId: Int, title: String): Intent =
            Intent(context, MovieDetailActivity::class.java)
                .putExtra(EXTRA_ID, movieId)
                .putExtra(EXTRA_TITLE, title)
    }
}
