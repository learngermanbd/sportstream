package com.sportstream.admin.data

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Phase 8 \u00b7 Step 8.13 \u2014 Tiny OkHttp wrapper for the admin REST endpoints.
 *
 * Only 2 endpoints are wired today (login + health); the full CRUD arrives in
 * Step 8.14 \u2013 8.15. Same `joinUrl` shape as :app's `ApiClient` so refactoring
 * is mechanical once it grows.
 */
class AdminApi(
    private val baseUrl: String,
    private val httpClient: OkHttpClient
) {

    /** Result type for [login]. Mirrors :app's sealed ApiResult pattern. */
    sealed class LoginResult {
        data class Success(val token: String) : LoginResult()
        data class Failure(val message: String) : LoginResult()
    }

    suspend fun login(request: LoginRequest): LoginResult = try {
        val raw = postJsonRaw(
            path = "/api/admin/auth/login",
            body = JSONObject().apply {
                put("email", request.email)
                put("password", request.password)
            }
        )
        val token = JSONObject(raw).optString("token", "")
        if (token.isEmpty()) {
            LoginResult.Failure("Server returned no token")
        } else {
            LoginResult.Success(token)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        // Structured-concurrency cancellation must propagate \u2014 never swallow it
        // as a typed Failure (would break viewModelScope.cancel() semantics).
        throw e
    } catch (e: Throwable) {
        LoginResult.Failure(e.message ?: e.javaClass.simpleName)
    }

    /** Health-check mirror for :app's /api/health. */
    suspend fun health(): String = withContext(Dispatchers.IO) {
        val url = joinUrl(baseUrl, "/api/health").newBuilder().build()
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            resp.body?.string().orEmpty()
        }
    }

    /** Raw POST JSON. Throws on non-2xx \u2014 the typed callers (e.g. [login]) decide
     *  how to convert exceptions into their own sealed Result. */
    private suspend fun postJsonRaw(path: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        val url = joinUrl(baseUrl, path).newBuilder().build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${raw.take(256)}")
            }
            raw
        }
    }

    private fun joinUrl(base: String, path: String): HttpUrl =
        (base.trimEnd('/') + if (path.startsWith('/')) path else "/$path").toHttpUrl()

    /** Stub auth interceptor. Real interceptor lands in Step 8.16 (biometric + JWT). */
    internal class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val req = chain.request()
            val token = tokenProvider()
            val authed = if (token.isNullOrEmpty()) req else req.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            return chain.proceed(authed)
        }
    }
}

/** Login request payload. */
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

/** Convenience: extract a `JSONArray<String>` field as a `List<String>`. Used
 *  by Step 8.6 generic list deserializers. */
internal fun JSONArray.toStringList(): List<String> = (0 until length()).map { optString(it) }
