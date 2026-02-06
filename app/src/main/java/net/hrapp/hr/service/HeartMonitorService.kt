package net.hrapp.hr.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.hrapp.hr.HeartMonitorApp
import net.hrapp.hr.MainActivity
import net.hrapp.hr.R
import net.hrapp.hr.api.ApiClient
import net.hrapp.hr.api.OfflineBuffer
import net.hrapp.hr.alert.AlertManager
import net.hrapp.hr.ble.BleManager
import net.hrapp.hr.ble.SignalQualityDetector
import net.hrapp.hr.data.HeartRateData
import net.hrapp.hr.data.PreferencesManager

class HeartMonitorService : Service() {

    companion object {
        private const val TAG = "HeartMonitorService"
        private const val NOTIFICATION_ID = 1001
        private const val WAKELOCK_TAG = "HeartMonitor::ServiceWakeLock"

        // Service kontrolü için Intent action'ları
        const val ACTION_START = "net.hrapp.hr.action.START"
        const val ACTION_STOP = "net.hrapp.hr.action.STOP"
        const val ACTION_REQUEST_STATE = "net.hrapp.hr.action.REQUEST_STATE"

        // Broadcast action'ları (UI güncellemesi için)
        const val ACTION_HEART_RATE_UPDATE = "net.hrapp.hr.action.HEART_RATE_UPDATE"
        const val ACTION_CONNECTION_STATE = "net.hrapp.hr.action.CONNECTION_STATE"
        const val ACTION_OFFLINE_COUNT = "net.hrapp.hr.action.OFFLINE_COUNT"
        const val ACTION_DEVICE_INFO = "net.hrapp.hr.action.DEVICE_INFO"

        // Broadcast extra'ları
        const val EXTRA_HEART_RATE = "extra_heart_rate"
        const val EXTRA_CONNECTION_STATE = "extra_connection_state"
        const val EXTRA_OFFLINE_COUNT = "extra_offline_count"
        const val EXTRA_BATTERY_LEVEL = "extra_battery_level"
        const val EXTRA_SENSOR_CONTACT = "extra_sensor_contact"
        const val EXTRA_RR_INTERVALS = "extra_rr_intervals"
        const val EXTRA_SIGNAL_QUALITY = "extra_signal_quality"
        const val EXTRA_SENT_COUNT = "extra_sent_count"

        // Offline buffer flush interval (5 dakika)
        private const val BUFFER_FLUSH_INTERVAL_MS = 5 * 60 * 1000L

        // Watchdog timer - veri gelmezse reconnect
        private const val WATCHDOG_TIMEOUT_MS = 10_000L

        // Sensor contact tolerance - kaç ardışık false okuma sonrası "temas yok" sayılsın
        private const val SENSOR_CONTACT_TOLERANCE = 3
    }

    private lateinit var bleManager: BleManager
    private lateinit var offlineBuffer: OfflineBuffer
    private lateinit var signalQualityDetector: SignalQualityDetector
    private lateinit var alertManager: AlertManager
    private lateinit var prefs: PreferencesManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var bufferFlushJob: Job? = null
    private var watchdogJob: Job? = null

    private var currentHeartRate: Int = 0
    private var isConnected: Boolean = false
    private var sentCount: Int = 0
    private var errorCount: Int = 0
    private var isMonitoringStarted: Boolean = false
    private var currentBatteryLevel: Int? = null
    private var currentSensorContact: Boolean? = null

    // Watchdog için son veri alım zamanı
    private var lastDataReceivedTime: Long = 0L

    // Sensor contact tolerance - ardışık "temas yok" sayacı
    private var noContactCounter: Int = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        bleManager = BleManager(this)
        offlineBuffer = OfflineBuffer(this)
        signalQualityDetector = SignalQualityDetector()
        alertManager = AlertManager(this)
        prefs = PreferencesManager(this)

        setupBleCallbacks()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand, action: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REQUEST_STATE -> {
                // UI durumu istedi, mevcut durumu broadcast et
                broadcastCurrentState()
                return START_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, createNotification("Bağlanılıyor..."))
                startMonitoring()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed, scheduling restart...")

        val restartIntent = Intent(this, HeartMonitorService::class.java).apply {
            action = ACTION_START
        }

        val pendingIntent = PendingIntent.getService(
            this,
            1,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")

        isMonitoringStarted = false
        stopWatchdogTimer()
        bufferFlushJob?.cancel()
        bufferFlushJob = null
        serviceScope.cancel()

        bleManager.destroy()
        releaseWakeLock()

        super.onDestroy()
    }

