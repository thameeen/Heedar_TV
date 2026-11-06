package com.heedar.tv.ui.channels

import androidx.recyclerview.widget.DiffUtil
import com.heedar.tv.domain.Channel

/**
 * Comparison rules kept in a pure object so they can be unit tested on the JVM
 * without instantiating AndroidX classes.
 */
object ChannelDiffLogic {

    fun sameItem(old: Channel, new: Channel): Boolean = old.id == new.id

    fun sameContent(old: Channel, new: Channel): Boolean = old == new
}

class ChannelDiffCallback : DiffUtil.ItemCallback<Channel>() {

    override fun areItemsTheSame(oldItem: Channel, newItem: Channel): Boolean =
        ChannelDiffLogic.sameItem(oldItem, newItem)

    override fun areContentsTheSame(oldItem: Channel, newItem: Channel): Boolean =
        ChannelDiffLogic.sameContent(oldItem, newItem)
}
