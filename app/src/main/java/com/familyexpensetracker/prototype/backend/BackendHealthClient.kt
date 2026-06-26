package com.familyexpensetracker.prototype.backend

import java.net.HttpURLConnection
import java.net.URL

class BackendHealthClient {
    fun check(baseUrl: String): Result<String> =
        runCatching {
            val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
            require(normalizedBaseUrl.startsWith("http://") || normalizedBaseUrl.startsWith("https://")) {
                "Backend URL must start with http:// or https://"
            }

            val connection = (URL("$normalizedBaseUrl/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3_000
                readTimeout = 3_000
            }

            connection.inputStream.bufferedReader().use { reader ->
                val body = reader.readText()
                if (connection.responseCode !in 200..299) {
                    error("Backend returned HTTP ${connection.responseCode}")
                }
                body
            }
        }
}
