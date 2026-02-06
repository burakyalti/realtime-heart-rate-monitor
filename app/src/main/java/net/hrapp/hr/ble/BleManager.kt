package net.hrapp.hr.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import net.hrapp.hr.data.HeartRateData
import net.hrapp.hr.data.PreferencesManager
import java.util.UUID

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"

        // BLE UUIDs
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Reconnect settings
        private const val FAST_RECONNECT_DELAY_MS = 500L     // İlk denemeler için hızlı
        private const val INITIAL_RECONNECT_DELAY_MS = 2000L  // Scan için
        private const val MAX_RECONNECT_DELAY_MS = 60000L     // Max 1 dakika
        private const val FAST_RECONNECT_ATTEMPTS = 3         // İlk 3 deneme direkt bağlan

        // Battery read interval (5 dakika)
        private const val BATTERY_READ_INTERVAL_MS = 5 * 60 * 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val prefs = PreferencesManager(context)

    // Configurable target MAC address
    private val targetMac: String
        get() = prefs.deviceMac

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private var reconnectAttempt = 0
    private var batteryLevel: Int? = null
    private var isScanning = false
    private var isDiscoveryMode = false

    // Discovery scan timeout (15 seconds)
    private val discoveryTimeoutRunnable = Runnable { stopDeviceDiscovery() }

    // Callbacks
    var onHeartRateReceived: ((HeartRateData) -> Unit)? = null
    var onConnectionStateChanged: ((Boolean, String) -> Unit)? = null
    var onBatteryLevelReceived: ((Int) -> Unit)? = null
    var onScanningStateChanged: ((Boolean) -> Unit)? = null
    var onBluetoothStateChanged: ((Boolean) -> Unit)? = null
    var onDeviceDiscovered: ((ScannedDevice) -> Unit)? = null
    var onDiscoveryStateChanged: ((Boolean) -> Unit)? = null

    private var bluetoothReceiver: BroadcastReceiver? = null

    // Periodic battery read
    private val batteryReadRunnable = object : Runnable {
        override fun run() {
            bluetoothGatt?.let { gatt ->
                Log.d(TAG, "Periodic battery read...")
                readBatteryLevel(gatt)
            }
            handler.postDelayed(this, BATTERY_READ_INTERVAL_MS)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            Log.d(TAG, "Discovered: ${device.name} - ${device.address}")

            if (device.address.equals(targetMac, ignoreCase = true)) {
                Log.i(TAG, "Found TICKR! Stopping scan and connecting...")
                stopScan()
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            isScanning = false
            onScanningStateChanged?.invoke(false)
            scheduleReconnect()
        }
    }

    // Discovery mode scan callback - for finding nearby devices
    private val discoveryScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val scannedDevice = ScannedDevice(
                name = device.name ?: "Bilinmeyen Cihaz",
                address = device.address,
                rssi = result.rssi,
                hasHeartRateService = hasHeartRateService(result)
            )
            handler.post {
                onDeviceDiscovered?.invoke(scannedDevice)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Discovery scan failed with error: $errorCode")
            isDiscoveryMode = false
            handler.post {
                onDiscoveryStateChanged?.invoke(false)
            }
        }
    }

    private fun hasHeartRateService(result: ScanResult): Boolean {
        return result.scanRecord?.serviceUuids?.any {
            it.uuid == HEART_RATE_SERVICE_UUID
        } == true
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server")
                    reconnectAttempt = 0
                    handler.post {
                        onConnectionStateChanged?.invoke(true, "Bağlandı")
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "Disconnected from GATT server, status: $status")
                    stopPeriodicBatteryRead()
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    handler.post {
                        onConnectionStateChanged?.invoke(false, "Bağlantı kesildi")
                        scheduleReconnect()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                enableHeartRateNotifications(gatt)
            } else {
                Log.e(TAG, "Service discovery failed: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            when (characteristic.uuid) {
                HEART_RATE_MEASUREMENT_UUID -> {
                    // Raw data debug
                    val hexString = value.joinToString(" ") { String.format("%02X", it) }
                    val flags = if (value.isNotEmpty()) value[0].toInt() and 0xFF else 0
                    Log.d(TAG, "RAW: [$hexString] flags=0x${String.format("%02X", flags)} (contact_supported=${(flags and 0x02) != 0}, contact_detected=${(flags and 0x04) != 0})")

                    HeartRateParser.parse(value)?.let { parsed ->
                        val heartRateData = HeartRateParser.toHeartRateData(
                            parsed = parsed,
                            deviceId = gatt.device.address,
                            batteryLevel = batteryLevel
                        )
                        Log.d(TAG, "PARSED: HR=${heartRateData.heartRate}, Contact=${heartRateData.sensorContact}, RR=${parsed.rrIntervals}")
                        handler.post {
                            onHeartRateReceived?.invoke(heartRateData)
                        }
                    }
                }
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            characteristic.value?.let { value ->
                onCharacteristicChanged(gatt, characteristic, value)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_UUID) {
                if (value.isNotEmpty()) {
                    batteryLevel = value[0].toInt() and 0xFF
                    Log.d(TAG, "Battery Level: $batteryLevel%")
                    handler.post {
                        onBatteryLevelReceived?.invoke(batteryLevel!!)
                    }
                }
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            characteristic.value?.let { value ->
                onCharacteristicRead(gatt, characteristic, value, status)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor written successfully")
                // After enabling HR notifications, read battery level and start periodic reads
                readBatteryLevel(gatt)
                startPeriodicBatteryRead()
            } else {
                Log.e(TAG, "Descriptor write failed: $status")
            }
        }
    }

    private var isInitialized = false

    fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "BleManager already initialized")
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        // Register Bluetooth state receiver
        registerBluetoothReceiver()

        isInitialized = true
        // Reset reconnect attempts on fresh initialization
        reconnectAttempt = 0

        Log.d(TAG, "BleManager initialized, Bluetooth enabled: ${isBluetoothEnabled()}")
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    private fun registerBluetoothReceiver() {
        // Unregister existing receiver if any
        if (bluetoothReceiver != null) {
            try {
                context.unregisterReceiver(bluetoothReceiver)
            } catch (e: Exception) {
                // Ignore if not registered
            }
        }

        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_ON -> {
                            Log.i(TAG, "Bluetooth turned ON")
                            handler.post {
                                onBluetoothStateChanged?.invoke(true)
                            }
                            // Auto-start scan when Bluetooth is turned on
                            startScan()
                        }
                        BluetoothAdapter.STATE_OFF -> {
                            Log.w(TAG, "Bluetooth turned OFF")
                            stopScan()
                            bluetoothGatt?.close()
                            bluetoothGatt = null
                            handler.post {
                                onBluetoothStateChanged?.invoke(false)
                                onConnectionStateChanged?.invoke(false, "Bluetooth kapalı")
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(bluetoothReceiver, filter)
        }
    }

    fun startScan() {
        // MAC adresi sabit olduğu için scan yapmaya gerek yok
        // Direkt bağlantı kur
        if (bluetoothGatt != null) {
            Log.d(TAG, "Already connected or connecting")
            return
        }

        reconnectAttempt = 0
        directConnect()
    }

    fun stopScan() {
        if (!isScanning) return

        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
        isScanning = false
        onScanningStateChanged?.invoke(false)
        Log.d(TAG, "BLE scan stopped")
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to ${device.address}...")
        onConnectionStateChanged?.invoke(false, "Bağlanıyor...")
        bluetoothGatt = device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun enableHeartRateNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(HEART_RATE_SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "Heart Rate service not found")
            return
        }

        val characteristic = service.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
        if (characteristic == null) {
            Log.e(TAG, "Heart Rate Measurement characteristic not found")
            return
        }

        gatt.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
            Log.d(TAG, "Heart Rate notification enabled")
        }
    }

    private fun readBatteryLevel(gatt: BluetoothGatt) {
        val service = gatt.getService(BATTERY_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(BATTERY_LEVEL_UUID)
        if (characteristic != null) {
            gatt.readCharacteristic(characteristic)
        }
    }

    private fun startPeriodicBatteryRead() {
        handler.removeCallbacks(batteryReadRunnable)
        handler.postDelayed(batteryReadRunnable, BATTERY_READ_INTERVAL_MS)
        Log.d(TAG, "Periodic battery read started (every ${BATTERY_READ_INTERVAL_MS / 60000} min)")
    }

    private fun stopPeriodicBatteryRead() {
        handler.removeCallbacks(batteryReadRunnable)
        Log.d(TAG, "Periodic battery read stopped")
    }

    fun disconnect() {
        stopScan()
        stopPeriodicBatteryRead()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        handler.removeCallbacksAndMessages(null)
    }

    fun isConnected(): Boolean = bluetoothGatt != null

    /**
     * Start device discovery mode to find nearby BLE devices
     * This is separate from the normal scan which looks for a specific device
     */
    fun startDeviceDiscovery() {
        if (!isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth disabled, cannot start discovery")
            return
        }

        if (isDiscoveryMode) {
            Log.d(TAG, "Discovery already in progress")
            return
        }

        Log.i(TAG, "Starting device discovery...")
        isDiscoveryMode = true
        onDiscoveryStateChanged?.invoke(true)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bluetoothLeScanner?.startScan(null, settings, discoveryScanCallback)

            // Auto-stop after 15 seconds
            handler.removeCallbacks(discoveryTimeoutRunnable)
            handler.postDelayed(discoveryTimeoutRunnable, 15000L)

            Log.d(TAG, "Device discovery started (15s timeout)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery: ${e.message}")
            isDiscoveryMode = false
            onDiscoveryStateChanged?.invoke(false)
        }
    }

    /**
     * Stop device discovery mode
     */
    fun stopDeviceDiscovery() {
        if (!isDiscoveryMode) return

        Log.d(TAG, "Stopping device discovery...")
        handler.removeCallbacks(discoveryTimeoutRunnable)

        try {
            bluetoothLeScanner?.stopScan(discoveryScanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery: ${e.message}")
        }

        isDiscoveryMode = false
        onDiscoveryStateChanged?.invoke(false)
        Log.i(TAG, "Device discovery stopped")
    }

    fun isDiscovering(): Boolean = isDiscoveryMode

    /**
     * Zorla yeniden bağlantı kur (watchdog timer için)
     * Mevcut bağlantıyı koparıp yeniden bağlanır
     */
    fun forceReconnect() {
        Log.w(TAG, "Force reconnect requested")
        handler.removeCallbacksAndMessages(null)
        stopPeriodicBatteryRead()

        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        bluetoothGatt = null

        // Hemen yeniden bağlan
        reconnectAttempt = 0
        handler.postDelayed({
            directConnect()
        }, 500L)
    }

    private fun scheduleReconnect() {
        reconnectAttempt++
        val delay = calculateReconnectDelay()

        Log.d(TAG, "Scheduling direct reconnect in ${delay}ms (attempt $reconnectAttempt)")
        onConnectionStateChanged?.invoke(false, "Bağlanıyor...")

        handler.postDelayed({
            directConnect()
        }, delay)
    }

    /**
     * MAC adresi bilinen cihaza direkt bağlantı (scan yapmadan)
     * Çok daha hızlı reconnection sağlar
     */
    private fun directConnect() {
        if (!isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth disabled, cannot connect")
            onConnectionStateChanged?.invoke(false, "Bluetooth kapalı")
            return
        }

        try {
            val device = bluetoothAdapter?.getRemoteDevice(targetMac)
            if (device != null) {
                Log.d(TAG, "Direct connecting to $targetMac (attempt $reconnectAttempt)...")
                onConnectionStateChanged?.invoke(false, "Bağlanıyor...")
                // autoConnect=false: hemen bağlanmayı dene (daha hızlı)
                bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                Log.e(TAG, "Could not get remote device")
                scheduleReconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct connect failed: ${e.message}")
            scheduleReconnect()
        }
    }

    private fun calculateReconnectDelay(): Long {
        // İlk 3 deneme çok hızlı (500ms), sonra yavaşça artar (max 1 dk)
        return when {
            reconnectAttempt <= FAST_RECONNECT_ATTEMPTS -> FAST_RECONNECT_DELAY_MS
            else -> {
                val slowAttempt = reconnectAttempt - FAST_RECONNECT_ATTEMPTS
                val delay = INITIAL_RECONNECT_DELAY_MS * (1L shl minOf(slowAttempt, 4))
                minOf(delay, MAX_RECONNECT_DELAY_MS)
            }
        }
    }

    fun destroy() {
        disconnect()

        // Unregister Bluetooth receiver
        try {
            bluetoothReceiver?.let {
                context.unregisterReceiver(it)
                bluetoothReceiver = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering Bluetooth receiver", e)
        }

        // Reset state for potential re-initialization
        isInitialized = false
        reconnectAttempt = 0
        bluetoothAdapter = null
        bluetoothLeScanner = null

        Log.d(TAG, "BleManager destroyed")
    }
}
