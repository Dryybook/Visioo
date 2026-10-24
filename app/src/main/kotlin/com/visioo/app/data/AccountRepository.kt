package com.visioo.app.data

import com.visioo.core.network.ActivateRequest
import com.visioo.core.network.MeDto
import com.visioo.core.network.ProfileDto
import com.visioo.core.network.ProfileRequest
import com.visioo.core.network.VipDto
import com.visioo.core.network.VisiooApi
import com.visioo.core.network.safeApiCall

class AccountRepository(private val api: VisiooApi) {
    suspend fun me(): Result<MeDto> = safeApiCall { api.me() }
    suspend fun vip(): Result<VipDto> = safeApiCall { api.vip() }
    suspend fun activateVip(key: String): Result<VipDto> = safeApiCall { api.activateVip(ActivateRequest(key)) }

    /** Returns the 12 words. The server keeps only a hash, so this is the only time they can be shown. */
    suspend fun createRecoveryPhrase(): Result<List<String>> = safeApiCall { api.setupRecovery().words }

    suspend fun profiles(): Result<List<ProfileDto>> = safeApiCall { api.profiles().profiles }
    suspend fun createProfile(name: String, isChild: Boolean): Result<ProfileDto> =
        safeApiCall { api.createProfile(ProfileRequest(name.trim(), isChild)) }
    suspend fun updateProfile(id: String, name: String, isChild: Boolean): Result<ProfileDto> =
        safeApiCall { api.updateProfile(id, ProfileRequest(name.trim(), isChild)) }
    suspend fun deleteProfile(id: String): Result<Unit> = safeApiCall { api.deleteProfile(id) }
}
