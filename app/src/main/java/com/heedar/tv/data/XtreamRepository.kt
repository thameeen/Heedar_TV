package com.heedar.tv.data

import com.heedar.tv.domain.Channel
import com.heedar.tv.domain.Movie
import com.heedar.tv.domain.Series
import com.heedar.tv.domain.XtreamResult

/**
 * Single entry point the ViewModels use. Implementations must never throw:
 * every failure is mapped to [XtreamResult.Failure].
 */
interface XtreamRepository {

    suspend fun authenticate(): XtreamResult<Unit>

    suspend fun loadLiveChannels(): XtreamResult<List<Channel>>

    suspend fun loadVod(): XtreamResult<List<Movie>>

    suspend fun loadSeries(): XtreamResult<List<Series>>
}
