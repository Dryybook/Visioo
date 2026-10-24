package com.visioo.app.data.session

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.visioo.core.network.SessionStore
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Tokens and the installation id, encrypted with an AES-GCM key that never leaves the Android Keystore.
 * If the key is lost (e.g. restored backup), values read as absent and the user signs in again.
 */
class KeystoreSessionStore(context: Context) : SessionStore {
    private val prefs: SharedPreferences = context.getSharedPreferences("visioo_session", Context.MODE_PRIVATE)

    override fun accessToken() = read(ACCESS)
    override fun refreshToken() = read(REFRESH)
    override fun profileId() = prefs.getString(PROFILE, null)

    override fun saveTokens(accessToken: String, refreshToken: String) {
        write(ACCESS, accessToken)
        write(REFRESH, refreshToken)
    }

    override fun setProfileId(id: String?) {
        prefs.edit().apply { if (id == null) remove(PROFILE) else putString(PROFILE, id) }.apply()
    }

    override fun clear() {
        prefs.edit().remove(ACCESS).remove(REFRESH).remove(PROFILE).apply()
    }

    /** App-generated id, stable across logins. Not a hardware identifier. */
    fun installationId(): String =
        read(INSTALLATION) ?: UUID.randomUUID().toString().also { write(INSTALLATION, it) }

    private fun write(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val blob = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    private fun read(name: String): String? {
        val encoded = prefs.getString(name, null) ?: return null
        return try {
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_SIZE))
            }
            String(cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE))
        } catch (_: Exception) {
            prefs.edit().remove(name).apply()
            null
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val ALIAS = "visioo_session_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val ACCESS = "access"
        const val REFRESH = "refresh"
        const val INSTALLATION = "installation"
        const val PROFILE = "profile"
    }
}
