package com.visioo.core.network

/** Where tokens and the selected profile live. The Android implementation is Keystore-backed. */
interface SessionStore {
    fun accessToken(): String?
    fun refreshToken(): String?
    fun profileId(): String?
    fun saveTokens(accessToken: String, refreshToken: String)
    fun setProfileId(id: String?)
    fun clear()
}
