package com.heedar.tv.ui.channels

import com.heedar.tv.domain.Channel
import com.heedar.tv.domain.XtreamFailure

/** Immutable screen state for the channel list. */
sealed interface ChannelsUiState {

    data object Loading : ChannelsUiState

    data class Content(val channels: List<Channel>) : ChannelsUiState

    data object Empty : ChannelsUiState

    data class Error(val reason: XtreamFailure) : ChannelsUiState
}
