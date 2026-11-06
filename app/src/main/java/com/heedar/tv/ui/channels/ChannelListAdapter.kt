package com.heedar.tv.ui.channels

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.heedar.tv.R
import com.heedar.tv.databinding.ItemChannelBinding
import com.heedar.tv.domain.Channel

/**
 * ListAdapter gives us DiffUtil-based updates, so a refresh no longer rebuilds and
 * flickers the whole list (the previous notifyDataSetChanged approach).
 */
class ChannelListAdapter(
    private val onChannelClick: (Channel) -> Unit
) : ListAdapter<Channel, ChannelListAdapter.ChannelViewHolder>(ChannelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChannelViewHolder(
        private val binding: ItemChannelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(channel: Channel) {
            binding.tvChannelName.text = channel.name
            binding.ivChannelLogo.load(channel.logoUrl) {
                placeholder(R.drawable.ic_channel_placeholder)
                error(R.drawable.ic_channel_placeholder)
                crossfade(true)
            }
            binding.root.setOnClickListener { onChannelClick(channel) }
        }
    }
}
