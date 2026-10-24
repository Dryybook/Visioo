package com.visioo.app.data

import com.visioo.core.network.ContentSummaryDto
import com.visioo.core.network.HomeSectionDto
import com.visioo.core.network.VisiooApi
import com.visioo.core.network.safeApiCall

enum class SectionLayout { Poster, Landscape }

data class ContentSummary(
    val id: String,
    val title: String,
    val type: String,
    val imageUrl: String?,
    val isVip: Boolean,
)

data class HomeSection(
    val id: String,
    val title: String,
    val layout: SectionLayout,
    val items: List<ContentSummary>,
)

interface HomeRepository {
    suspend fun getHome(): Result<List<HomeSection>>
}

class RemoteHomeRepository(private val api: VisiooApi) : HomeRepository {
    override suspend fun getHome(): Result<List<HomeSection>> = safeApiCall {
        api.home().sections.map { it.toDomain() }.filter { it.items.isNotEmpty() }
    }
}

internal fun HomeSectionDto.toDomain() = HomeSection(
    id = id,
    title = title,
    layout = if (layout == "landscape") SectionLayout.Landscape else SectionLayout.Poster,
    items = items.map { it.toDomain(landscape = layout == "landscape") },
)

internal fun ContentSummaryDto.toDomain(landscape: Boolean) = ContentSummary(
    id = id,
    title = title,
    type = type,
    imageUrl = if (landscape) backdropUrl ?: posterUrl else posterUrl ?: backdropUrl,
    isVip = isVip,
)
