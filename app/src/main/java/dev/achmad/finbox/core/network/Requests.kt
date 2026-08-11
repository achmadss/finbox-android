package dev.achmad.finbox.core.network

import kotlin.time.Duration.Companion.minutes
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

private val DEFAULT_CACHE_CONTROL = CacheControl.Builder().maxAge(10.minutes).build()
private val EMPTY_HEADERS = Headers.Builder().build()

/**
 * Executes a GET request and returns the response.
 *
 * @param cacheControl the cache policy, or null to send the request without one —
 *   use null for authorized requests, whose responses must not land in the shared cache.
 * @param ensureSuccess throws [HttpException] on a non-2xx response.
 */
suspend fun OkHttpClient.get(
    url: String,
    headers: Headers = EMPTY_HEADERS,
    cacheControl: CacheControl? = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = execute(url, headers, cacheControl, ensureSuccess) { get() }

/** Executes a HEAD request and returns the response. See [get] for the parameters. */
suspend fun OkHttpClient.head(
    url: String,
    headers: Headers = EMPTY_HEADERS,
    cacheControl: CacheControl? = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = execute(url, headers, cacheControl, ensureSuccess) { head() }

/** Executes a POST request and returns the response. See [get] for the parameters. */
suspend fun OkHttpClient.post(
    url: String,
    body: RequestBody,
    headers: Headers = EMPTY_HEADERS,
    cacheControl: CacheControl? = null,
    ensureSuccess: Boolean = true,
): Response = execute(url, headers, cacheControl, ensureSuccess) { post(body) }

/** Executes a PUT request and returns the response. See [get] for the parameters. */
suspend fun OkHttpClient.put(
    url: String,
    body: RequestBody,
    headers: Headers = EMPTY_HEADERS,
    cacheControl: CacheControl? = null,
    ensureSuccess: Boolean = true,
): Response = execute(url, headers, cacheControl, ensureSuccess) { put(body) }

/** Executes a PATCH request and returns the response. See [get] for the parameters. */
suspend fun OkHttpClient.patch(
    url: String,
    body: RequestBody,
    headers: Headers = EMPTY_HEADERS,
    cacheControl: CacheControl? = null,
    ensureSuccess: Boolean = true,
): Response = execute(url, headers, cacheControl, ensureSuccess) { patch(body) }

/** Executes a DELETE request and returns the response. See [get] for the parameters. */
suspend fun OkHttpClient.delete(
    url: String,
    body: RequestBody? = null,
    headers: Headers = EMPTY_HEADERS,
    cacheControl: CacheControl? = null,
    ensureSuccess: Boolean = true,
): Response = execute(url, headers, cacheControl, ensureSuccess) { delete(body) }

private suspend fun OkHttpClient.execute(
    url: String,
    headers: Headers,
    cacheControl: CacheControl?,
    ensureSuccess: Boolean,
    method: Request.Builder.() -> Request.Builder,
): Response {
    val request = Request.Builder()
        .url(url)
        .headers(headers)
        .apply { cacheControl?.let(::cacheControl) }
        .method()
        .build()
    return newCall(request).let { if (ensureSuccess) it.awaitSuccess() else it.await() }
}
