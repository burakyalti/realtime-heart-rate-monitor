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
import net.hrapp.hr.HeartMonitorApp
import net.hrapp.hr.MainActivity
import net.hrapp.hr.R
import net.hrapp.hr.alert.AlertManager
import net.hrapp.hr.api.ApiClient
import net.hrapp.hr.data.PreferencesManager

/**
 * Client modunda çalışan hafif servis.
 * API'den son nabız değerini periyodik olarak alır ve threshold kontrolü yapar.
 */
class ClientMonitorService : Service() {

    companion object {
        private const val TAG = "ClientMonitorService"
        private const val NOTIFICATION_ID = 2002
        private const val WAKELOCK_TAG = "HeartMonitor::ClientWakeLock"

        // Service kontrolü için Intent action'ları
        const val ACTION_START = "net.hrapp.hr.action.CLIENT_START"
        const val ACTION_STOP = "net.hrapp.hr.action.CLIENT_STOP"

        // Polling interval (5 saniye)
        private const val POLL_INTERVAL_MS = 5_000L
    }

    private lateinit var alertManager: AlertManager
    private lateinit var prefs: PreferencesManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pollingJob: Job? = null
    private var isMonitoringStarted: Boolean = false
    private var lastHeartRate: Int? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        alertManager = AlertManager(this)
        prefs = PreferencesManager(this)

        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand, action: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, createNotification(getString(R.string.client_service_starting)))
                startMonitoring()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed, scheduling restart...")

        val restartIntent = Intent(this, ClientMonitorService::class.java).apply {
            action = ACTION_START
        }

        val pendingIntent = PendingIntent.getService(
            this,
            2,
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
        pollingJob?.cancel()
        pollingJob = null
        serviceScope.cancel()

        releaseWakeLock()

        super.onDestroy()
    }

    private fun startMonitoring() {
        if (isMonitoringStarted) {
            Log.d(TAG, "Monitoring already started, skipping...")
            return
        }

        // Diğer servisi durdur (çakışma önleme)
        try {
            stopService(Intent(this, HeartMonitorService::class.java))
        } catch (e: Exception) {
            Log.d(TAG, "HeartMonitorService already stopped")
        }

        // Initialize ApiClient with saved URL
        ApiClient.setBaseUrl(prefs.apiUrl)

        Log.d(TAG, "Starting client monitoring with URL: ${ApiClient.getBaseUrl()}")
        isMonitoringStarted = true

        // Start polling job
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    val result = ApiClient.getLatestHeartRate()
                    result.onSuccess { heartRate ->
                        if (heartRate != null && heartRate > 0) {
                            lastHeartRate = heartRate
                            Log.d(TAG, "Got heart rate: $heartRate BPM")

                            updateNotification(getString(R.string.client_service_hr, heartRate))

                            // Check thresholds and alert
                            alertManager.checkAndAlert(
                                heartRate = heartRate,
                                minThreshold = prefs.minHeartRateThreshold,
                                maxThreshold = prefs.maxHeartRateThreshold
                            )
                        } else {
                            Log.d(TAG, "No heart rate data available")
                            updateNotification(getString(R.string.client_service_waiting))
                        }
                    }.onFailure { e ->
                        Log.e(TAG, "Failed to get heart rate: ${e.message}")
                        updateNotification(getString(R.string.client_service_error))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}", e)
                }

                delay(POLL_INTERVAL_MS)
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
            Intent(this, ClientMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, HeartMonitorApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.client_service_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.dashboard_btn_stop), stopIntent)
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
