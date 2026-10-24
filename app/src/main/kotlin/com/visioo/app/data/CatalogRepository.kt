package com.visioo.app.data

import com.visioo.core.network.ContentSummaryDto
import com.visioo.core.network.EpisodeDto
import com.visioo.core.network.GenreDto
import com.visioo.core.network.HomeSectionDto
import com.visioo.core.network.MovieDetailDto
import com.visioo.core.network.SeasonDto
import com.visioo.core.network.SeriesDetailDto
import com.visioo.core.network.VisiooApi
import com.visioo.core.network.safeApiCall

enum class SectionLayout { Poster, Landscape }
enum class ContentType { Movie, Series;
    val path get() = name.lowercase()
    companion object { fun fromPath(value: String) = entries.firstOrNull { it.path == value } }
}

data class ContentSummary(
    val id: String,
    val title: String,
    val type: ContentType,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val year: Int?,
    val isVip: Boolean,
)

data class HomeSection(val id: String, val title: String, val layout: SectionLayout, val items: List<ContentSummary>)
data class HomeContent(val hero: List<ContentSummary>, val sections: List<HomeSection>)

data class Genre(val slug: String, val name: String)

data class ContentDetail(
    val id: String,
    val type: ContentType,
    val title: String,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val runtimeMin: Int?,
    val rating: Double?,
    val ageRating: Int,
    val isVip: Boolean,
    val genres: List<Genre>,
    val canPlay: Boolean,
    val seasons: List<SeasonSummary>,
)

data class SeasonSummary(val id: String, val number: Int, val title: String?)
data class Season(val id: String, val number: Int, val title: String?, val episodes: List<Episode>)
data class Episode(val id: String, val number: Int, val title: String, val overview: String?, val thumbnailUrl: String?, val runtimeMin: Int?)

interface CatalogRepository {
    suspend fun getHome(): Result<HomeContent>
    suspend fun genres(): Result<List<Genre>>
    suspend fun browse(type: ContentType, genre: String?, offset: Int, limit: Int): Result<List<ContentSummary>>
    suspend fun detail(type: ContentType, id: String): Result<ContentDetail>
    suspend fun seasons(seriesId: String): Result<List<Season>>
    suspend fun search(query: String): Result<List<ContentSummary>>
}

class RemoteCatalogRepository(private val api: VisiooApi) : CatalogRepository {
    override suspend fun getHome() = safeApiCall {
        val home = api.home()
        HomeContent(
            hero = home.hero.map { it.toDomain() },
            sections = home.sections.map { it.toDomain() }.filter { it.items.isNotEmpty() },
        )
    }

    override suspend fun genres() = safeApiCall { api.genres().genres.map { Genre(it.slug, it.name) } }

    override suspend fun browse(type: ContentType, genre: String?, offset: Int, limit: Int) = safeApiCall {
        val page = if (type == ContentType.Movie) api.movies(limit, offset, genre) else api.series(limit, offset, genre)
        page.items.map { it.toDomain() }
    }

    override suspend fun detail(type: ContentType, id: String) = safeApiCall {
        if (type == ContentType.Movie) api.movie(id).toDomain() else api.seriesDetail(id).toDomain()
    }

    override suspend fun seasons(seriesId: String) = safeApiCall { api.seasons(seriesId).seasons.map { it.toDomain() } }

    override suspend fun search(query: String) = safeApiCall { api.search(query, 30).items.map { it.toDomain() } }
}

internal fun HomeSectionDto.toDomain() = HomeSection(
    id = id,
    title = title,
    layout = if (layout == "landscape") SectionLayout.Landscape else SectionLayout.Poster,
    items = items.map { it.toDomain() },
)

internal fun ContentSummaryDto.toDomain() = ContentSummary(
    id = id,
    title = title,
    type = ContentType.fromPath(type) ?: ContentType.Movie,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    overview = overview,
    year = year,
    isVip = isVip,
)

internal fun MovieDetailDto.toDomain() = ContentDetail(
    id = id, type = ContentType.Movie, title = title, overview = overview, posterUrl = posterUrl, backdropUrl = backdropUrl,
    year = year, runtimeMin = runtimeMin, rating = rating, ageRating = ageRating, isVip = isVip,
    genres = genres.map { Genre(it.slug, it.name) }, canPlay = canPlay, seasons = emptyList(),
)

internal fun SeriesDetailDto.toDomain() = ContentDetail(
    id = id, type = ContentType.Series, title = title, overview = overview, posterUrl = posterUrl, backdropUrl = backdropUrl,
    year = year, runtimeMin = null, rating = rating, ageRating = ageRating, isVip = isVip,
    genres = genres.map { Genre(it.slug, it.name) }, canPlay = canPlay,
    seasons = seasons.map { SeasonSummary(it.id, it.seasonNumber, it.title) },
)

internal fun SeasonDto.toDomain() = Season(id, seasonNumber, title, episodes.map { it.toDomain() })
internal fun EpisodeDto.toDomain() = Episode(id, episodeNumber, title, overview, thumbnailUrl, runtimeMin)
