package com.heedar.tv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.heedar.tv.R
import com.heedar.tv.data.LiveChannel

class LiveChannelsAdapter(
    private val channels: List<LiveChannel>,
    private val onChannelClick: (LiveChannel) -> Unit
) : RecyclerView.Adapter<LiveChannelsAdapter.ChannelViewHolder>() {

    class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvChannelName: TextView = itemView.findViewById(R.id.tvChannelName)
        val ivChannelLogo: ImageView = itemView.findViewById(R.id.ivChannelLogo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]
        holder.tvChannelName.text = channel.name

        // عند الضغط على القناة يتم تفعيل الحدث للانتقال للمشغل
        holder.itemView.setOnClickListener {
            onChannelClick(channel)
        }
    }

    override fun getItemCount(): Int = channels.size
}
