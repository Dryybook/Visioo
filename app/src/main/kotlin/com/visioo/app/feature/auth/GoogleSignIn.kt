package com.visioo.app.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException

/** Gets a Google ID token for [serverClientId] (the web client id the backend verifies against). */
suspend fun requestGoogleIdToken(context: Context, serverClientId: String): Result<String> = try {
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(serverClientId)
        .setFilterByAuthorizedAccounts(false)
        .build()
    val response = CredentialManager.create(context).getCredential(
        context,
        GetCredentialRequest.Builder().addCredentialOption(option).build(),
    )
    val credential = response.credential
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        Result.success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
    } else {
        Result.failure(IllegalStateException("Unexpected credential type"))
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
