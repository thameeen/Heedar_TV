package com.heedar.tv.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.heedar.tv.HeedarApp
import com.heedar.tv.R
import com.heedar.tv.data.FavoritesStore
import com.heedar.tv.data.StreamUrlBuilder
import com.heedar.tv.databinding.FragmentFavoritesBinding
import com.heedar.tv.domain.MediaKind
import com.heedar.tv.domain.ServerConfig
import com.heedar.tv.ui.common.MediaCard
import com.heedar.tv.ui.common.MediaGridAdapter
import com.heedar.tv.ui.common.gridSpanCount
import com.heedar.tv.ui.common.toCard
import com.heedar.tv.ui.details.MovieDetailActivity
import com.heedar.tv.ui.details.SeriesDetailActivity
import com.heedar.tv.ui.player.PlayerActivity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything the user marked as favourite, across live, movies and series.
 * Long-press removes an entry (the only destructive action, and it is reversible
 * by re-adding from any grid).
 */
class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding: FragmentFavoritesBinding get() = requireNotNull(_binding)

    private lateinit var serverConfig: ServerConfig
    private lateinit var favorites: FavoritesStore
    private lateinit var ioDispatcher: CoroutineDispatcher

    private val adapter = MediaGridAdapter(
        onClick = { card -> openCard(card) },
        onLongClick = { card -> removeFavorite(card) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (requireActivity().application as HeedarApp).container
        serverConfig = requireNotNull(container.playlistStore.load()) {
            "FavoritesFragment requires a saved playlist"
        }
        favorites = container.favoritesStore
        ioDispatcher = container.ioDispatcher
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvFavorites.layoutManager =
            GridLayoutManager(requireContext(), requireContext().gridSpanCount())
        binding.rvFavorites.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadFavorites()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvFavorites.adapter = null
        _binding = null
    }

    private fun loadFavorites() {
        viewLifecycleOwner.lifecycleScope.launch {
            val cards = withContext(ioDispatcher) {
                favorites.all().map { it.toCard() }
            }
            if (_binding == null) return@launch
            adapter.submitList(cards)
            adapter.setFavorites(cards.map { it.key }.toSet())
            binding.rvFavorites.visibility =
                if (cards.isEmpty()) View.GONE else View.VISIBLE
            binding.tvFavoritesStatus.visibility =
                if (cards.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun removeFavorite(card: MediaCard) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(ioDispatcher) { favorites.toggle(card.toFavoriteItem()) }
            Toast.makeText(requireContext(), R.string.favorites_removed, Toast.LENGTH_SHORT).show()
            loadFavorites()
        }
    }

    private fun openCard(card: MediaCard) {
        when (card.kind) {
            MediaKind.LIVE -> startActivity(
                PlayerActivity.newIntent(
                    requireContext(),
                    StreamUrlBuilder.liveStream(serverConfig, card.id),
                    card.title
                )
            )

            MediaKind.MOVIE -> startActivity(
                MovieDetailActivity.newIntent(requireContext(), card.id, card.title)
            )

            MediaKind.SERIES -> startActivity(
                SeriesDetailActivity.newIntent(requireContext(), card.id, card.title)
            )

            MediaKind.EPISODE -> startActivity(
                PlayerActivity.newIntent(
                    requireContext(),
                    StreamUrlBuilder.seriesStream(serverConfig, card.id),
                    card.title
                )
            )
        }
    }
}
