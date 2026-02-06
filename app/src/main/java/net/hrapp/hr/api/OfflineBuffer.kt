package net.hrapp.hr.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.hrapp.hr.data.HeartRateData
import java.io.File

class OfflineBuffer(private val context: Context) {

    companion object {
        private const val TAG = "OfflineBuffer"
        private const val FILE_NAME = "offline_buffer.json"
        private const val MAX_RECORDS = 1000
    }

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val bufferFile: File
        get() = File(context.filesDir, FILE_NAME)

    suspend fun add(data: HeartRateData) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val records = loadAllInternal().toMutableList()
                records.add(data)

                // FIFO: Eski kayıtları sil
                while (records.size > MAX_RECORDS) {
                    records.removeAt(0)
                }

                saveAllInternal(records)
                Log.d(TAG, "Added to offline buffer. Total: ${records.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add to buffer", e)
            }
        }
    }

    suspend fun flush(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val records = loadAllInternal()
                if (records.isEmpty()) {
                    Log.d(TAG, "Buffer is empty, nothing to flush")
                    return@withContext 0
                }

                Log.d(TAG, "Flushing ${records.size} records...")

                val failed = mutableListOf<HeartRateData>()
                var successCount = 0

                for (record in records) {
                    val result = ApiClient.sendHeartRate(record)
                    if (result.isSuccess) {
                        successCount++
                    } else {
                        failed.add(record)
                    }
                }

                saveAllInternal(failed)
                Log.d(TAG, "Flush complete. Success: $successCount, Failed: ${failed.size}")
                successCount
            } catch (e: Exception) {
                Log.e(TAG, "Failed to flush buffer", e)
                0
            }
        }
    }

    suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadAllInternal().size
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                bufferFile.delete()
                Log.d(TAG, "Buffer cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear buffer", e)
            }
        }
    }

    private fun loadAllInternal(): List<HeartRateData> {
        return try {
            if (!bufferFile.exists()) {
                emptyList()
            } else {
                val content = bufferFile.readText()
                if (content.isBlank()) {
                    emptyList()
                } else {
                    json.decodeFromString<List<HeartRateData>>(content)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load buffer", e)
            emptyList()
        }
    }

    private fun saveAllInternal(records: List<HeartRateData>) {
        try {
            val content = json.encodeToString(records)
            bufferFile.writeText(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save buffer", e)
        }
    }
}
