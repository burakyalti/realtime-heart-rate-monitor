package net.hrapp.hr.api

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import net.hrapp.hr.data.HeartRateData

object ApiClient {

    private const val TAG = "ApiClient"
    private const val DEFAULT_BASE_URL = "https://example.com/hr/api"
    private const val DEFAULT_API_KEY = "hr_api_key_2024_secure"

    // Configurable base URL
    private var baseUrl: String = DEFAULT_BASE_URL

    // Configurable API key
    private var apiKey: String = DEFAULT_API_KEY

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
        Log.d(TAG, "Base URL set to: $baseUrl")
    }

    fun getBaseUrl(): String = baseUrl

    fun setApiKey(key: String) {
        apiKey = key.trim()
        Log.d(TAG, "API Key updated")
    }

    fun getApiKey(): String = apiKey

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }

        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * Client modunda son nabız değerini almak için
     */
    suspend fun getLatestHeartRate(): Result<Int?> {
        return try {
            val response = client.get("$baseUrl/latest.php") {
                header("X-API-Key", apiKey)
            }
            val responseBody = response.bodyAsText()
            Log.d(TAG, "Latest HR response: ${response.status} - $responseBody")

            if (response.status.isSuccess()) {
                // Response format: {"heart_rate": 75, "timestamp": "..."}
                val hrMatch = Regex(""""heart_rate":\s*(\d+)""").find(responseBody)
                val heartRate = hrMatch?.groupValues?.get(1)?.toIntOrNull()
                Log.d(TAG, "Parsed heart rate: $heartRate")
                Result.success(heartRate)
            } else {
                Log.e(TAG, "Failed to get latest HR: ${response.status}")
                Result.failure(Exception("Server error: ${response.status}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting latest HR: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun sendHeartRate(data: HeartRateData): Result<Boolean> {
        return try {
            Log.d(TAG, "Sending HR: ${data.heartRate} BPM, device: ${data.deviceId}, " +
                "rr: ${data.rrIntervals}, contact: ${data.sensorContact}, " +
                "battery: ${data.batteryLevel}, timestamp: ${data.recordedAt}")

            val response = client.post("$baseUrl/log.php") {
                header("X-API-Key", apiKey)
                setBody(data)
            }

            val responseBody = response.bodyAsText()
            Log.d(TAG, "Response: ${response.status} - $responseBody")

            if (response.status.isSuccess()) {
                Log.i(TAG, "✓ Sent: ${data.heartRate} BPM @ ${data.recordedAt}")
                Result.success(true)
            } else {
                Log.e(TAG, "✗ Server error: ${response.status} - $responseBody")
                Result.failure(Exception("Server error: ${response.status}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to send: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
    }
}
