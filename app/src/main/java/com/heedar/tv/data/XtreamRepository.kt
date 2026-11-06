package com.heedar.tv.data

import com.heedar.tv.domain.AccountProfile
import com.heedar.tv.domain.Category
import com.heedar.tv.domain.Channel
import com.heedar.tv.domain.Movie
import com.heedar.tv.domain.MovieDetail
import com.heedar.tv.domain.Series
import com.heedar.tv.domain.SeriesDetail
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

    suspend fun loadLiveCategories(): XtreamResult<List<Category>>

    suspend fun loadVodCategories(): XtreamResult<List<Category>>

    suspend fun loadSeriesCategories(): XtreamResult<List<Category>>

    /** Full series payload: metadata plus every season with its episodes. */
    suspend fun loadSeriesDetail(seriesId: Int): XtreamResult<SeriesDetail>

    suspend fun loadMovieDetail(vodId: Int): XtreamResult<MovieDetail>

    /** Subscription status/expiry for the signed-in account. */
    suspend fun loadProfile(): XtreamResult<AccountProfile>
}
