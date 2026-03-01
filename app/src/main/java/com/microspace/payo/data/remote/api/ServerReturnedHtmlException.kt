package com.microspace.payo.data.remote.api

import java.io.IOException

/**
 * Thrown when the server responds with HTML (e.g. 404/500 error page) instead of JSON.
 * This is detected before Retrofit tries to parse the body, avoiding ClassCastException.
 * @param httpCode HTTP status code (e.g. 200, 404, 500)
 * @param bodyPreview First part of the response body (often contains the real error message)
 */
class ServerReturnedHtmlException(
    val httpCode: Int,
    val bodyPreview: String,
    message: String = "Server returned HTML instead of JSON (HTTP $httpCode). Check BASE_URL and that the API endpoint is correct."
) : IOException(message)




