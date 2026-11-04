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
    val isConfigured get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
    fun savedSession(): AuthSession? = preferences.getString("user_id", null)?.let { AuthSession(it, preferences.getString("email", "")!!, preferences.getString("access_token", "")!!, preferences.getString("refresh_token", "")!!) }
    suspend fun signIn(email: String, password: String): Result<AuthSession> = authenticate("token?grant_type=password", JSONObject().put("email", email).put("password", password))
    suspend fun signUp(email: String, password: String): Result<AuthSession> = authenticate("signup", JSONObject().put("email", email).put("password", password))
    fun signOut() { preferences.edit().clear().apply() }
    private suspend fun authenticate(path: String, body: JSONObject): Result<AuthSession> = withContext(Dispatchers.IO) { runCatching {
        check(isConfigured) { "Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties first." }
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/" + path).openConnection() as HttpURLConnection).apply { requestMethod = "POST"; setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY); setRequestProperty("Content-Type", "application/json"); doOutput = true; outputStream.use { it.write(body.toString().toByteArray()) } }
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        check(connection.responseCode in 200..299) { JSONObject(response).optString("msg", "Sign-in failed") }
        val json = JSONObject(response); val user = json.getJSONObject("user")
        AuthSession(user.getString("id"), user.optString("email"), json.optString("access_token"), json.optString("refresh_token")).also { session -> preferences.edit().putString("user_id", session.userId).putString("email", session.email).putString("access_token", session.accessToken).putString("refresh_token", session.refreshToken).apply() }
    } }
}
