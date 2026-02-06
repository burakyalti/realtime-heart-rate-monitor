package net.hrapp.hr.ble

import net.hrapp.hr.data.HeartRateData

/**
 * BLE Heart Rate Measurement Characteristic Parser
 * Bluetooth SIG Heart Rate Profile specification'a göre parse eder
 */
object HeartRateParser {

    data class ParsedHeartRate(
        val heartRate: Int,
        val rrIntervals: List<Int>,
        val sensorContact: Boolean,
        val sensorContactSupported: Boolean
    )

    /**
     * Heart Rate Measurement characteristic değerini parse eder
     *
     * Flags byte (bit field):
     * - Bit 0: Heart Rate Value Format (0 = UINT8, 1 = UINT16)
     * - Bit 1-2: Sensor Contact Status
     * - Bit 3: Energy Expended Status (0 = not present, 1 = present)
     * - Bit 4: RR-Interval (0 = not present, 1 = present)
     */
    fun parse(data: ByteArray): ParsedHeartRate? {
        if (data.isEmpty()) return null

        val flags = data[0].toInt() and 0xFF
        val is16Bit = (flags and 0x01) != 0
        val sensorContactSupported = (flags and 0x02) != 0
        val sensorContactBit = (flags and 0x04) != 0
        // Sensor contact: only valid when supported, otherwise assume true
        val sensorContact = if (sensorContactSupported) sensorContactBit else true
        val hasEnergyExpended = (flags and 0x08) != 0
        val hasRRInterval = (flags and 0x10) != 0

        var index = 1

        // Heart Rate değerini oku
        val heartRate: Int
        if (is16Bit) {
            if (data.size < index + 2) return null
            heartRate = (data[index].toInt() and 0xFF) or
                    ((data[index + 1].toInt() and 0xFF) shl 8)
            index += 2
        } else {
            if (data.size < index + 1) return null
            heartRate = data[index].toInt() and 0xFF
            index += 1
        }

        // Energy Expended alanını atla (varsa)
        if (hasEnergyExpended) {
            index += 2
        }

        // RR-Interval değerlerini oku
        val rrIntervals = mutableListOf<Int>()
        if (hasRRInterval) {
            while (index + 1 < data.size) {
                val rrRaw = (data[index].toInt() and 0xFF) or
                        ((data[index + 1].toInt() and 0xFF) shl 8)
                // 1/1024 saniyeden milisaniyeye çevir
                val rrMs = rrRaw * 1000 / 1024
                rrIntervals.add(rrMs)
                index += 2
            }
        }

        return ParsedHeartRate(
            heartRate = heartRate,
            rrIntervals = rrIntervals,
            sensorContact = sensorContact,
            sensorContactSupported = sensorContactSupported
        )
    }

    /**
     * ParsedHeartRate'i HeartRateData'ya dönüştürür
     */
    fun toHeartRateData(
        parsed: ParsedHeartRate,
        deviceId: String,
        batteryLevel: Int? = null
    ): HeartRateData {
        return HeartRateData(
            deviceId = deviceId,
            heartRate = parsed.heartRate,
            rrIntervals = parsed.rrIntervals,
            sensorContact = parsed.sensorContact,
            batteryLevel = batteryLevel
        )
    }
}
