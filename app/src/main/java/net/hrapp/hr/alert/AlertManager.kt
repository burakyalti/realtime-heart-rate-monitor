package net.hrapp.hr.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import net.hrapp.hr.MainActivity
import net.hrapp.hr.R
import net.hrapp.hr.data.PreferencesManager

class AlertManager(private val context: Context) {

    enum class ConnectivityAlertType {
        API_UNREACHABLE,     // API URL'e erişilemedi
        BLE_DISCONNECTED,    // BLE bağlantısı kesildi
        DATA_STALE           // Güncel veri gelmiyor (client modda)
    }

    companion object {
        private const val TAG = "AlertManager"
        const val ALERT_CHANNEL_ID = "heart_rate_alert_channel"
        private const val ALERT_NOTIFICATION_ID = 2001
        private const val CONNECTIVITY_NOTIFICATION_ID = 2003
        private const val MAX_BUFFER_SIZE = 120
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefs = PreferencesManager(context)

    private var lastLowAlertTime = 0L
    private var lastHighAlertTime = 0L

    // İletişim kaybı cooldown timer'ları
    private var lastApiAlertTime = 0L
    private var lastBleAlertTime = 0L
    private var lastStaleDataAlertTime = 0L

    // Zaman penceresi için timestamped okuma buffer'ı
    private data class TimestampedReading(val heartRate: Int, val timestamp: Long)
    private val readingBuffer = mutableListOf<TimestampedReading>()

    init {
        createAlertChannel()
    }

    private fun createAlertChannel() {
        val soundUri = getAlertSoundUri()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            context.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alert_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            setSound(soundUri, audioAttributes)
            setBypassDnd(true)
        }

        notificationManager.createNotificationChannel(channel)
    }

    fun updateAlertSound() {
        createAlertChannel()
    }

    fun checkAndAlert(heartRate: Int, minThreshold: Int, maxThreshold: Int) {
        if (!prefs.alertsEnabled) return

        val now = System.currentTimeMillis()
        val windowSeconds = prefs.alertWindowSeconds
        val minExceedCount = prefs.alertMinExceedCount
        val cooldownMs = prefs.alertCooldownMinutes * 60 * 1000L

        Log.d(TAG, "checkAndAlert: HR=$heartRate, thresholds=[$minThreshold-$maxThreshold], " +
            "window=${windowSeconds}s, minExceed=$minExceedCount, cooldown=${prefs.alertCooldownMinutes}min")

        if (windowSeconds == 0) {
            // Anlık kontrol - tek okuma ile karar ver
            if (heartRate < minThreshold && now - lastLowAlertTime > cooldownMs) {
                triggerAlert(
                    title = context.getString(R.string.alert_low_title),
                    message = context.getString(R.string.alert_low_message, heartRate, minThreshold),
                    isLow = true
                )
                lastLowAlertTime = now
            } else if (heartRate > maxThreshold && now - lastHighAlertTime > cooldownMs) {
                triggerAlert(
                    title = context.getString(R.string.alert_high_title),
                    message = context.getString(R.string.alert_high_message, heartRate, maxThreshold),
                    isLow = false
                )
                lastHighAlertTime = now
            }
        } else {
            // Pencereli kontrol
            // 1. Buffer'a ekle
            readingBuffer.add(TimestampedReading(heartRate, now))

            // 2. Pencere dışındaki okumaları temizle
            // Zamanlama kayması (jitter) toleransı: +1 saniye ekle
            // Client modda 5sn polling + 10sn pencere gibi sınır durumlarda
            // milisaniye farkıyla okumaların silinmesini önler
            val windowMs = windowSeconds * 1000L + 1000L
            readingBuffer.removeAll { now - it.timestamp > windowMs }

            // 3. Pencere içindeki eşik aşımlarını say
            val lowExceedCount = readingBuffer.count { it.heartRate < minThreshold }
            val highExceedCount = readingBuffer.count { it.heartRate > maxThreshold }

            Log.d(TAG, "Window: buffer=${readingBuffer.size}, lowExceed=$lowExceedCount, " +
                "highExceed=$highExceedCount, needed=$minExceedCount")

            // 4. Yeterli aşım varsa ve cooldown geçmişse bildirim ver
            if (lowExceedCount >= minExceedCount && now - lastLowAlertTime > cooldownMs) {
                Log.w(TAG, "LOW ALERT: $lowExceedCount exceedances in ${windowSeconds}s")
                triggerAlert(
                    title = context.getString(R.string.alert_low_title),
                    message = context.getString(R.string.alert_low_message, heartRate, minThreshold),
                    isLow = true
                )
                lastLowAlertTime = now
                readingBuffer.clear()
            } else if (highExceedCount >= minExceedCount && now - lastHighAlertTime > cooldownMs) {
                Log.w(TAG, "HIGH ALERT: $highExceedCount exceedances in ${windowSeconds}s")
                triggerAlert(
                    title = context.getString(R.string.alert_high_title),
                    message = context.getString(R.string.alert_high_message, heartRate, maxThreshold),
                    isLow = false
                )
                lastHighAlertTime = now
                readingBuffer.clear()
            }

            // 5. Buffer boyut kontrolü (bellek koruması)
            while (readingBuffer.size > MAX_BUFFER_SIZE) {
                readingBuffer.removeAt(0)
            }
        }
    }

