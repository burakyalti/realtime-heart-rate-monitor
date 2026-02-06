package net.hrapp.hr.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import net.hrapp.hr.ble.ScannedDevice
import net.hrapp.hr.ui.components.BottomNavBar
import net.hrapp.hr.ui.components.BottomNavItem
import net.hrapp.hr.ui.components.ConnectionState
import net.hrapp.hr.ui.theme.HeartMonitorColors

@Composable
fun MainScreen(
    // Mode
    isServerMode: Boolean,
    // Heart rate state
    heartRate: Int?,
    connectionState: ConnectionState,
    isServiceRunning: Boolean,
    // Device info
    deviceMac: String,
    offlineCount: Int,
    batteryLevel: Int?,
    sensorContact: Boolean?,
    lastUpdateTime: Long?,
    rrIntervals: List<Int>,
    signalQuality: Float?,
    todayCount: Int,
    heartRateHistory: List<Int>,
    minHeartRate: Int?,
    avgHeartRate: Int?,
    maxHeartRate: Int?,
    // Thresholds
    minThreshold: Int,
    maxThreshold: Int,
    // Settings state
    isServiceEnabled: Boolean,
    isBatteryOptimized: Boolean,
    // Alert settings
    alertsEnabled: Boolean,
    alertVibrationEnabled: Boolean,
    alertSoundName: String,
    // API URL and Key
    apiUrl: String,
    apiKey: String,
    liveUrl: String,
    // Language
    currentLanguage: String,
    // Callbacks
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onServiceEnabledChange: (Boolean) -> Unit,
    onBatteryOptimizationClick: () -> Unit,
    onOemSetupClick: () -> Unit,
    onModeChange: (Boolean) -> Unit, // true = Server, false = Client
    onThresholdsChange: (Int, Int) -> Unit, // min, max
    onAlertsEnabledChange: (Boolean) -> Unit,
    onAlertVibrationChange: (Boolean) -> Unit,
    onAlertSoundClick: () -> Unit,
    onApiUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onDeviceMacChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    // Device discovery
    scannedDevices: List<ScannedDevice> = emptyList(),
    isDiscovering: Boolean = false,
    onStartDiscovery: () -> Unit = {},
    onStopDiscovery: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Başlangıç tab'ı: Server modda Live, Client modda Monitor
    val defaultRoute = if (isServerMode) BottomNavItem.Live.route else BottomNavItem.Monitor.route
    var currentRoute by remember(isServerMode) { mutableStateOf(defaultRoute) }

    // Mod değiştiğinde route'u güncelle
    if (!isServerMode && currentRoute == BottomNavItem.Live.route) {
        currentRoute = BottomNavItem.Monitor.route
    }

    Scaffold(
        containerColor = HeartMonitorColors.Background,
        bottomBar = {
            BottomNavBar(
                currentRoute = currentRoute,
                isServerMode = isServerMode,
                onItemClick = { item ->
                    currentRoute = item.route
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        when (currentRoute) {
            BottomNavItem.Live.route -> {
                // Server mode - Dashboard (Live heart rate)
                DashboardScreenContent(
                    heartRate = heartRate,
                    connectionState = connectionState,
                    isServiceRunning = isServiceRunning,
                    deviceMac = deviceMac,
                    offlineCount = offlineCount,
                    batteryLevel = batteryLevel,
                    sensorContact = sensorContact,
                    lastUpdateTime = lastUpdateTime,
                    rrIntervals = rrIntervals,
                    signalQuality = signalQuality,
                    todayCount = todayCount,
                    heartRateHistory = heartRateHistory,
                    minHeartRate = minHeartRate,
                    avgHeartRate = avgHeartRate,
                    maxHeartRate = maxHeartRate,
                    minThreshold = minThreshold,
                    maxThreshold = maxThreshold,
                    onStartService = onStartService,
                    onStopService = onStopService,
                    onEnableBluetooth = onEnableBluetooth,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            BottomNavItem.Monitor.route -> {
                // WebView - live.php
                ClientMonitorScreen(
                    liveUrl = liveUrl,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            BottomNavItem.Settings.route -> {
                // Settings
                SettingsScreenContent(
                    deviceMac = deviceMac,
                    isServerMode = isServerMode,
                    isServiceEnabled = isServiceEnabled,
                    isBatteryOptimized = isBatteryOptimized,
                    minThreshold = minThreshold,
                    maxThreshold = maxThreshold,
                    alertsEnabled = alertsEnabled,
                    alertVibrationEnabled = alertVibrationEnabled,
                    alertSoundName = alertSoundName,
                    apiUrl = apiUrl,
                    apiKey = apiKey,
                    currentLanguage = currentLanguage,
                    onModeChange = onModeChange,
                    onServiceEnabledChange = onServiceEnabledChange,
                    onBatteryOptimizationClick = onBatteryOptimizationClick,
                    onOemSetupClick = onOemSetupClick,
                    onThresholdsChange = onThresholdsChange,
                    onAlertsEnabledChange = onAlertsEnabledChange,
                    onAlertVibrationChange = onAlertVibrationChange,
                    onAlertSoundClick = onAlertSoundClick,
                    onApiUrlChange = onApiUrlChange,
                    onApiKeyChange = onApiKeyChange,
                    onDeviceMacChange = onDeviceMacChange,
                    onLanguageChange = onLanguageChange,
                    scannedDevices = scannedDevices,
                    isDiscovering = isDiscovering,
                    onStartDiscovery = onStartDiscovery,
                    onStopDiscovery = onStopDiscovery,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}
