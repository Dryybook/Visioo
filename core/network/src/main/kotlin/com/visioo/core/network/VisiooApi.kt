package com.visioo.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class HomeResponseDto(
    val hero: List<ContentSummaryDto> = emptyList(),
    val sections: List<HomeSectionDto> = emptyList(),
)

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
    val overview: String? = null,
)

@Serializable data class PagedContentDto(val items: List<ContentSummaryDto> = emptyList(), val limit: Int = 0, val offset: Int = 0)
@Serializable data class SearchResponseDto(val items: List<ContentSummaryDto> = emptyList())
@Serializable data class GenreDto(val slug: String, val name: String)
@Serializable data class GenresResponseDto(val genres: List<GenreDto> = emptyList())

@Serializable
data class MovieDetailDto(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val year: Int? = null,
    val runtimeMin: Int? = null,
    val rating: Double? = null,
    val ageRating: Int = 0,
    val isVip: Boolean = false,
    val genres: List<GenreDto> = emptyList(),
    val canPlay: Boolean = true,
)

@Serializable
data class SeasonSummaryDto(
    val id: String,
    val seasonNumber: Int,
    val title: String? = null,
    val posterUrl: String? = null,
    val episodeCount: Int = 0,
)

@Serializable
data class SeriesDetailDto(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val ageRating: Int = 0,
    val isVip: Boolean = false,
    val genres: List<GenreDto> = emptyList(),
    val seasons: List<SeasonSummaryDto> = emptyList(),
    val canPlay: Boolean = true,
)

@Serializable
data class EpisodeDto(
    val id: String,
    val episodeNumber: Int,
    val title: String,
    val overview: String? = null,
    val thumbnailUrl: String? = null,
    val runtimeMin: Int? = null,
    val airDate: String? = null,
)

@Serializable data class SeasonDto(val id: String, val seasonNumber: Int, val title: String? = null, val episodes: List<EpisodeDto> = emptyList())
@Serializable data class SeasonsResponseDto(val seasons: List<SeasonDto> = emptyList())

@Serializable
data class ResolveRequest(val contentType: String, val contentId: String, val language: String? = null, val preferredQuality: Int? = null)

@Serializable
data class SubtitleTrackDto(val url: String, val language: String? = null, val label: String? = null, val format: String? = null)

@Serializable
data class SourceDto(
    val id: String,
    val provider: String,
    val type: String,
    val quality: Int? = null,
    val language: String? = null,
    val url: String,
    val expiresAt: String? = null,
    val subtitleTracks: List<SubtitleTrackDto> = emptyList(),
)

@Serializable data class ResolveResponseDto(val sources: List<SourceDto> = emptyList())
@Serializable data class ProgressRequest(val contentType: String, val contentId: String, val positionMs: Long, val durationMs: Long)
@Serializable data class PositionDto(val positionMs: Long = 0, val durationMs: Long = 0)

@Serializable
data class EpisodeDetailDto(
    val id: String,
    val seriesId: String,
    val seriesTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
)

@Serializable
data class DeviceInfoDto(
    val installationId: String,
    val platform: String,
    val model: String? = null,
    val appVersion: String? = null,
)

@Serializable data class RegisterRequest(val email: String, val password: String, val profileName: String? = null, val device: DeviceInfoDto)
@Serializable data class LoginRequest(val email: String, val password: String, val device: DeviceInfoDto)
@Serializable data class GoogleRequest(val idToken: String, val device: DeviceInfoDto)
@Serializable data class RecoverRequest(val email: String, val phrase: String, val newPassword: String, val device: DeviceInfoDto)
@Serializable data class RefreshRequest(val refreshToken: String)

@Serializable
data class SessionDto(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val deviceId: String,
    val recoveryReset: Boolean = false,
)

@Serializable data class RecoveryWordsDto(val words: List<String>)

@Serializable
data class VipDto(
    val active: Boolean,
    val planId: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val deviceLimit: Int? = null,
)

@Serializable
data class MeDto(
    val id: String,
    val email: String,
    val deviceId: String,
    val hasRecovery: Boolean = false,
    val vip: VipDto,
)

@Serializable data class ActivateRequest(val key: String)

@Serializable
data class ProfileDto(
    val id: String,
    val name: String,
    val avatar: String? = null,
    val isChild: Boolean = false,
    val language: String = "fr",
    val subtitleLanguage: String? = null,
    val maturityLevel: Int = 18,
    val autoplay: Boolean = true,
)

@Serializable data class ProfilesResponse(val profiles: List<ProfileDto> = emptyList())
@Serializable data class ProfileRequest(val name: String, val isChild: Boolean = false)

interface VisiooApi {
    @POST("auth/register") suspend fun register(@Body body: RegisterRequest): SessionDto
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): SessionDto
    @POST("auth/google") suspend fun google(@Body body: GoogleRequest): SessionDto
    @POST("auth/recover") suspend fun recover(@Body body: RecoverRequest): SessionDto
    @POST("auth/refresh") suspend fun refresh(@Body body: RefreshRequest): SessionDto
    @POST("auth/logout") suspend fun logout(@Body body: RefreshRequest)
    @POST("auth/recovery/setup") suspend fun setupRecovery(): RecoveryWordsDto

    @GET("me") suspend fun me(): MeDto

    @GET("vip") suspend fun vip(): VipDto
    @POST("vip/activate") suspend fun activateVip(@Body body: ActivateRequest): VipDto

    @GET("profiles") suspend fun profiles(): ProfilesResponse
    @POST("profiles") suspend fun createProfile(@Body body: ProfileRequest): ProfileDto
    @PATCH("profiles/{id}") suspend fun updateProfile(@Path("id") id: String, @Body body: ProfileRequest): ProfileDto
    @DELETE("profiles/{id}") suspend fun deleteProfile(@Path("id") id: String)

    @GET("episodes/{id}") suspend fun episode(@Path("id") id: String): EpisodeDetailDto
    @POST("playback/resolve") suspend fun resolve(@Body body: ResolveRequest): ResolveResponseDto
    @POST("history/progress") suspend fun saveProgress(@Body body: ProgressRequest)
    @GET("history/position") suspend fun position(@Query("contentType") contentType: String, @Query("contentId") contentId: String): PositionDto

    @GET("home") suspend fun home(): HomeResponseDto
    @GET("genres") suspend fun genres(): GenresResponseDto
    @GET("movies") suspend fun movies(@Query("limit") limit: Int, @Query("offset") offset: Int, @Query("genre") genre: String?): PagedContentDto
    @GET("series") suspend fun series(@Query("limit") limit: Int, @Query("offset") offset: Int, @Query("genre") genre: String?): PagedContentDto
    @GET("movies/{id}") suspend fun movie(@Path("id") id: String): MovieDetailDto
    @GET("series/{id}") suspend fun seriesDetail(@Path("id") id: String): SeriesDetailDto
    @GET("series/{id}/seasons") suspend fun seasons(@Path("id") id: String): SeasonsResponseDto
    @GET("search") suspend fun search(@Query("q") query: String, @Query("limit") limit: Int): SearchResponseDto
}
