package com.visioo.app.ui

import androidx.annotation.StringRes
import com.visioo.app.R
import com.visioo.core.network.ApiError
import com.visioo.core.network.ApiException

/** Maps server error codes first, then the broad error class, to a localized message. Never shows raw server text. */
@StringRes
fun Throwable.messageRes(): Int {
    val e = this as? ApiException ?: return R.string.error_unknown
    return when (e.code) {
        "INVALID_CREDENTIALS" -> R.string.error_invalid_credentials
        "EMAIL_TAKEN" -> R.string.error_email_taken
        "DEVICE_LIMIT_REACHED" -> R.string.error_device_limit
        "INVALID_KEY" -> R.string.error_invalid_key
        "KEY_ALREADY_USED" -> R.string.error_key_used
        "KEY_EXPIRED" -> R.string.error_key_expired
        "KEY_REVOKED" -> R.string.error_key_revoked
        "INVALID_RECOVERY" -> R.string.error_invalid_recovery
        "INVALID_GOOGLE_TOKEN", "GOOGLE_NOT_CONFIGURED" -> R.string.error_google
        "VIP_REQUIRED" -> R.string.error_vip_required
        "VIP_EXPIRED" -> R.string.error_vip_expired
        "PROFILE_RESTRICTED" -> R.string.error_profile_restricted
        "NO_SOURCE" -> R.string.error_no_source
        "PROFILE_LIMIT" -> R.string.error_profile_limit
        "LAST_PROFILE" -> R.string.error_last_profile
        else -> e.error.messageRes()
    }
}

@StringRes
fun ApiError.messageRes(): Int = when (this) {
    ApiError.Network -> R.string.error_network
    ApiError.Unauthorized -> R.string.error_unauthorized
    ApiError.Forbidden -> R.string.error_forbidden
    ApiError.NotFound -> R.string.error_not_found
    ApiError.RateLimited -> R.string.error_rate_limited
    ApiError.Server -> R.string.error_server
    ApiError.Unknown -> R.string.error_unknown
}
