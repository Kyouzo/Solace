package dev.kyouzo.solace.ui.account

import androidx.annotation.StringRes
import dev.kyouzo.solace.R

/** Pure input checks and error mapping for the sign-in form, kept free of Android types so they can be unit tested. */
object AuthValidation {
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    const val MIN_PASSWORD = 6

    fun isValidEmail(email: String): Boolean = emailPattern.matches(email.trim())
    fun isValidPassword(password: String): Boolean = password.length >= MIN_PASSWORD

    /** Maps raw Supabase messages to friendlier text; returns null when there is no better wording. */
    @StringRes
    fun friendlyErrorRes(raw: String?): Int? {
        val text = raw?.lowercase() ?: return null
        return when {
            "rate limit" in text -> R.string.auth_error_rate_limit
            "invalid login credentials" in text -> R.string.auth_error_bad_credentials
            "already registered" in text || "already exists" in text -> R.string.auth_error_exists
            "email not confirmed" in text -> R.string.auth_error_unconfirmed
            "unable to resolve host" in text || "failed to connect" in text || "timeout" in text -> R.string.auth_error_network
            else -> null
        }
    }
}
