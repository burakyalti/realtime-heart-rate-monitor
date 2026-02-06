package net.hrapp.hr

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import net.hrapp.hr.ble.BleManager
import net.hrapp.hr.ble.ScannedDevice
import net.hrapp.hr.data.PreferencesManager
import net.hrapp.hr.service.ClientMonitorService
import net.hrapp.hr.service.HeartMonitorService
import net.hrapp.hr.ui.components.ConnectionState
import net.hrapp.hr.ui.screens.MainScreen
import net.hrapp.hr.ui.screens.OemSetupScreen
import net.hrapp.hr.ui.theme.HeartMonitorTheme
import net.hrapp.hr.util.LocaleHelper
import net.hrapp.hr.util.OemCompatibilityHelper
import net.hrapp.hr.util.PermissionHelper

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = PreferencesManager(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, prefs.appLanguage))
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            checkAndStartService()
        }
    }

    // Sound picker callback - will be set by compose
    private var onSoundSelected: ((Uri?, String) -> Unit)? = null

    private val soundPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val name = if (uri != null) {
                RingtoneManager.getRingtone(this, uri)?.getTitle(this) ?: "Özel Ses"
            } else {
                ""
            }
            onSoundSelected?.invoke(uri, name)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPermissions()

        setContent {
            HeartMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }

    private fun requestPermissions() {
        if (!PermissionHelper.hasAllPermissions(this)) {
            permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
        }
    }

    private fun checkAndStartService() {
        val prefs = PreferencesManager(this)
        if (prefs.isServiceEnabled) {
            if (prefs.isServerMode && !isServiceRunning()) {
                startHeartMonitorService()
            } else if (!prefs.isServerMode && !isClientServiceRunning()) {
                startClientMonitorService()
            }
        }
    }

    private fun startHeartMonitorService() {
        val intent = Intent(this, HeartMonitorService::class.java).apply {
            action = HeartMonitorService.ACTION_START
        }
        startForegroundService(intent)
    }

    private fun stopHeartMonitorService() {
        val intent = Intent(this, HeartMonitorService::class.java).apply {
            action = HeartMonitorService.ACTION_STOP
        }
        startService(intent)
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == HeartMonitorService::class.java.name
        }
    }

    private fun startClientMonitorService() {
        val intent = Intent(this, ClientMonitorService::class.java).apply {
            action = ClientMonitorService.ACTION_START
        }
        startForegroundService(intent)
    }

    private fun stopClientMonitorService() {
        val intent = Intent(this, ClientMonitorService::class.java).apply {
            action = ClientMonitorService.ACTION_STOP
        }
        startService(intent)
    }

    private fun isClientServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == ClientMonitorService::class.java.name
        }
    }

    @Composable
    fun MainNavigation() {
        var currentScreen by remember { mutableStateOf("main") }
        val context = LocalContext.current
        val prefs = remember { PreferencesManager(context) }

        // Mode state
        var isServerMode by remember { mutableStateOf(prefs.isServerMode) }
        var minThreshold by remember { mutableIntStateOf(prefs.minHeartRateThreshold) }
        var maxThreshold by remember { mutableIntStateOf(prefs.maxHeartRateThreshold) }

        // Service enabled state
        var isServiceEnabledState by remember { mutableStateOf(prefs.isServiceEnabled) }

        // Alert settings state
        var alertsEnabled by remember { mutableStateOf(prefs.alertsEnabled) }
        var alertVibrationEnabled by remember { mutableStateOf(prefs.alertVibrationEnabled) }
        var alertSoundName by remember { mutableStateOf(
            if (prefs.alertSoundUri.isEmpty()) "Varsayılan" else "Özel Ses"
        ) }

        // API URL and Key state
        var apiUrl by remember { mutableStateOf(prefs.apiUrl) }
        var apiKey by remember { mutableStateOf(prefs.apiKey) }

        // Language state
        var currentLanguage by remember { mutableStateOf(prefs.appLanguage) }

        // Initialize ApiClient with saved URL and API Key
        remember {
            net.hrapp.hr.api.ApiClient.setBaseUrl(prefs.apiUrl)
            net.hrapp.hr.api.ApiClient.setApiKey(prefs.apiKey)
            true
        }

        var heartRate by remember { mutableStateOf<Int?>(null) }
        var connectionState by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
        var isServiceRunning by remember { mutableStateOf(isServiceRunning()) }
        var offlineCount by remember { mutableIntStateOf(0) }
        var sentCount by remember { mutableIntStateOf(0) }
        var batteryLevel by remember { mutableStateOf<Int?>(null) }
        var sensorContact by remember { mutableStateOf<Boolean?>(null) }
        var rrIntervals by remember { mutableStateOf<List<Int>>(emptyList()) }
        var lastUpdateTime by remember { mutableStateOf<Long?>(null) }
        var signalQuality by remember { mutableStateOf<Float?>(null) }
        var heartRateHistory by remember { mutableStateOf<List<Int>>(emptyList()) }

        // BLE device discovery state
        val bleManager = remember { BleManager(context) }
        val scannedDevices = remember { mutableStateListOf<ScannedDevice>() }
        var isDiscovering by remember { mutableStateOf(false) }

        // Initialize BleManager for discovery
        DisposableEffect(Unit) {
            bleManager.initialize()
            bleManager.onDeviceDiscovered = { device ->
                // Add to list if not already present (by address)
                if (scannedDevices.none { it.address == device.address }) {
                    scannedDevices.add(device)
                } else {
                    // Update existing device (RSSI may have changed)
                    val index = scannedDevices.indexOfFirst { it.address == device.address }
                    if (index >= 0) {
                        scannedDevices[index] = device
                    }
                }
            }
            bleManager.onDiscoveryStateChanged = { discovering ->
                isDiscovering = discovering
            }

            onDispose {
                bleManager.stopDeviceDiscovery()
            }
        }

        // Check initial Bluetooth state
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        var isBluetoothEnabled by remember {
            mutableStateOf(bluetoothAdapter?.isEnabled == true)
        }

        // Update connectionState based on Bluetooth state
        if (!isBluetoothEnabled && connectionState != ConnectionState.BLUETOOTH_OFF) {
            connectionState = ConnectionState.BLUETOOTH_OFF
        }

        // Listen for Bluetooth state changes directly
        DisposableEffect(Unit) {
            val bluetoothReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        when (state) {
                            BluetoothAdapter.STATE_ON -> {
                                isBluetoothEnabled = true
                                // Bluetooth açıldı, servis çalışıyorsa SCANNING durumuna geç
                                if (isServiceRunning && connectionState == ConnectionState.BLUETOOTH_OFF) {
                                    connectionState = ConnectionState.SCANNING
                                }
                            }
                            BluetoothAdapter.STATE_OFF -> {
                                isBluetoothEnabled = false
                                connectionState = ConnectionState.BLUETOOTH_OFF
                                heartRate = null
                            }
                        }
                    }
                }
            }

            val btFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            context.registerReceiver(bluetoothReceiver, btFilter)

            onDispose {
                context.unregisterReceiver(bluetoothReceiver)
            }
        }

        // Listen for service broadcasts (only in Server mode)
        DisposableEffect(isServerMode) {
            if (!isServerMode) {
                // Client modda broadcast dinlemeye gerek yok
                return@DisposableEffect onDispose { }
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        HeartMonitorService.ACTION_HEART_RATE_UPDATE -> {
                            val hr = intent.getIntExtra(HeartMonitorService.EXTRA_HEART_RATE, 0)
                            val hasContact = intent.getBooleanExtra(HeartMonitorService.EXTRA_SENSOR_CONTACT, false)
                            val quality = intent.getFloatExtra(HeartMonitorService.EXTRA_SIGNAL_QUALITY, 0f)
                            val sent = intent.getIntExtra(HeartMonitorService.EXTRA_SENT_COUNT, 0)

                            sensorContact = hasContact
                            signalQuality = if (quality > 0f) quality else null
                            if (sent > 0) sentCount = sent

                            // Sadece temas varken nabız değerini göster
                            if (hasContact && hr > 0) {
                                heartRate = hr
                                lastUpdateTime = System.currentTimeMillis()
                                if (intent.hasExtra(HeartMonitorService.EXTRA_RR_INTERVALS)) {
                                    val rrArray = intent.getIntArrayExtra(HeartMonitorService.EXTRA_RR_INTERVALS)
                                    rrIntervals = rrArray?.toList() ?: emptyList()
                                }
                                // History'ye ekle (son 180 değer = ~3 dakika)
                                heartRateHistory = (heartRateHistory + hr).takeLast(180)
                            } else {
                                // Temas yok - nabız değerini temizle
                                heartRate = null
                                rrIntervals = emptyList()
                            }
                        }
                        HeartMonitorService.ACTION_CONNECTION_STATE -> {
                            val state = intent.getStringExtra(HeartMonitorService.EXTRA_CONNECTION_STATE)
                            // Bluetooth kapalıysa, servis ne derse desin BLUETOOTH_OFF kalsın
                            if (!isBluetoothEnabled) {
                                connectionState = ConnectionState.BLUETOOTH_OFF
                            } else {
                                connectionState = when (state) {
                                    "CONNECTED" -> ConnectionState.CONNECTED
                                    "CONNECTING" -> ConnectionState.CONNECTING
                                    "SCANNING" -> ConnectionState.SCANNING
                                    "BLUETOOTH_OFF" -> ConnectionState.BLUETOOTH_OFF
                                    else -> ConnectionState.DISCONNECTED
                                }
                            }
                            // Clear heart rate when not connected
                            if (connectionState != ConnectionState.CONNECTED) {
                                heartRate = null
                            }
                        }
                        HeartMonitorService.ACTION_OFFLINE_COUNT -> {
                            offlineCount = intent.getIntExtra(HeartMonitorService.EXTRA_OFFLINE_COUNT, 0)
                        }
                        HeartMonitorService.ACTION_DEVICE_INFO -> {
                            if (intent.hasExtra(HeartMonitorService.EXTRA_BATTERY_LEVEL)) {
                                batteryLevel = intent.getIntExtra(HeartMonitorService.EXTRA_BATTERY_LEVEL, 0)
                            }
                            if (intent.hasExtra(HeartMonitorService.EXTRA_SENSOR_CONTACT)) {
                                sensorContact = intent.getBooleanExtra(HeartMonitorService.EXTRA_SENSOR_CONTACT, false)
                            }
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(HeartMonitorService.ACTION_HEART_RATE_UPDATE)
                addAction(HeartMonitorService.ACTION_CONNECTION_STATE)
                addAction(HeartMonitorService.ACTION_OFFLINE_COUNT)
                addAction(HeartMonitorService.ACTION_DEVICE_INFO)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }

            // Servis çalışıyorsa mevcut durumu iste
            if (isServiceRunning) {
                context.startService(Intent(context, HeartMonitorService::class.java).apply {
                    action = HeartMonitorService.ACTION_REQUEST_STATE
                })
            }

            onDispose {
                context.unregisterReceiver(receiver)
            }
        }

        when (currentScreen) {
            "main" -> MainScreen(
                isServerMode = isServerMode,
                heartRate = heartRate,
                connectionState = connectionState,
                isServiceRunning = isServiceRunning,
                deviceMac = prefs.deviceMac,
                offlineCount = offlineCount,
                batteryLevel = batteryLevel,
                sensorContact = sensorContact,
                lastUpdateTime = lastUpdateTime,
                rrIntervals = rrIntervals,
                signalQuality = signalQuality,
                todayCount = sentCount,
                heartRateHistory = heartRateHistory,
                minHeartRate = heartRateHistory.minOrNull(),
                avgHeartRate = if (heartRateHistory.isNotEmpty()) heartRateHistory.average().toInt() else null,
                maxHeartRate = heartRateHistory.maxOrNull(),
                minThreshold = minThreshold,
                maxThreshold = maxThreshold,
                isServiceEnabled = isServiceEnabledState,
                isBatteryOptimized = !PermissionHelper.isIgnoringBatteryOptimizations(context),
                onStartService = {
                    prefs.isServiceEnabled = true
                    startHeartMonitorService()
                    isServiceRunning = true
                },
                onStopService = {
                    prefs.isServiceEnabled = false
                    stopHeartMonitorService()
                    isServiceRunning = false
                    heartRate = null
                    batteryLevel = null
                    sensorContact = null
                    rrIntervals = emptyList()
                    lastUpdateTime = null
                    signalQuality = null
                    sentCount = 0
                    heartRateHistory = emptyList()
                    connectionState = ConnectionState.DISCONNECTED
                },
                onEnableBluetooth = {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    try {
                        context.startActivity(enableBtIntent)
                    } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                },
                onServiceEnabledChange = { enabled ->
                    prefs.isServiceEnabled = enabled
                    isServiceEnabledState = enabled
                    if (enabled) {
                        if (isServerMode && !isServiceRunning) {
                            startHeartMonitorService()
                            isServiceRunning = true
                        } else if (!isServerMode) {
                            startClientMonitorService()
                        }
                    } else {
                        // Disable - stop the appropriate service
                        if (isServerMode && isServiceRunning) {
                            stopHeartMonitorService()
                            isServiceRunning = false
                        } else if (!isServerMode) {
                            stopClientMonitorService()
                        }
                    }
                },
                onBatteryOptimizationClick = {
                    PermissionHelper.requestBatteryOptimizationExemption(context)
                },
                onOemSetupClick = { currentScreen = "oem_setup" },
                onModeChange = { newIsServerMode ->
                    // Mode değişimi
                    prefs.deviceMode = if (newIsServerMode) PreferencesManager.MODE_SERVER else PreferencesManager.MODE_CLIENT
                    isServerMode = newIsServerMode

                    if (newIsServerMode) {
                        // Server moduna geçildi
                        // Client servisini durdur
                        stopClientMonitorService()

                        // Server servisini başlat
                        if (prefs.isServiceEnabled && !isServiceRunning) {
                            startHeartMonitorService()
                            isServiceRunning = true
                        }
                    } else {
                        // Client moduna geçildi
                        // Server servisini durdur
                        if (isServiceRunning) {
                            stopHeartMonitorService()
                            isServiceRunning = false
                            heartRate = null
                            batteryLevel = null
                            sensorContact = null
                            rrIntervals = emptyList()
                            lastUpdateTime = null
                            signalQuality = null
                            sentCount = 0
                            heartRateHistory = emptyList()
                            connectionState = ConnectionState.DISCONNECTED
                        }

                        // Client servisini başlat
                        if (prefs.isServiceEnabled) {
                            startClientMonitorService()
                        }
                    }
                },
                onThresholdsChange = { newMin, newMax ->
                    prefs.minHeartRateThreshold = newMin
                    prefs.maxHeartRateThreshold = newMax
                    minThreshold = newMin
                    maxThreshold = newMax
                },
                // Alert settings
                alertsEnabled = alertsEnabled,
                alertVibrationEnabled = alertVibrationEnabled,
                alertSoundName = alertSoundName,
                // API URL and Key
                apiUrl = apiUrl,
                apiKey = apiKey,
                liveUrl = prefs.liveUrl,
                // Language
                currentLanguage = currentLanguage,
                onAlertsEnabledChange = { enabled ->
                    prefs.alertsEnabled = enabled
                    alertsEnabled = enabled
                },
                onAlertVibrationChange = { enabled ->
                    prefs.alertVibrationEnabled = enabled
                    alertVibrationEnabled = enabled
                },
                onAlertSoundClick = {
                    onSoundSelected = { uri, name ->
                        if (uri != null) {
                            prefs.alertSoundUri = uri.toString()
                            alertSoundName = name
                        } else {
                            prefs.alertSoundUri = ""
                            alertSoundName = "Varsayılan"
                        }
                    }
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Uyarı Sesi Seç")
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        val currentUri = prefs.alertSoundUri
                        if (currentUri.isNotEmpty()) {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri))
                        }
                    }
                    soundPickerLauncher.launch(intent)
                },
                onApiUrlChange = { newUrl ->
                    prefs.apiUrl = newUrl
                    apiUrl = newUrl
                    // ApiClient'a da bildir
                    net.hrapp.hr.api.ApiClient.setBaseUrl(newUrl)
                },
                onApiKeyChange = { newKey ->
                    prefs.apiKey = newKey
                    apiKey = newKey
                    // ApiClient'a da bildir
                    net.hrapp.hr.api.ApiClient.setApiKey(newKey)
                },
                onDeviceMacChange = { newMac ->
                    prefs.deviceMac = newMac
                },
                onLanguageChange = { newLanguage ->
                    prefs.appLanguage = newLanguage
                    currentLanguage = newLanguage
                    // Activity'yi yeniden başlat
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    (context as? ComponentActivity)?.finish()
                },
                // Device discovery
                scannedDevices = scannedDevices.toList(),
                isDiscovering = isDiscovering,
                onStartDiscovery = {
                    scannedDevices.clear()
                    bleManager.startDeviceDiscovery()
                },
                onStopDiscovery = {
                    bleManager.stopDeviceDiscovery()
                }
            )

            "oem_setup" -> OemSetupScreen(
                onBackClick = { currentScreen = "main" },
                onOpenSettings = {
                    OemCompatibilityHelper.tryOpenAutoStartSettings(context)
                },
                onComplete = {
                    prefs.isOemSetupShown = true
                    currentScreen = "main"
                }
            )
        }

        // Show OEM setup on first launch for compatible devices (only in Server mode)
        if (prefs.isFirstLaunch && OemCompatibilityHelper.needsSpecialSetup() && isServerMode) {
            prefs.isFirstLaunch = false
            currentScreen = "oem_setup"
        }
    }
}