    private fun setupBleCallbacks() {
        bleManager.onHeartRateReceived = { data ->
            // Watchdog timer'ı sıfırla - veri geldi
            lastDataReceivedTime = System.currentTimeMillis()

            // Sensor contact tolerance kontrolü
            val rawContact = data.sensorContact ?: true
            val shouldProcess: Boolean

            if (!rawContact) {
                noContactCounter++
                Log.d(TAG, "No contact detected, counter: $noContactCounter/$SENSOR_CONTACT_TOLERANCE")

                // Tolerance aşılmadıysa, veriyi işleme (tolerans içinde)
                shouldProcess = noContactCounter < SENSOR_CONTACT_TOLERANCE
                if (!shouldProcess) {
                    Log.d(TAG, "Contact tolerance exceeded - treating as no contact")
                }
            } else {
                // Temas var - sayacı sıfırla
                noContactCounter = 0
                shouldProcess = true
            }

            // Tolerance aşılmadıysa veriyi işle
            val hasContact = rawContact || (noContactCounter < SENSOR_CONTACT_TOLERANCE)
            currentSensorContact = hasContact

            if (shouldProcess) {
                // SignalQualityDetector ile sinyal kalitesini değerlendir
                val qualityResult = signalQualityDetector.addReading(data.heartRate)

                Log.d(TAG, "Quality: ${qualityResult.quality}, Stage: ${qualityResult.stage}, " +
                    "Reason: ${qualityResult.reason}, IsNoise: ${qualityResult.isNoise}")

                // Kalite kabul edilebilir mi? (gürültü değilse)
                if (!qualityResult.isNoise) {
                    currentHeartRate = data.heartRate
                    val qualityPercent = (qualityResult.quality * 100).toInt()
                    updateNotification("Nabız: ${data.heartRate} BPM (Q:$qualityPercent%)")
                    broadcastHeartRate(data.heartRate, true, data.rrIntervals, qualityResult.quality)

                    // Threshold kontrolü ve alert
                    alertManager.checkAndAlert(
                        heartRate = data.heartRate,
                        minThreshold = prefs.minHeartRateThreshold,
                        maxThreshold = prefs.maxHeartRateThreshold
                    )

                    // API'ye gönder
                    serviceScope.launch {
                        val result = ApiClient.sendHeartRate(data)
                        if (result.isSuccess) {
                            sentCount++
                            Log.d(TAG, "Sent: $sentCount, Errors: $errorCount")
                        } else {
                            errorCount++
                            offlineBuffer.add(data)
                            broadcastOfflineCount(offlineBuffer.getCount())
                            Log.w(TAG, "Failed to send, added to offline buffer")
                        }
                    }
                } else {
                    // Gürültü tespit edildi - veriyi gönderme
                    Log.w(TAG, "Noise detected - HR ignored: ${data.heartRate}, Reason: ${qualityResult.reason}")
                    updateNotification("Sinyal kalitesi düşük")
                    broadcastHeartRate(0, false, emptyList(), 0f)
                }
            } else {
                // Tolerance aşıldı - temas yok durumu
                Log.w(TAG, "No contact - tolerance exceeded ($noContactCounter)")
                updateNotification("Sensör teması yok")
                broadcastHeartRate(0, false, emptyList(), 0f)
            }
        }

        bleManager.onConnectionStateChanged = { connected, status ->
            isConnected = connected
            updateNotification(if (connected) "Nabız: $currentHeartRate BPM" else status)
            broadcastConnectionState(if (connected) "CONNECTED" else status)

            if (connected) {
                // Bağlandı - watchdog timer'ı başlat
                lastDataReceivedTime = System.currentTimeMillis()
                noContactCounter = 0
                startWatchdogTimer()
            } else {
                // Bağlantı kesildi - watchdog timer'ı durdur
                stopWatchdogTimer()
            }
        }

        bleManager.onBatteryLevelReceived = { level ->
            Log.d(TAG, "Battery: $level%")
            currentBatteryLevel = level
            broadcastDeviceInfo()
        }

        bleManager.onScanningStateChanged = { scanning ->
            if (scanning) {
                broadcastConnectionState("SCANNING")
            }
        }

        bleManager.onBluetoothStateChanged = { enabled ->
            if (!enabled) {
                broadcastConnectionState("BLUETOOTH_OFF")
                updateNotification("Bluetooth kapalı")
            }
        }
    }

    private var currentRrIntervals: List<Int> = emptyList()
    private var currentSignalQuality: Float = 0f

    private fun broadcastHeartRate(heartRate: Int, sensorContact: Boolean? = null, rrIntervals: List<Int>? = null, signalQuality: Float? = null) {
        rrIntervals?.let { currentRrIntervals = it }
        signalQuality?.let { currentSignalQuality = it }
        sendBroadcast(Intent(ACTION_HEART_RATE_UPDATE).apply {
            putExtra(EXTRA_HEART_RATE, heartRate)
            sensorContact?.let { putExtra(EXTRA_SENSOR_CONTACT, it) }
            putExtra(EXTRA_RR_INTERVALS, currentRrIntervals.toIntArray())
            putExtra(EXTRA_SIGNAL_QUALITY, currentSignalQuality)
            putExtra(EXTRA_SENT_COUNT, sentCount)
            setPackage(packageName)
        })
    }

