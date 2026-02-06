package net.hrapp.hr

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import net.hrapp.hr.service.ServiceHealthWorker

class HeartMonitorApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "heart_monitor_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ServiceHealthWorker.schedule(this)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
