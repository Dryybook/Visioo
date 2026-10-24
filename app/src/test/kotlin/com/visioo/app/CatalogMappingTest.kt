package com.visioo.app

import com.visioo.app.data.ContentType
import com.visioo.app.data.SectionLayout
import com.visioo.app.data.toDomain
import com.visioo.core.network.ContentSummaryDto
import com.visioo.core.network.GenreDto
import com.visioo.core.network.HomeSectionDto
import com.visioo.core.network.MovieDetailDto
import com.visioo.core.network.SeriesDetailDto
import com.visioo.core.network.SeasonSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogMappingTest {
    private val dto = ContentSummaryDto("m1", "T", "series", "poster", "backdrop", 2020, true, "Overview")

    @Test fun mapsSummaries() {
        val s = dto.toDomain()
        assertEquals(ContentType.Series, s.type)
        assertEquals("poster", s.posterUrl)
        assertEquals("backdrop", s.backdropUrl)
        assertTrue(s.isVip)
    }

    @Test fun unknownTypeFallsBackToMovie() = assertEquals(ContentType.Movie, dto.copy(type = "weird").toDomain().type)

    @Test fun mapsSectionLayouts() {
        assertEquals(SectionLayout.Landscape, HomeSectionDto("s", "S", "landscape", listOf(dto)).toDomain().layout)
        assertEquals(SectionLayout.Poster, HomeSectionDto("s", "S", "anything", listOf(dto)).toDomain().layout)
    }

    @Test fun mapsMovieAndSeriesDetails() {
        val movie = MovieDetailDto("m", "Movie", runtimeMin = 120, ageRating = 12, genres = listOf(GenreDto("action", "Action")), canPlay = false).toDomain()
        assertEquals(ContentType.Movie, movie.type)
        assertEquals(false, movie.canPlay)
        assertEquals("Action", movie.genres.single().name)

        val series = SeriesDetailDto("s", "Show", seasons = listOf(SeasonSummaryDto("x", 2, null, null, 8))).toDomain()
        assertEquals(ContentType.Series, series.type)
        assertEquals(2, series.seasons.single().number)
    }
}
