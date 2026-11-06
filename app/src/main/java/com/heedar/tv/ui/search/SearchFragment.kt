package com.heedar.tv.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.GridLayoutManager
import com.heedar.tv.HeedarApp
import com.heedar.tv.R
import com.heedar.tv.data.StreamUrlBuilder
import com.heedar.tv.databinding.FragmentSearchBinding
import com.heedar.tv.domain.MediaKind
import com.heedar.tv.domain.ServerConfig
import com.heedar.tv.domain.XtreamFailure
import com.heedar.tv.ui.common.MediaCard
import com.heedar.tv.ui.common.MediaGridAdapter
import com.heedar.tv.ui.common.gridSpanCount
import com.heedar.tv.ui.details.MovieDetailActivity
import com.heedar.tv.ui.details.SeriesDetailActivity
import com.heedar.tv.ui.player.PlayerActivity
import kotlinx.coroutines.launch

/** Search across live channels, movies and series. */
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding: FragmentSearchBinding get() = requireNotNull(_binding)

    private lateinit var serverConfig: ServerConfig

    private val viewModel: SearchViewModel by viewModels {
        val container = (requireActivity().application as HeedarApp).container
        viewModelFactory {
            initializer { SearchViewModel(container.createRepository(serverConfig)) }
        }
    }

    private val adapter = MediaGridAdapter(
        onClick = { card -> openCard(card) },
        onLongClick = { }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverConfig = requireNotNull(
            (requireActivity().application as HeedarApp).container.playlistStore.load()
        ) { "SearchFragment requires a saved playlist" }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvResults.layoutManager = GridLayoutManager(requireContext(), requireContext().gridSpanCount())
        binding.rvResults.adapter = adapter

        binding.etSearch.doAfterTextChanged { text ->
            viewModel.onQueryChange(text?.toString().orEmpty())
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvResults.adapter = null
        _binding = null
    }

    private fun render(state: SearchViewModel.State) {
        binding.progressSearch.visibility = if (state.loading) View.VISIBLE else View.GONE
        binding.rvResults.visibility = if (state.results.isNotEmpty()) View.VISIBLE else View.GONE

        val messageRes = when {
            state.failure != null -> state.failure.toMessageRes()
            state.promptVisible -> R.string.search_prompt
            state.emptyVisible -> R.string.search_empty
            else -> null
        }
        binding.tvSearchStatus.visibility = if (messageRes == null) View.GONE else View.VISIBLE
        messageRes?.let { binding.tvSearchStatus.setText(it) }

        adapter.submitList(state.results)
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

    private fun XtreamFailure.toMessageRes(): Int = when (this) {
        XtreamFailure.AUTH -> R.string.error_auth
        XtreamFailure.NETWORK -> R.string.error_network
        XtreamFailure.PARSE, XtreamFailure.UNKNOWN -> R.string.error_unknown
    }
}
