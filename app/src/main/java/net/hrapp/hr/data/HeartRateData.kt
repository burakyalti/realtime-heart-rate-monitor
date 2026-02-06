package net.hrapp.hr.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HeartRateData(
    @SerialName("device_id")
    val deviceId: String,

    @SerialName("heart_rate")
    val heartRate: Int,

    @SerialName("rr_intervals")
    val rrIntervals: List<Int>,

    @SerialName("sensor_contact")
    val sensorContact: Boolean,

    @SerialName("battery_level")
    val batteryLevel: Int? = null,

    @SerialName("recorded_at")
    val recordedAt: Long = System.currentTimeMillis()  // Unix timestamp (milisaniye)
)

@Serializable
data class ApiResponse(
    val status: String,
    val message: String? = null
)
