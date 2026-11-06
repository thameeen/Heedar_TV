package com.heedar.tv.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.heedar.tv.R
import com.heedar.tv.databinding.ItemMediaCardBinding
import com.heedar.tv.domain.MediaKind

/**
 * One adapter for every content grid. Focus handling lives here so D-pad
 * behaviour is identical on the Live, Movies, Series, News, Sports, Search and
 * Favourites screens.
 */
class MediaGridAdapter(
    private val onClick: (MediaCard) -> Unit,
    private val onLongClick: (MediaCard) -> Unit
) : ListAdapter<MediaCard, MediaGridAdapter.CardHolder>(DIFF) {

    private var favoriteKeys: Set<String> = emptySet()

    fun setFavorites(keys: Set<String>) {
        if (favoriteKeys == keys) return
        favoriteKeys = keys
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder =
        CardHolder(
            ItemMediaCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: CardHolder, position: Int) {
        val card = getItem(position)
        holder.bind(card, favoriteKeys.contains(card.key))
    }

    inner class CardHolder(
        private val binding: ItemMediaCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(card: MediaCard, isFavorite: Boolean) {
            binding.tvTitle.text = card.title
            binding.tvSubtitle.text = card.subtitle.orEmpty()
            binding.tvSubtitle.visibility =
                if (card.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.tvFavorite.visibility = if (isFavorite) View.VISIBLE else View.GONE

            // Channel logos are usually wide/transparent; posters are 2:3 art.
            binding.ivPoster.scaleType = if (card.kind == MediaKind.LIVE) {
                ImageView.ScaleType.FIT_CENTER
            } else {
                ImageView.ScaleType.CENTER_CROP
            }
            binding.ivPoster.load(card.imageUrl) {
                placeholder(R.drawable.ic_channel_placeholder)
                error(R.drawable.ic_channel_placeholder)
                crossfade(true)
            }

            binding.root.scaleX = 1f
            binding.root.scaleY = 1f
            binding.root.setOnClickListener { onClick(card) }
            binding.root.setOnLongClickListener {
                onLongClick(card)
                true
            }
            binding.root.setOnFocusChangeListener { view, hasFocus ->
                val scale = if (hasFocus) FOCUSED_SCALE else 1f
                view.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(FOCUS_ANIMATION_MS)
                    .start()
            }
        }
    }

    private companion object {
        const val FOCUSED_SCALE = 1.05f
        const val FOCUS_ANIMATION_MS = 120L

        val DIFF = object : DiffUtil.ItemCallback<MediaCard>() {
            override fun areItemsTheSame(oldItem: MediaCard, newItem: MediaCard): Boolean =
                oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: MediaCard, newItem: MediaCard): Boolean =
                oldItem == newItem
        }
    }
}
