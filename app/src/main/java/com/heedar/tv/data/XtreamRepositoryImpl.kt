package com.heedar.tv.data

import com.google.gson.JsonParseException
import com.heedar.tv.data.remote.CategoryDto
import com.heedar.tv.data.remote.EpisodeDto
import com.heedar.tv.data.remote.LiveStreamDto
import com.heedar.tv.data.remote.SeriesDto
import com.heedar.tv.data.remote.SeriesInfoDto
import com.heedar.tv.data.remote.UserInfoDto
import com.heedar.tv.data.remote.VodInfoDto
import com.heedar.tv.data.remote.VodStreamDto
import com.heedar.tv.data.remote.XtreamApiService
import com.heedar.tv.domain.AccountProfile
import com.heedar.tv.domain.Category
import com.heedar.tv.domain.Channel
import com.heedar.tv.domain.Episode
import com.heedar.tv.domain.Movie
import com.heedar.tv.domain.MovieDetail
import com.heedar.tv.domain.Season
import com.heedar.tv.domain.Series
import com.heedar.tv.domain.SeriesDetail
import com.heedar.tv.domain.ServerConfig
import com.heedar.tv.domain.XtreamFailure
import com.heedar.tv.domain.XtreamResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Maps the Xtream API onto domain models and converts every thrown exception into
 * an [XtreamResult.Failure]. All I/O runs on the injected dispatcher so tests can
 * drive it deterministically.
 */
