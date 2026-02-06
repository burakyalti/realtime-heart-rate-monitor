package net.hrapp.hr.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.hrapp.hr.data.PreferencesManager
import java.util.concurrent.TimeUnit

class ServiceHealthWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ServiceHealthWorker"
        private const val WORK_NAME = "service_health_check"
        private const val CHECK_INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            Log.d(TAG, "Scheduling periodic health check")

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .build()

            val request = PeriodicWorkRequestBuilder<ServiceHealthWorker>(
                CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.i(TAG, "Health check scheduled every $CHECK_INTERVAL_MINUTES minutes")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Health check cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Running health check...")

        val prefs = PreferencesManager(applicationContext)

        if (!prefs.isServiceEnabled) {
            Log.d(TAG, "Service is disabled by user, skipping")
            return Result.success()
        }

        if (!isServiceRunning()) {
            Log.w(TAG, "Service not running, restarting...")
            startService()
        } else {
            Log.d(TAG, "Service is running OK")
        }

        return Result.success()
    }

    private fun isServiceRunning(): Boolean {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val services = manager.getRunningServices(Int.MAX_VALUE)
        return services.any {
            it.service.className == HeartMonitorService::class.java.name
        }
    }

    private fun startService() {
        try {
            val intent = Intent(applicationContext, HeartMonitorService::class.java).apply {
                action = HeartMonitorService.ACTION_START
            }
            applicationContext.startForegroundService(intent)
            Log.i(TAG, "Service restart initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart service", e)
        }
    }
}
