package com.visioo.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET

@Serializable
data class HomeResponseDto(val sections: List<HomeSectionDto> = emptyList())

@Serializable
data class HomeSectionDto(
    val id: String,
    val title: String,
    val layout: String = "poster",
    val items: List<ContentSummaryDto> = emptyList(),
)

@Serializable
data class ContentSummaryDto(
    val id: String,
    val title: String,
    val type: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val isVip: Boolean = false,
)

interface VisiooApi {
    @GET("home")
    suspend fun home(): HomeResponseDto
}
