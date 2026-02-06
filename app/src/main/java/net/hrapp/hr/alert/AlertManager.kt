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

    companion object {
        private const val TAG = "AlertManager"
        const val ALERT_CHANNEL_ID = "heart_rate_alert_channel"
        private const val ALERT_NOTIFICATION_ID = 2001

        // Cooldown - aynı alert için minimum bekleme süresi (30 saniye)
        private const val ALERT_COOLDOWN_MS = 30_000L
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefs = PreferencesManager(context)

    private var lastLowAlertTime = 0L
    private var lastHighAlertTime = 0L

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
            "Nabız Uyarıları",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Nabız eşikleri aşıldığında uyarı verir"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            setSound(soundUri, audioAttributes)
            setBypassDnd(true)
        }

        notificationManager.createNotificationChannel(channel)
    }

    fun updateAlertSound() {
        // Kanal ses ayarını güncellemek için kanalı yeniden oluştur
        createAlertChannel()
    }

    fun checkAndAlert(heartRate: Int, minThreshold: Int, maxThreshold: Int) {
        if (!prefs.alertsEnabled) return

        val now = System.currentTimeMillis()

        when {
            heartRate < minThreshold -> {
                if (now - lastLowAlertTime > ALERT_COOLDOWN_MS) {
                    triggerAlert(
                        title = "Düşük Nabız Uyarısı",
                        message = "Nabız $heartRate BPM - Eşik değerin ($minThreshold BPM) altında!",
                        isLow = true
                    )
                    lastLowAlertTime = now
                }
            }
            heartRate > maxThreshold -> {
                if (now - lastHighAlertTime > ALERT_COOLDOWN_MS) {
                    triggerAlert(
                        title = "Yüksek Nabız Uyarısı",
                        message = "Nabız $heartRate BPM - Eşik değerin ($maxThreshold BPM) üstünde!",
                        isLow = false
                    )
                    lastHighAlertTime = now
                }
            }
        }
    }

    private fun triggerAlert(title: String, message: String, isLow: Boolean) {
        Log.w(TAG, "ALERT: $title - $message")

        // Vibrate
        if (prefs.alertVibrationEnabled) {
            vibrate()
        }

        // Show notification with sound
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
}
