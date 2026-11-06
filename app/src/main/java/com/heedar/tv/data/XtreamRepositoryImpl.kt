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