    private fun triggerAlert(title: String, message: String, isLow: Boolean) {
        Log.w(TAG, "ALERT: $title - $message")

        if (prefs.alertVibrationEnabled) {
            vibrate()
        }

        showAlertNotification(title, message, isLow)
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 500, 200, 500, 200, 500)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun showAlertNotification(title: String, message: String, isLow: Boolean) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = getAlertSoundUri()

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun getAlertSoundUri(): Uri {
        val savedUri = prefs.alertSoundUri
        return if (savedUri.isNotEmpty()) {
            Uri.parse(savedUri)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }

    fun dismissAlert() {
        notificationManager.cancel(ALERT_NOTIFICATION_ID)
    }

    /**
     * İletişim kaybı bildirimi kontrol et.
     * Mevcut cooldown sistemini kullanır, alertsEnabled master switch'ine bağlıdır.
     * @return true ise alert tetiklendi
     */
    fun checkConnectivityAlert(type: ConnectivityAlertType): Boolean {
        if (!prefs.alertsEnabled) return false

        val now = System.currentTimeMillis()
        val cooldownMs = prefs.alertCooldownMinutes * 60 * 1000L

        val lastAlertTime = when (type) {
            ConnectivityAlertType.API_UNREACHABLE -> lastApiAlertTime
            ConnectivityAlertType.BLE_DISCONNECTED -> lastBleAlertTime
            ConnectivityAlertType.DATA_STALE -> lastStaleDataAlertTime
        }

        if (now - lastAlertTime <= cooldownMs) {
            Log.d(TAG, "Connectivity alert $type in cooldown, skipping")
            return false
        }

        val (title, message) = when (type) {
            ConnectivityAlertType.API_UNREACHABLE ->
                context.getString(R.string.alert_connectivity_api_title) to
                context.getString(R.string.alert_connectivity_api_message)
            ConnectivityAlertType.BLE_DISCONNECTED ->
                context.getString(R.string.alert_connectivity_ble_title) to
                context.getString(R.string.alert_connectivity_ble_message)
            ConnectivityAlertType.DATA_STALE ->
                context.getString(R.string.alert_connectivity_stale_title) to
                context.getString(R.string.alert_connectivity_stale_message)
        }

        when (type) {
            ConnectivityAlertType.API_UNREACHABLE -> lastApiAlertTime = now
            ConnectivityAlertType.BLE_DISCONNECTED -> lastBleAlertTime = now
            ConnectivityAlertType.DATA_STALE -> lastStaleDataAlertTime = now
        }

        Log.w(TAG, "CONNECTIVITY ALERT: $type - $title")
        if (prefs.alertVibrationEnabled) {
            vibrate()
        }
        showConnectivityNotification(title, message)
        return true
    }

    /**
     * İletişim geri geldiğinde connectivity bildirimini kaldır.
     */
    fun clearConnectivityState(type: ConnectivityAlertType) {
        Log.d(TAG, "Connectivity restored: $type")
        notificationManager.cancel(CONNECTIVITY_NOTIFICATION_ID)
    }

    private fun showConnectivityNotification(title: String, message: String) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = getAlertSoundUri()

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        notificationManager.notify(CONNECTIVITY_NOTIFICATION_ID, notification)
    }
}