    private fun broadcastDeviceInfo() {
        sendBroadcast(Intent(ACTION_DEVICE_INFO).apply {
            currentBatteryLevel?.let { putExtra(EXTRA_BATTERY_LEVEL, it) }
            currentSensorContact?.let { putExtra(EXTRA_SENSOR_CONTACT, it) }
            setPackage(packageName)
        })
    }

    private fun broadcastConnectionState(state: String) {
        sendBroadcast(Intent(ACTION_CONNECTION_STATE).apply {
            putExtra(EXTRA_CONNECTION_STATE, state)
            setPackage(packageName)
        })
    }

    private fun broadcastOfflineCount(count: Int) {
        sendBroadcast(Intent(ACTION_OFFLINE_COUNT).apply {
            putExtra(EXTRA_OFFLINE_COUNT, count)
            setPackage(packageName)
        })
    }

    private fun broadcastCurrentState() {
        Log.d(TAG, "Broadcasting current state: connected=$isConnected, hr=$currentHeartRate, battery=$currentBatteryLevel")

        // Broadcast connection state
        val connectionState = when {
            !bleManager.isBluetoothEnabled() -> "BLUETOOTH_OFF"
            isConnected -> "CONNECTED"
            else -> "SCANNING"
        }
        broadcastConnectionState(connectionState)

        // Broadcast heart rate if connected
        if (isConnected && currentHeartRate > 0) {
            broadcastHeartRate(currentHeartRate, currentSensorContact, currentRrIntervals, currentSignalQuality)
        }

        // Broadcast device info (battery, sensor contact)
        broadcastDeviceInfo()

        // Broadcast offline count
        serviceScope.launch {
            broadcastOfflineCount(offlineBuffer.getCount())
        }
    }

    private fun startWatchdogTimer() {
        stopWatchdogTimer()

        watchdogJob = serviceScope.launch {
            Log.d(TAG, "Watchdog timer started (timeout: ${WATCHDOG_TIMEOUT_MS}ms)")
            while (isActive && isConnected) {
                delay(WATCHDOG_TIMEOUT_MS / 2) // Her 5 saniyede kontrol et

                val timeSinceLastData = System.currentTimeMillis() - lastDataReceivedTime
                Log.v(TAG, "Watchdog check: ${timeSinceLastData}ms since last data")

                if (timeSinceLastData > WATCHDOG_TIMEOUT_MS) {
                    Log.w(TAG, "Watchdog timeout! No data for ${timeSinceLastData}ms, forcing reconnect...")
                    updateNotification("Bağlantı yenileniyor...")

                    // Force reconnect on main thread
                    withContext(Dispatchers.Main) {
                        bleManager.forceReconnect()
                    }
                    break
                }
            }
        }
    }

    private fun stopWatchdogTimer() {
        watchdogJob?.cancel()
        watchdogJob = null
        Log.d(TAG, "Watchdog timer stopped")
    }

    private fun startMonitoring() {
        if (isMonitoringStarted) {
            Log.d(TAG, "Monitoring already started, skipping...")
            return
        }

        // Diğer servisi durdur (çakışma önleme)
        try {
            stopService(Intent(this, ClientMonitorService::class.java))
        } catch (e: Exception) {
            Log.d(TAG, "ClientMonitorService already stopped")
        }

        Log.d(TAG, "Starting monitoring...")
        isMonitoringStarted = true

        bleManager.initialize()

        // Broadcast initial offline count
        serviceScope.launch {
            broadcastOfflineCount(offlineBuffer.getCount())
        }

        // Check if Bluetooth is enabled before scanning
        if (!bleManager.isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth is disabled")
            broadcastConnectionState("BLUETOOTH_OFF")
            updateNotification("Bluetooth kapalı")
            // BleManager will auto-start scan when Bluetooth is turned on
        } else {
            // Broadcast initial state as scanning
            broadcastConnectionState("SCANNING")
        }

        bleManager.startScan()

        // Offline buffer flush job
        if (bufferFlushJob == null || bufferFlushJob?.isActive != true) {
            bufferFlushJob = serviceScope.launch {
                while (isActive) {
                    delay(BUFFER_FLUSH_INTERVAL_MS)
                    Log.d(TAG, "Flushing offline buffer...")
                    offlineBuffer.flush()
                    broadcastOfflineCount(offlineBuffer.getCount())
                }
            }
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, HeartMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, HeartMonitorApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Durdur", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 saat
        }
        Log.d(TAG, "WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }
}
