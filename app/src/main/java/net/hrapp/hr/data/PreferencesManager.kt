package net.hrapp.hr.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferencesManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "heart_monitor_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_DEVICE_MAC = "device_mac"
        private const val KEY_API_URL = "api_url"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_OEM_SETUP_SHOWN = "oem_setup_shown"
        private const val KEY_BATTERY_OPTIMIZATION_ASKED = "battery_optimization_asked"
        private const val KEY_DEVICE_MODE = "device_mode"
        private const val KEY_MIN_HR_THRESHOLD = "min_hr_threshold"
        private const val KEY_MAX_HR_THRESHOLD = "max_hr_threshold"
        private const val KEY_ALERTS_ENABLED = "alerts_enabled"
        private const val KEY_ALERT_SOUND_URI = "alert_sound_uri"
        private const val KEY_ALERT_VIBRATION = "alert_vibration"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_APP_LANGUAGE = "app_language"

        private const val DEFAULT_API_URL = "https://example.com/hr/api"
        private const val DEFAULT_LANGUAGE = "system"
        private const val DEFAULT_DEVICE_MAC = "F0:13:C3:EE:E1:55"
        private const val DEFAULT_API_KEY = "hr_api_key_2024_secure"
        const val MODE_SERVER = "SERVER"
        const val MODE_CLIENT = "CLIENT"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_SERVICE_ENABLED, value) }

    var deviceMac: String
        get() = prefs.getString(KEY_DEVICE_MAC, DEFAULT_DEVICE_MAC) ?: DEFAULT_DEVICE_MAC
        set(value) = prefs.edit { putString(KEY_DEVICE_MAC, value) }

    var apiUrl: String
        get() = prefs.getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL
        set(value) = prefs.edit { putString(KEY_API_URL, value) }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
        set(value) = prefs.edit { putString(KEY_API_KEY, value) }

    // API base URL'den live.php URL'i türet
    // Örnek: https://example.com/hr/api -> https://example.com/hr/live.php
    val liveUrl: String
        get() {
            val base = apiUrl.trimEnd('/')
            // /api veya /api/ ile bitiyorsa, onu kaldır ve /live.php ekle
            return if (base.endsWith("/api")) {
                base.dropLast(4) + "/live.php"
            } else {
                // Eğer farklı bir yapıdaysa, parent dizine live.php ekle
                base.substringBeforeLast('/') + "/live.php"
            }
        }

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit { putBoolean(KEY_FIRST_LAUNCH, value) }

    var isOemSetupShown: Boolean
        get() = prefs.getBoolean(KEY_OEM_SETUP_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_OEM_SETUP_SHOWN, value) }

    var isBatteryOptimizationAsked: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_OPTIMIZATION_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_BATTERY_OPTIMIZATION_ASKED, value) }

    // Cihaz modu: SERVER (BLE ile veri toplama) veya CLIENT (WebView ile izleme)
    var deviceMode: String
        get() = prefs.getString(KEY_DEVICE_MODE, MODE_SERVER) ?: MODE_SERVER
        set(value) = prefs.edit { putString(KEY_DEVICE_MODE, value) }

    val isServerMode: Boolean
        get() = deviceMode == MODE_SERVER

    val isClientMode: Boolean
        get() = deviceMode == MODE_CLIENT

    // Nabız eşikleri
    var minHeartRateThreshold: Int
        get() = prefs.getInt(KEY_MIN_HR_THRESHOLD, 50)
        set(value) = prefs.edit { putInt(KEY_MIN_HR_THRESHOLD, value) }

    var maxHeartRateThreshold: Int
        get() = prefs.getInt(KEY_MAX_HR_THRESHOLD, 120)
        set(value) = prefs.edit { putInt(KEY_MAX_HR_THRESHOLD, value) }

    // Uyarı ayarları
    var alertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALERTS_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_ALERTS_ENABLED, value) }

    var alertSoundUri: String
        get() = prefs.getString(KEY_ALERT_SOUND_URI, "") ?: ""
        set(value) = prefs.edit { putString(KEY_ALERT_SOUND_URI, value) }

    var alertVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALERT_VIBRATION, true)
        set(value) = prefs.edit { putBoolean(KEY_ALERT_VIBRATION, value) }

    // Dil tercihi: "system", "tr", "en"
    var appLanguage: String
        get() = prefs.getString(KEY_APP_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = prefs.edit { putString(KEY_APP_LANGUAGE, value) }
}
