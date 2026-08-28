package dev.achmad.finbox.util.network

import okhttp3.Response

/**
 * An HTTP response that OkHttp does not consider successful.
 *
 * @see Response.isSuccessful
 */
class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
