package dev.achmad.finbox.core.network

import okhttp3.Response

/**
 * Exception that handles HTTP codes considered not successful by OkHttp.
 *
 * @see Response.isSuccessful
 * @param code [Int] the HTTP status code
 */
class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
