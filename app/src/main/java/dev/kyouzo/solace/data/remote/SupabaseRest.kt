package dev.kyouzo.solace.data.remote

import dev.kyouzo.solace.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal authenticated PostgREST/RPC caller. Retries once after refreshing an expired access token. */
class SupabaseRest(private val auth: SupabaseAuthRepository) {
    suspend fun request(method: String, path: String, body: String? = null, extraHeaders: Map<String, String> = emptyMap()): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            check(auth.isConfigured) { "Supabase isn't configured." }
            for (attempt in 0..1) {
                val token = auth.savedSession()?.accessToken ?: error("Sign in first.")
                val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/" + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method; connectTimeout = 15_000; readTimeout = 30_000
                    setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
                    if (body != null) { doOutput = true; outputStream.use { it.write(body.toByteArray()) } }
                }
                val code = connection.responseCode
                val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code == 401 && attempt == 0 && auth.refresh()) continue
                check(code in 200..299) { errorMessage(text, code) }
                return@runCatching text
            }
            error("Your session expired. Sign in again.")
        }
    }

    private fun errorMessage(text: String, code: Int): String =
        runCatching { JSONObject(text).let { it.optString("message").ifBlank { it.optString("msg") } } }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Request failed ($code)"
}
