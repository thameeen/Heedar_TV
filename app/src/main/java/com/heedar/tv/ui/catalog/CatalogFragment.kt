package com.heedar.tv.ui.catalog

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.heedar.tv.HeedarApp
import com.heedar.tv.R
import com.heedar.tv.data.StreamUrlBuilder
import com.heedar.tv.databinding.FragmentCatalogBinding
import com.heedar.tv.domain.MediaKind
import com.heedar.tv.domain.ServerConfig
import com.heedar.tv.domain.XtreamFailure
import com.heedar.tv.ui.ContentSection
import com.heedar.tv.ui.common.CategoryAdapter
import com.heedar.tv.ui.common.CategoryChip
import com.heedar.tv.ui.common.MediaCard
import com.heedar.tv.ui.common.MediaGridAdapter
import com.heedar.tv.ui.common.SectionFilters
import com.heedar.tv.ui.details.MovieDetailActivity
import com.heedar.tv.ui.details.SeriesDetailActivity
import com.heedar.tv.ui.player.PlayerActivity
import kotlinx.coroutines.launch

/**
 * Renders Live, Movies, Series, News and Sports. News and Sports are the Live
 * catalog narrowed to matching server categories.
 */
class CatalogFragment : Fragment() {

    private var _binding: FragmentCatalogBinding? = null
    private val binding: FragmentCatalogBinding get() = requireNotNull(_binding)

    private lateinit var serverConfig: ServerConfig

    private val section: ContentSection by lazy {
        ContentSection.fromName(requireArguments().getString(ARG_SECTION)) ?: ContentSection.LIVE
    }

    private val catalogType: CatalogType
        get() = when (section) {
            ContentSection.MOVIES -> CatalogType.MOVIES
            ContentSection.SERIES -> CatalogType.SERIES
            else -> CatalogType.LIVE
        }

    private val keywords: List<String>
        get() = when (section) {
            ContentSection.NEWS -> SectionFilters.NEWS_KEYWORDS
            ContentSection.SPORTS -> SectionFilters.SPORTS_KEYWORDS
            else -> emptyList()
        }

    private val viewModel: CatalogViewModel by viewModels {
        val container = (requireActivity().application as HeedarApp).container
        viewModelFactory {
            initializer {
                CatalogViewModel(
                    repository = container.createRepository(serverConfig),
                    favorites = container.favoritesStore,
                    type = catalogType,
                    categoryKeywords = keywords,
                    ioDispatcher = container.ioDispatcher
                )
            }
        }
    }

    private val mediaAdapter = MediaGridAdapter(
        onClick = { card -> openCard(card) },
        onLongClick = { card -> toggleFavorite(card) }
    )

    private val categoryAdapter = CategoryAdapter { categoryId -> viewModel.selectCategory(categoryId) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverConfig = requireNotNull(
            (requireActivity().application as HeedarApp).container.playlistStore.load()
        ) { "CatalogFragment requires a saved playlist" }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCatalogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvCategories.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvCategories.adapter = categoryAdapter

        binding.rvMedia.layoutManager = GridLayoutManager(requireContext(), gridSpanCount())
        binding.rvMedia.adapter = mediaAdapter
        binding.btnRetry.setOnClickListener { viewModel.load() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Favourites can be changed on another screen; keep the markers honest.
        viewModel.refreshFavorites()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvMedia.adapter = null
        binding.rvCategories.adapter = null
        _binding = null
    }

    private fun render(state: CatalogViewModel.State) {
        binding.progressCatalog.visibility = if (state.loading) View.VISIBLE else View.GONE

        val showContent = !state.loading && state.failure == null && state.cards.isNotEmpty()
        binding.rvMedia.visibility = if (showContent) View.VISIBLE else View.GONE

        val statusVisible = !state.loading && !showContent
        binding.groupStatus.visibility = if (statusVisible) View.VISIBLE else View.GONE

        if (statusVisible) {
            val messageRes = when {
                state.failure != null -> state.failure.toMessageRes()
                else -> emptyMessageRes()
            }
            binding.tvStatus.setText(messageRes)
            binding.btnRetry.visibility =
                if (state.failure != null) View.VISIBLE else View.GONE
        }

        val chips = buildList {
            add(CategoryChip(id = null, name = getString(R.string.category_all)))
            state.categories.forEach { add(CategoryChip(id = it.id, name = it.name)) }
        }
        categoryAdapter.submitList(chips)
        categoryAdapter.setSelected(state.selectedCategoryId)
        binding.rvCategories.visibility = if (state.categories.isEmpty()) View.GONE else View.VISIBLE

        mediaAdapter.submitList(state.cards)
        mediaAdapter.setFavorites(state.favoriteKeys)

        if (showContent && !hasTouchscreen()) {
            binding.rvMedia.post {
                binding.rvMedia.layoutManager?.findViewByPosition(0)?.requestFocus()
            }
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

            MediaKind.EPISODE -> Unit
        }
    }

    private fun toggleFavorite(card: MediaCard) {
        val nowFavorite = viewModel.toggleFavorite(card)
        Toast.makeText(
            requireContext(),
            if (nowFavorite) R.string.favorites_added else R.string.favorites_removed,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun hasTouchscreen(): Boolean =
        requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

    private fun gridSpanCount(): Int {
        val resources = resources
        val cardWithGap = resources.getDimensionPixelSize(R.dimen.card_width) +
            resources.getDimensionPixelSize(R.dimen.card_grid_spacing)
        val available = resources.displayMetrics.widthPixels -
            resources.getDimensionPixelSize(R.dimen.screen_padding) * 2
        return (available / cardWithGap).coerceIn(MIN_SPAN, MAX_SPAN)
    }

    private fun emptyMessageRes(): Int = when (section) {
        ContentSection.MOVIES -> R.string.movies_empty
        ContentSection.SERIES -> R.string.series_empty
        ContentSection.NEWS -> R.string.news_empty
        ContentSection.SPORTS -> R.string.sports_empty
        else -> R.string.channels_empty
    }

    private fun XtreamFailure?.toMessageRes(): Int = when (this) {
        XtreamFailure.AUTH -> R.string.error_auth
        XtreamFailure.NETWORK -> R.string.error_network
        XtreamFailure.PARSE, XtreamFailure.UNKNOWN, null -> R.string.error_unknown
    }

    companion object {
        private const val ARG_SECTION = "com.heedar.tv.arg.SECTION"
        private const val MIN_SPAN = 2
        private const val MAX_SPAN = 6

        fun newInstance(section: ContentSection): CatalogFragment = CatalogFragment().apply {
            arguments = Bundle().apply { putString(ARG_SECTION, section.name) }
        }
    }
}
