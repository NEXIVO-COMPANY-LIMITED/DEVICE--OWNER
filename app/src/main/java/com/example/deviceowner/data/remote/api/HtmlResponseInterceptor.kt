package com.microspace.payo.data.remote.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Detects when the server returns HTML instead of JSON (e.g. 404/500 error page).
 * Throws [ServerReturnedHtmlException] before Retrofit tries to parse the body,
 * so the app shows a clear message instead of ClassCastException.
 */
class HtmlResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val url = response.request.url
        // Only check API paths (e.g. api/devices/register/)
        if (!url.encodedPath.contains("api/")) return response

        val contentType = response.header("Content-Type") ?: ""
        if (contentType.contains("text/html", ignoreCase = true)) {
            val preview = response.peekBody(MAX_PEEK).string().take(MAX_PREVIEW)
            throw ServerReturnedHtmlException(response.code, preview)
        }
        val body = response.peekBody(MAX_PEEK).string()
        if (body.trimStart().startsWith("<") || body.contains("<html", ignoreCase = true)) {
            throw ServerReturnedHtmlException(response.code, body.take(MAX_PREVIEW))
        }
        return response
    }

    companion object {
        private const val MAX_PEEK = 64 * 1024L
        private const val MAX_PREVIEW = 2000
    }
}
