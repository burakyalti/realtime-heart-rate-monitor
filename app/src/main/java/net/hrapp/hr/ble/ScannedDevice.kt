package net.hrapp.hr.ble

/**
 * Represents a scanned BLE device
 */
data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val hasHeartRateService: Boolean
)
