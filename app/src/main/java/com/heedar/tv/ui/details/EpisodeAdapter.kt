package com.heedar.tv.ui.details

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.heedar.tv.R
import com.heedar.tv.databinding.ItemEpisodeBinding
import com.heedar.tv.databinding.ItemSeasonHeaderBinding
import com.heedar.tv.domain.Episode
import com.heedar.tv.domain.Season

/** Flattened list row: either a season header or one episode. */
sealed interface EpisodeListRow {
    data class Header(val season: Season) : EpisodeListRow
    data class Item(val episode: Episode) : EpisodeListRow
}

/**
 * Renders every season as a header followed by its episodes, which keeps a whole
 * series in a single scrollable list (good for TV remotes, no nested scrolling).
 */
class EpisodeAdapter(
    private val onClick: (Episode) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = mutableListOf<EpisodeListRow>()

    fun submit(seasons: List<Season>) {
        rows.clear()
        seasons.forEach { season ->
            rows += EpisodeListRow.Header(season)
            season.episodes.forEach { rows += EpisodeListRow.Item(it) }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is EpisodeListRow.Header -> VIEW_TYPE_HEADER
        is EpisodeListRow.Item -> VIEW_TYPE_EPISODE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderHolder(ItemSeasonHeaderBinding.inflate(inflater, parent, false))
        } else {
            EpisodeHolder(ItemEpisodeBinding.inflate(inflater, parent, false), onClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is EpisodeListRow.Header -> (holder as HeaderHolder).bind(row.season)
            is EpisodeListRow.Item -> (holder as EpisodeHolder).bind(row.episode)
        }
    }

    override fun getItemCount(): Int = rows.size

    private class HeaderHolder(
        private val binding: ItemSeasonHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(season: Season) {
            val context = binding.root.context
            val fallback = context.getString(R.string.season_title, season.number)
            binding.tvSeasonTitle.text = season.title?.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    private class EpisodeHolder(
        private val binding: ItemEpisodeBinding,
        private val onClick: (Episode) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(episode: Episode) {
            val context = binding.root.context
            val fallback = context.getString(R.string.episode_title_fallback, episode.number)
            val title = episode.title.takeIf { it.isNotBlank() } ?: fallback
            binding.tvEpisodeTitle.text = "S${episode.season} · E${episode.number} · $title"

            val plot = episode.plot?.takeIf { it.isNotBlank() }
            binding.tvEpisodeSubtitle.text = plot
            binding.tvEpisodeSubtitle.visibility =
                if (plot == null) android.view.View.GONE else android.view.View.VISIBLE

            binding.root.setOnClickListener { onClick(episode) }
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_EPISODE = 1
    }
}