class XtreamRepositoryImpl(
    private val api: XtreamApiService,
    private val config: ServerConfig,
    private val ioDispatcher: CoroutineDispatcher
) : XtreamRepository {

    override suspend fun authenticate(): XtreamResult<Unit> = withContext(ioDispatcher) {
        try {
            val response = api.authenticate(config.username, config.password)
            if (response.userInfo?.auth == 1) {
                XtreamResult.Success(Unit)
            } else {
                XtreamResult.Failure(XtreamFailure.AUTH)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (io: IOException) {
            XtreamResult.Failure(XtreamFailure.NETWORK, io.message)
        } catch (http: HttpException) {
            XtreamResult.Failure(XtreamFailure.NETWORK, http.message())
        } catch (parse: JsonParseException) {
            XtreamResult.Failure(XtreamFailure.PARSE, parse.message)
        } catch (unexpected: Exception) {
            XtreamResult.Failure(XtreamFailure.UNKNOWN, unexpected.message)
        }
    }

    override suspend fun loadLiveChannels(): XtreamResult<List<Channel>> = withContext(ioDispatcher) {
        execute(
            request = {
                api.getLiveStreams(
                    config.username,
                    config.password,
                    XtreamApiService.ACTION_LIVE_STREAMS
                )
            },
            mapper = { dtos: List<LiveStreamDto> -> dtos.mapNotNull { it.toChannel() } }
        )
    }

    override suspend fun loadVod(): XtreamResult<List<Movie>> = withContext(ioDispatcher) {
        execute(
            request = {
                api.getVodStreams(
                    config.username,
                    config.password,
                    XtreamApiService.ACTION_VOD_STREAMS
                )
            },
            mapper = { dtos: List<VodStreamDto> -> dtos.mapNotNull { it.toMovie() } }
        )
    }

    override suspend fun loadSeries(): XtreamResult<List<Series>> = withContext(ioDispatcher) {
        execute(
            request = {
                api.getSeries(
                    config.username,
                    config.password,
                    XtreamApiService.ACTION_SERIES
                )
            },
            mapper = { dtos: List<SeriesDto> -> dtos.mapNotNull { it.toSeries() } }
        )
    }

    override suspend fun loadLiveCategories(): XtreamResult<List<Category>> =
        withContext(ioDispatcher) {
            execute(
                request = {
                    api.getCategories(
                        config.username,
                        config.password,
                        XtreamApiService.ACTION_LIVE_CATEGORIES
                    )
                },
                mapper = { dtos: List<CategoryDto> -> dtos.mapNotNull { it.toCategory() } }
            )
        }

    override suspend fun loadVodCategories(): XtreamResult<List<Category>> =
        withContext(ioDispatcher) {
            execute(
                request = {
                    api.getCategories(
                        config.username,
                        config.password,
                        XtreamApiService.ACTION_VOD_CATEGORIES
                    )
                },
                mapper = { dtos: List<CategoryDto> -> dtos.mapNotNull { it.toCategory() } }
            )
        }

    override suspend fun loadSeriesCategories(): XtreamResult<List<Category>> =
        withContext(ioDispatcher) {
            execute(
                request = {
                    api.getCategories(
                        config.username,
                        config.password,
                        XtreamApiService.ACTION_SERIES_CATEGORIES
                    )
                },
                mapper = { dtos: List<CategoryDto> -> dtos.mapNotNull { it.toCategory() } }
            )
        }

    override suspend fun loadSeriesDetail(seriesId: Int): XtreamResult<SeriesDetail> =
        withContext(ioDispatcher) {
            execute(
                request = {
                    api.getSeriesInfo(
                        config.username,
                        config.password,
                        XtreamApiService.ACTION_SERIES_INFO,
                        seriesId
                    )
                },
                mapper = { dto: SeriesInfoDto -> dto.toSeriesDetail(seriesId) }
            )
        }

    override suspend fun loadMovieDetail(vodId: Int): XtreamResult<MovieDetail> =
        withContext(ioDispatcher) {
            execute(
                request = {
                    api.getVodInfo(
                        config.username,
                        config.password,
                        XtreamApiService.ACTION_VOD_INFO,
                        vodId
                    )
                },
                mapper = { dto: VodInfoDto -> dto.toMovieDetail(vodId) }
            )
        }

    /**
     * Re-authenticates to read the subscription snapshot. Kept separate from
     * [authenticate] because a rejected session here is an auth failure, not a
     * malformed payload.
     */
    override suspend fun loadProfile(): XtreamResult<AccountProfile> =
        withContext(ioDispatcher) {
            try {
                val info = api.authenticate(config.username, config.password).userInfo
                if (info == null || info.auth != 1) {
                    XtreamResult.Failure(XtreamFailure.AUTH)
                } else {
                    XtreamResult.Success(info.toProfile())
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (io: IOException) {
                XtreamResult.Failure(XtreamFailure.NETWORK, io.message)
            } catch (http: HttpException) {
                XtreamResult.Failure(XtreamFailure.NETWORK, http.message())
            } catch (parse: JsonParseException) {
                XtreamResult.Failure(XtreamFailure.PARSE, parse.message)
            } catch (unexpected: Exception) {
                XtreamResult.Failure(XtreamFailure.UNKNOWN, unexpected.message)
            }
        }

    /**
     * Runs [request] and [mapper] inside one try/catch so both network errors and
     * malformed payloads become typed failures instead of crashing the caller.
     */
    private inline fun <T, R> execute(
        request: () -> T,
        mapper: (T) -> R
    ): XtreamResult<R> = try {
        XtreamResult.Success(mapper(request()))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (io: IOException) {
        XtreamResult.Failure(XtreamFailure.NETWORK, io.message)
    } catch (http: HttpException) {
        XtreamResult.Failure(XtreamFailure.NETWORK, http.message())
    } catch (parse: JsonParseException) {
        XtreamResult.Failure(XtreamFailure.PARSE, parse.message)
    } catch (unexpected: Exception) {
        XtreamResult.Failure(XtreamFailure.UNKNOWN, unexpected.message)
    }
}

private fun CategoryDto.toCategory(): Category? {
    val id = categoryId?.trim().orEmpty()
    val label = categoryName?.trim().orEmpty()
    if (id.isEmpty() || label.isEmpty()) return null
    return Category(id = id, name = label)
}

/**
 * Xtream returns episodes as a map keyed by season number, and the `seasons`
 * array is optional. Both are merged here so the UI always receives ordered
 * seasons with ordered episodes, and unknown seasons are never invented.
 */
private fun SeriesInfoDto.toSeriesDetail(seriesId: Int): SeriesDetail {
    val meta = info
    val seasonLabels = seasons.orEmpty()
        .mapNotNull { season -> season.seasonNumber?.let { number -> number to season.name?.trim() } }
        .toMap()

    val seasonBlocks = episodes.orEmpty().entries.mapNotNull { (key, list) ->
        val seasonNumber = key.trim().toIntOrNull() ?: return@mapNotNull null
        val mappedEpisodes = list.mapNotNull { it.toEpisode(seasonNumber) }.sortedBy { it.number }
        if (mappedEpisodes.isEmpty()) return@mapNotNull null
        Season(
            number = seasonNumber,
            title = seasonLabels[seasonNumber]?.takeIf { it.isNotEmpty() },
            episodes = mappedEpisodes
        )
    }.sortedBy { it.number }

    return SeriesDetail(
        id = seriesId,
        name = meta?.name?.trim().orEmpty(),
        coverUrl = meta?.cover?.trim()?.takeIf { it.isNotEmpty() },
        plot = meta?.plot?.trim()?.takeIf { it.isNotEmpty() },
        cast = meta?.cast?.trim()?.takeIf { it.isNotEmpty() },
        director = meta?.director?.trim()?.takeIf { it.isNotEmpty() },
        genre = meta?.genre?.trim()?.takeIf { it.isNotEmpty() },
        releaseDate = meta?.releaseDate?.trim()?.takeIf { it.isNotEmpty() },
        rating = meta?.rating?.trim()?.takeIf { it.isNotEmpty() },
        seasons = seasonBlocks
    )
}

private fun EpisodeDto.toEpisode(fallbackSeason: Int): Episode? {
    // Episode ids arrive as strings on most panels and are the stream id used to
    // build /series/ URLs, so an unparsable id means the episode is unplayable.
    val episodeId = id?.trim()?.toIntOrNull() ?: return null
    return Episode(
        id = episodeId,
        season = season ?: fallbackSeason,
        number = episodeNum ?: 0,
        title = title?.trim().orEmpty(),
        imageUrl = info?.movieImage?.trim()?.takeIf { it.isNotEmpty() },
        containerExtension = containerExtension?.trim()?.takeIf { it.isNotEmpty() },
        plot = info?.plot?.trim()?.takeIf { it.isNotEmpty() }
    )
}

private fun VodInfoDto.toMovieDetail(vodId: Int): MovieDetail {
    val meta = info
    val data = movieData
    return MovieDetail(
        id = data?.streamId ?: vodId,
        name = (meta?.name ?: data?.name)?.trim().orEmpty(),
        posterUrl = meta?.movieImage?.trim()?.takeIf { it.isNotEmpty() },
        plot = meta?.plot?.trim()?.takeIf { it.isNotEmpty() },
        cast = meta?.cast?.trim()?.takeIf { it.isNotEmpty() },
        director = meta?.director?.trim()?.takeIf { it.isNotEmpty() },
        genre = meta?.genre?.trim()?.takeIf { it.isNotEmpty() },
        duration = meta?.duration?.trim()?.takeIf { it.isNotEmpty() },
        rating = meta?.rating?.trim()?.takeIf { it.isNotEmpty() },
        releaseDate = (meta?.releaseDate ?: meta?.releaseDateAlt)?.trim()?.takeIf { it.isNotEmpty() },
        containerExtension = data?.containerExtension?.trim()?.takeIf { it.isNotEmpty() }
    )
}

private fun UserInfoDto.toProfile(): AccountProfile = AccountProfile(
    username = username?.trim()?.takeIf { it.isNotEmpty() },
    status = status?.trim()?.takeIf { it.isNotEmpty() },
    // exp_date is a unix timestamp in seconds; non-positive means "never expires".
    expiresAtMillis = expDate?.takeIf { it > 0L }?.times(1000L),
    maxConnections = maxConnections?.trim()?.toIntOrNull(),
    activeConnections = activeConnections?.trim()?.toIntOrNull(),
    isTrial = isTrial?.trim() == "1"
)

private fun LiveStreamDto.toChannel(): Channel? {
    val id = streamId ?: return null
    val channelName = name?.trim().orEmpty()
    if (channelName.isEmpty()) return null
    return Channel(
        id = id,
        name = channelName,
        logoUrl = streamIcon?.trim()?.takeIf { it.isNotEmpty() },
        categoryId = categoryId
    )
}

private fun VodStreamDto.toMovie(): Movie? {
    val id = streamId ?: return null
    val movieName = name?.trim().orEmpty()
    if (movieName.isEmpty()) return null
    return Movie(
        id = id,
        name = movieName,
        posterUrl = streamIcon?.trim()?.takeIf { it.isNotEmpty() },
        categoryId = categoryId
    )
}

private fun SeriesDto.toSeries(): Series? {
    val id = seriesId ?: return null
    val seriesName = name?.trim().orEmpty()
    if (seriesName.isEmpty()) return null
    return Series(
        id = id,
        name = seriesName,
        coverUrl = cover?.trim()?.takeIf { it.isNotEmpty() },
        categoryId = categoryId
    )
}
