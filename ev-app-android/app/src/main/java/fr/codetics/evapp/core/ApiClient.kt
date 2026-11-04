// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.core

import fr.codetics.evapp.models.CsrfResponse
import fr.codetics.evapp.models.ProblemDetail
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

sealed class ApiError : Exception() {
    data object Unauthorized : ApiError() {
        private fun readResolve(): Any = Unauthorized
    }

    data class Server(val status: Int, val detail: String?) : ApiError()

    data object Offline : ApiError() {
        private fun readResolve(): Any = Offline
    }

    data object InvalidResponse : ApiError() {
        private fun readResolve(): Any = InvalidResponse
    }
}

private class MemoryCookieJar : CookieJar {
    private val cookies = mutableMapOf<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { this.cookies["${it.domain}|${it.path}|${it.name}"] = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.values.removeAll { it.expiresAt < now }
        return cookies.values.filter { it.matches(url) }
    }
}

class ApiClient(baseUrl: String, interceptor: Interceptor? = null) {
    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .cookieJar(MemoryCookieJar())
            .apply { interceptor?.let(::addInterceptor) }
            .build()

    @Volatile var bearerToken: String? = null
    @Volatile private var csrf: CsrfResponse? = null

    suspend inline fun <reified T> get(
        path: String,
        query: Map<String, String?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): T = decode(serializer(), send("GET", path, query, headers, null))

    suspend fun data(path: String): ByteArray = send("GET", path, emptyMap(), emptyMap(), null)

    suspend inline fun <reified T> post(path: String, headers: Map<String, String> = emptyMap()): T =
        decode(serializer(), send("POST", path, emptyMap(), headers, null))

    suspend inline fun <reified B, reified T> post(path: String, body: B): T =
        decode(serializer(), send("POST", path, emptyMap(), emptyMap(), jsonBody(serializer<B>(), body)))

    suspend inline fun <reified B, reified T> put(path: String, body: B): T =
        decode(serializer(), send("PUT", path, emptyMap(), emptyMap(), jsonBody(serializer<B>(), body)))

    suspend inline fun <reified T> delete(path: String): T =
        decode(serializer(), send("DELETE", path, emptyMap(), emptyMap(), null))

    suspend inline fun <reified T> upload(path: String, bytes: ByteArray, contentType: String): T =
        decode(serializer(), send("POST", path, emptyMap(), emptyMap(), bytes.toRequestBody(contentType.toMediaType())))

    fun <B> jsonBody(strategy: KSerializer<B>, body: B): RequestBody =
        JsonCoding.json.encodeToString(strategy, body).toRequestBody(JSON)

    fun <T> decode(strategy: DeserializationStrategy<T>, bytes: ByteArray): T =
        try {
            JsonCoding.decode(strategy, bytes.decodeToString())
        } catch (_: SerializationException) {
            throw ApiError.InvalidResponse
        } catch (_: IllegalArgumentException) {
            throw ApiError.InvalidResponse
        } catch (_: java.time.format.DateTimeParseException) {
            throw ApiError.InvalidResponse
        }

    suspend fun send(
        method: String,
        path: String,
        query: Map<String, String?>,
        headers: Map<String, String>,
        body: RequestBody?,
        retried: Boolean = false,
    ): ByteArray {
        val builder = Request.Builder().url(url(path, query)).header("Accept", "application/json")
        bearerToken?.let { builder.header("Authorization", "Bearer $it") }
        headers.forEach { (key, value) -> builder.header(key, value) }
        if (method != "GET") {
            val token = csrfToken()
            builder.header(token.headerName, token.token)
        }
        builder.method(method, body ?: if (method == "GET") null else ByteArray(0).toRequestBody(null))
        val response =
            try {
                client.newCall(builder.build()).await()
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                throw ApiError.Offline
            }
        return response.use {
            val data =
                try {
                    it.body.bytes()
                } catch (_: IOException) {
                    throw ApiError.Offline
                }
            when {
                it.isSuccessful -> data
                it.code == 403 && method != "GET" && !retried -> {
                    csrf = null
                    send(method, path, query, headers, body, retried = true)
                }
                it.code == 401 && bearerToken != null -> throw ApiError.Unauthorized
                else -> throw ApiError.Server(it.code, problem(data))
            }
        }
    }

    private fun problem(data: ByteArray): String? =
        runCatching { JsonCoding.decode(ProblemDetail.serializer(), data.decodeToString()).detail }.getOrNull()

    private suspend fun csrfToken(): CsrfResponse {
        csrf?.let { return it }
        val data = send("GET", "api/v1/auth/csrf", emptyMap(), emptyMap(), null)
        return decode(CsrfResponse.serializer(), data).also { csrf = it }
    }

    private fun url(path: String, query: Map<String, String?>): HttpUrl {
        val builder = base.newBuilder()
        path.split('/').filter(String::isNotEmpty).forEach { builder.addPathSegment(it) }
        query.toSortedMap().forEach { (key, value) -> value?.let { builder.addQueryParameter(key, it) } }
        return builder.build()
    }

    private suspend fun Call.await(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!continuation.isCancelled) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response) { _, _, _ -> response.close() }
                    }
                }
            )
        }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
