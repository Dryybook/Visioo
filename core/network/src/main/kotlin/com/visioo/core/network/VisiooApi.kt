package com.visioo.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

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

    @GET("home") suspend fun home(): HomeResponseDto
}
