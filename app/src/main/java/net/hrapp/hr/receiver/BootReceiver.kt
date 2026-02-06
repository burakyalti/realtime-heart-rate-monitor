package net.hrapp.hr.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import net.hrapp.hr.data.PreferencesManager
import net.hrapp.hr.service.ClientMonitorService
import net.hrapp.hr.service.HeartMonitorService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                val prefs = PreferencesManager(context)

                if (prefs.isServiceEnabled) {
                    if (prefs.isServerMode) {
                        Log.i(TAG, "Starting HeartMonitorService on boot (Server mode)...")
                        startServerService(context)
                    } else {
                        Log.i(TAG, "Starting ClientMonitorService on boot (Client mode)...")
                        startClientService(context)
                    }
                } else {
                    Log.d(TAG, "Service not enabled, skipping start")
                }
            }
        }
    }

    private fun startServerService(context: Context) {
        try {
            val serviceIntent = Intent(context, HeartMonitorService::class.java).apply {
                action = HeartMonitorService.ACTION_START
            }
            context.startForegroundService(serviceIntent)
            Log.i(TAG, "Server service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server service", e)
        }
    }

    private fun startClientService(context: Context) {
        try {
            val serviceIntent = Intent(context, ClientMonitorService::class.java).apply {
                action = ClientMonitorService.ACTION_START
            }
            context.startForegroundService(serviceIntent)
            Log.i(TAG, "Client service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start client service", e)
        }
    }
}
