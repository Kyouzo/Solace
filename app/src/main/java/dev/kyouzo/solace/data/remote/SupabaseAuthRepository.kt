package dev.kyouzo.solace.data.remote

import android.content.Context
import dev.kyouzo.solace.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthSession(val userId: String, val email: String, val accessToken: String, val refreshToken: String)

class SupabaseAuthRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val identity = context.applicationContext.getSharedPreferences("identity", Context.MODE_PRIVATE)
    private val sync = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)
    val isConfigured get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    /** Only a session with a real access token counts as signed in (sign-up awaiting confirmation has none). */
    fun savedSession(): AuthSession? {
        val token = preferences.getString("access_token", "").orEmpty()
        val userId = preferences.getString("user_id", null) ?: return null
        if (token.isBlank()) return null
        return AuthSession(userId, preferences.getString("email", "").orEmpty(), token, preferences.getString("refresh_token", "").orEmpty())
    }

    suspend fun signIn(email: String, password: String): Result<AuthSession> = authenticate("token?grant_type=password", JSONObject().put("email", email).put("password", password))
    suspend fun signUp(email: String, password: String): Result<AuthSession> = authenticate("signup", JSONObject().put("email", email).put("password", password).put("redirect_to", "solace://auth-callback"))

    fun signOut() {
        preferences.edit().clear().apply()
        identity.edit().remove("user_id").apply()
        sync.edit().clear().apply()
    }

    /** Exchanges the refresh token for a new access token. Returns false if the session can no longer be renewed. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            check(isConfigured)
            val refreshToken = preferences.getString("refresh_token", "").orEmpty()
            check(refreshToken.isNotBlank())
            val (code, text) = post("token?grant_type=refresh_token", JSONObject().put("refresh_token", refreshToken))
            check(code in 200..299)
            val json = JSONObject(text)
            val access = json.optString("access_token")
            check(access.isNotBlank())
            preferences.edit().putString("access_token", access).putString("refresh_token", json.optString("refresh_token", refreshToken)).apply()
        }.isSuccess
    }

    private fun post(path: String, body: JSONObject): Pair<Int, String> {
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/" + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY); setRequestProperty("Content-Type", "application/json")
            doOutput = true; outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        return code to text
    }

    private suspend fun authenticate(path: String, body: JSONObject): Result<AuthSession> = withContext(Dispatchers.IO) { runCatching {
        check(isConfigured) { "Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties first." }
        val (code, response) = post(path, body)
        check(code in 200..299) { JSONObject(response).let { it.optString("msg", it.optString("error_description", "Sign-in failed")) } }
        val json = JSONObject(response)
        val user = json.optJSONObject("user") ?: json.optJSONObject("data")?.optJSONObject("user") ?: json
        val userId = user.optString("id")
        check(userId.isNotBlank()) { json.optString("message", json.optString("msg", "Supabase did not return an account ID.")) }
        val session = AuthSession(userId, user.optString("email"), json.optString("access_token"), json.optString("refresh_token"))
        // A sign-up that still needs email confirmation has no tokens: do not store it as a signed-in session.
        if (session.accessToken.isNotBlank()) {
            preferences.edit().putString("user_id", session.userId).putString("email", session.email).putString("access_token", session.accessToken).putString("refresh_token", session.refreshToken).apply()
            identity.edit().putString("user_id", session.userId).apply()
        }
        session
    } }
}
