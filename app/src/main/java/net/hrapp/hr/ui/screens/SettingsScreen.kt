package net.hrapp.hr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.hrapp.hr.R
import net.hrapp.hr.ble.ScannedDevice
import net.hrapp.hr.util.LocaleHelper
import net.hrapp.hr.ui.components.DevicePickerDialog
import net.hrapp.hr.ui.theme.HeartMonitorColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    deviceMac: String,
    isServiceEnabled: Boolean,
    isBatteryOptimized: Boolean,
    onBackClick: () -> Unit,
    onServiceEnabledChange: (Boolean) -> Unit,
    onBatteryOptimizationClick: () -> Unit,
    onOemSetupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Cihaz Bilgisi
            SectionTitle(stringResource(R.string.settings_device_title))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                SettingsItem(
                    icon = Icons.Default.Bluetooth,
                    title = stringResource(R.string.settings_device_connected),
                    subtitle = deviceMac,
                    onClick = null
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Servis Ayarları
            SectionTitle(stringResource(R.string.settings_service_title))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_service_autostart),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.settings_service_autostart_server),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = isServiceEnabled,
                        onCheckedChange = onServiceEnabledChange
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Pil Ayarları
            SectionTitle(stringResource(R.string.settings_battery_title))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column {
                    SettingsItem(
                        icon = Icons.Default.BatteryAlert,
                        title = stringResource(R.string.settings_battery_optimization),
                        subtitle = stringResource(if (isBatteryOptimized) R.string.settings_battery_should_disable else R.string.settings_battery_disabled),
                        subtitleColor = if (isBatteryOptimized) Color(0xFFF44336) else Color(0xFF4CAF50),
                        onClick = onBatteryOptimizationClick
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsItem(
                        icon = Icons.Default.PhoneAndroid,
                        title = stringResource(R.string.settings_oem_settings),
                        subtitle = stringResource(R.string.settings_oem_settings_desc),
                        onClick = onOemSetupClick
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Versiyon
            Text(
                text = stringResource(R.string.settings_version),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = subtitleColor
            )
        }
    }
}

/**
 * SettingsScreen content without Scaffold - for use inside MainScreen tabs
 */
@Composable
fun SettingsScreenContent(
    deviceMac: String,
    isServerMode: Boolean,
    isServiceEnabled: Boolean,
    isBatteryOptimized: Boolean,
    minThreshold: Int,
    maxThreshold: Int,
    alertsEnabled: Boolean,
    alertVibrationEnabled: Boolean,
    alertSoundName: String,
    apiUrl: String,
    apiKey: String,
    currentLanguage: String,
    onModeChange: (Boolean) -> Unit, // true = Server, false = Client
    onServiceEnabledChange: (Boolean) -> Unit,
    onBatteryOptimizationClick: () -> Unit,
    onOemSetupClick: () -> Unit,
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
    // Local state for sliders
    var minSliderValue by remember(minThreshold) { mutableFloatStateOf(minThreshold.toFloat()) }
    var maxSliderValue by remember(maxThreshold) { mutableFloatStateOf(maxThreshold.toFloat()) }
    // Local state for API URL editing
    var apiUrlText by remember(apiUrl) { mutableStateOf(apiUrl) }
    // Local state for API Key editing
    var apiKeyText by remember(apiKey) { mutableStateOf(apiKey) }
    // Local state for device MAC editing
    var deviceMacText by remember(deviceMac) { mutableStateOf(deviceMac) }
    // Local state for device picker dialog
    var showDevicePickerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HeartMonitorColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Cihaz Modu
        SectionTitle(stringResource(R.string.settings_mode_title))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = HeartMonitorColors.CardBackground
            )
        ) {
            Column(modifier = Modifier.selectableGroup()) {
                // Server mode option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isServerMode,
                            onClick = { onModeChange(true) },
                            role = Role.RadioButton
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isServerMode,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = HeartMonitorColors.HeartPink
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = if (isServerMode) HeartMonitorColors.HeartPink else HeartMonitorColors.TextMuted
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_mode_server),
                            fontWeight = FontWeight.Medium,
                            color = HeartMonitorColors.TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.settings_mode_server_desc),
                            fontSize = 12.sp,
                            color = HeartMonitorColors.TextMuted
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = HeartMonitorColors.DarkBackground
                )

                // Client mode option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = !isServerMode,
                            onClick = { onModeChange(false) },
                            role = Role.RadioButton
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !isServerMode,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = HeartMonitorColors.HeartPink
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = if (!isServerMode) HeartMonitorColors.HeartPink else HeartMonitorColors.TextMuted
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_mode_client),
                            fontWeight = FontWeight.Medium,
                            color = HeartMonitorColors.TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.settings_mode_client_desc),
                            fontSize = 12.sp,
                            color = HeartMonitorColors.TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Dil Ayarları
        SectionTitle(stringResource(R.string.settings_language))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = HeartMonitorColors.CardBackground
            )
        ) {
            Column(modifier = Modifier.selectableGroup()) {
                LocaleHelper.supportedLanguages.forEach { (code, displayName) ->
                    val isSelected = currentLanguage == code

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { onLanguageChange(code) },
                                role = Role.RadioButton
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = HeartMonitorColors.HeartPink
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = displayName,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSelected) HeartMonitorColors.HeartPink else HeartMonitorColors.TextPrimary
                        )
                    }

                    if (code != LocaleHelper.supportedLanguages.last().first) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = HeartMonitorColors.DarkBackground
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // API URL Ayarları
        SectionTitle(stringResource(R.string.settings_server_title))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = HeartMonitorColors.CardBackground
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = HeartMonitorColors.HeartPink
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.settings_api_url),
                        fontWeight = FontWeight.Medium,
                        color = HeartMonitorColors.TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.settings_api_url_desc),
                    fontSize = 12.sp,
                    color = HeartMonitorColors.TextMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiUrlText,
                    onValueChange = { newValue ->
                        apiUrlText = newValue
                        onApiUrlChange(newValue)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.settings_api_url_hint),
                            color = HeartMonitorColors.TextMuted
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HeartMonitorColors.TextPrimary,
                        unfocusedTextColor = HeartMonitorColors.TextPrimary,
                        focusedBorderColor = HeartMonitorColors.HeartPink,
                        unfocusedBorderColor = HeartMonitorColors.TextMuted,
                        cursorColor = HeartMonitorColors.HeartPink
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = HeartMonitorColors.DarkBackground)

                Spacer(modifier = Modifier.height(16.dp))

                // API Key
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = HeartMonitorColors.HeartPink
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.settings_api_key),
                        fontWeight = FontWeight.Medium,
                        color = HeartMonitorColors.TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.settings_api_key_desc),
                    fontSize = 12.sp,
                    color = HeartMonitorColors.TextMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { newValue ->
                        apiKeyText = newValue
                        onApiKeyChange(newValue)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.settings_api_key_hint),
                            color = HeartMonitorColors.TextMuted
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HeartMonitorColors.TextPrimary,
                        unfocusedTextColor = HeartMonitorColors.TextPrimary,
                        focusedBorderColor = HeartMonitorColors.HeartPink,
                        unfocusedBorderColor = HeartMonitorColors.TextMuted,
                        cursorColor = HeartMonitorColors.HeartPink
                    )
                )
            }
        }

        // BLE Cihaz Ayarları - only in Server mode
        if (isServerMode) {
            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle(stringResource(R.string.settings_ble_title))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = HeartMonitorColors.CardBackground
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = HeartMonitorColors.HeartPink
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_device_mac),
                            fontWeight = FontWeight.Medium,
                            color = HeartMonitorColors.TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.settings_device_mac_desc),
                        fontSize = 12.sp,
                        color = HeartMonitorColors.TextMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = deviceMacText,
                            onValueChange = { newValue ->
                                // MAC adresini büyük harfe çevir
                                val formatted = newValue.uppercase()
                                deviceMacText = formatted
                                onDeviceMacChange(formatted)
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.settings_device_mac_hint),
                                    color = HeartMonitorColors.TextMuted
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = HeartMonitorColors.TextPrimary,
                                unfocusedTextColor = HeartMonitorColors.TextPrimary,
                                focusedBorderColor = HeartMonitorColors.HeartPink,
                                unfocusedBorderColor = HeartMonitorColors.TextMuted,
                                cursorColor = HeartMonitorColors.HeartPink
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { showDevicePickerDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HeartMonitorColors.HeartPink
                            )
                        ) {
                            Text(stringResource(R.string.settings_btn_scan))
                        }
                    }
                }
            }
        }

        // Device Picker Dialog
        if (showDevicePickerDialog) {
            DevicePickerDialog(
                devices = scannedDevices,
                isScanning = isDiscovering,
                onDeviceSelect = { device ->
                    deviceMacText = device.address
                    onDeviceMacChange(device.address)
                    showDevicePickerDialog = false
                    onStopDiscovery()
                },
                onDismiss = {
                    showDevicePickerDialog = false
                    onStopDiscovery()
                },
                onStartScan = onStartDiscovery,
                onStopScan = onStopDiscovery
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Nabız Eşikleri
        SectionTitle(stringResource(R.string.settings_thresholds_title))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = HeartMonitorColors.CardBackground
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Min threshold slider
                Text(
                    text = stringResource(R.string.settings_threshold_min, minSliderValue.toInt()),
                    fontWeight = FontWeight.Medium,
                    color = HeartMonitorColors.TextPrimary
                )
                Text(
                    text = stringResource(R.string.settings_threshold_min_desc),
                    fontSize = 12.sp,
                    color = HeartMonitorColors.TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = minSliderValue,
                    onValueChange = { minSliderValue = it },
                    onValueChangeFinished = {
                        onThresholdsChange(minSliderValue.toInt(), maxSliderValue.toInt())
                    },
                    valueRange = 30f..80f,
                    steps = 9, // 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80
                    colors = SliderDefaults.colors(
                        thumbColor = HeartMonitorColors.LowHeartRate,
                        activeTrackColor = HeartMonitorColors.LowHeartRate
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Max threshold slider
                Text(
                    text = stringResource(R.string.settings_threshold_max, maxSliderValue.toInt()),
                    fontWeight = FontWeight.Medium,
                    color = HeartMonitorColors.TextPrimary
                )
                Text(
                    text = stringResource(R.string.settings_threshold_max_desc),
                    fontSize = 12.sp,
                    color = HeartMonitorColors.TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = maxSliderValue,
                    onValueChange = { maxSliderValue = it },
                    onValueChangeFinished = {
                        onThresholdsChange(minSliderValue.toInt(), maxSliderValue.toInt())
                    },
                    valueRange = 100f..180f,
                    steps = 15, // 100, 105, 110, ... 180
                    colors = SliderDefaults.colors(
                        thumbColor = HeartMonitorColors.Critical,
                        activeTrackColor = HeartMonitorColors.Critical
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Preview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = HeartMonitorColors.DarkBackground,
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "<${minSliderValue.toInt()}",
                            color = HeartMonitorColors.LowHeartRate,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_threshold_low),
                            fontSize = 10.sp,
                            color = HeartMonitorColors.TextMuted
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${minSliderValue.toInt()}-${maxSliderValue.toInt()}",
                            color = HeartMonitorColors.Connected,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_threshold_normal),
                            fontSize = 10.sp,
                            color = HeartMonitorColors.TextMuted
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = ">${maxSliderValue.toInt()}",
                            color = HeartMonitorColors.Critical,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_threshold_high),
                            fontSize = 10.sp,
                            color = HeartMonitorColors.TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Uyarılar
        SectionTitle(stringResource(R.string.settings_alerts_title))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = HeartMonitorColors.CardBackground
            )
        ) {
            Column {
                // Uyarıları Etkinleştir
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = if (alertsEnabled) HeartMonitorColors.HeartPink else HeartMonitorColors.TextMuted
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_alerts_enabled),
                            fontWeight = FontWeight.Medium,
                            color = HeartMonitorColors.TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.settings_alerts_enabled_desc),
                            fontSize = 12.sp,
                            color = HeartMonitorColors.TextMuted
                        )
                    }
                    Switch(
                        checked = alertsEnabled,
                        onCheckedChange = onAlertsEnabledChange
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = HeartMonitorColors.DarkBackground
                )

                // Titreşim
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = null,
                        tint = if (alertVibrationEnabled && alertsEnabled) HeartMonitorColors.HeartPink else HeartMonitorColors.TextMuted
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_alerts_vibration),
                            fontWeight = FontWeight.Medium,
                            color = if (alertsEnabled) HeartMonitorColors.TextPrimary else HeartMonitorColors.TextMuted
                        )
                        Text(
                            text = stringResource(R.string.settings_alerts_vibration_desc),
                            fontSize = 12.sp,
                            color = HeartMonitorColors.TextMuted
                        )
                    }
                    Switch(
                        checked = alertVibrationEnabled,
                        onCheckedChange = onAlertVibrationChange,
                        enabled = alertsEnabled
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = HeartMonitorColors.DarkBackground
                )

                // Ses Tonu Seçimi
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = alertsEnabled, onClick = onAlertSoundClick)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (alertsEnabled) HeartMonitorColors.HeartPink else HeartMonitorColors.TextMuted
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_alerts_sound),
                            fontWeight = FontWeight.Medium,
                            color = if (alertsEnabled) HeartMonitorColors.TextPrimary else HeartMonitorColors.TextMuted
                        )
                        Text(
                            text = alertSoundName.ifEmpty { stringResource(R.string.settings_alerts_sound_default) },
                            fontSize = 12.sp,
                            color = HeartMonitorColors.TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cihaz Bilgisi - only show in Server mode
        if (isServerMode) {
            SectionTitle(stringResource(R.string.settings_device_title))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = HeartMonitorColors.CardBackground
                )
            ) {
                SettingsItem(
                    icon = Icons.Default.Bluetooth,
                    title = stringResource(R.string.settings_device_connected),
                    subtitle = deviceMac,
                    onClick = null
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Servis Ayarları - both modes need this for auto-start
        SectionTitle(stringResource(R.string.settings_service_title))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = HeartMonitorColors.CardBackground
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_service_autostart),
                        fontWeight = FontWeight.Medium,
                        color = HeartMonitorColors.TextPrimary
                    )
                    Text(
                        text = stringResource(
                            if (isServerMode) R.string.settings_service_autostart_server
                            else R.string.settings_service_autostart_client
                        ),
                        fontSize = 12.sp,
                        color = HeartMonitorColors.TextMuted
                    )
                }
                Switch(
                    checked = isServiceEnabled,
                    onCheckedChange = onServiceEnabledChange
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Pil Ayarları - both modes need this for background service
        SectionTitle(stringResource(R.string.settings_battery_title))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = HeartMonitorColors.CardBackground
            )
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Default.BatteryAlert,
                    title = stringResource(R.string.settings_battery_optimization),
                    subtitle = stringResource(if (isBatteryOptimized) R.string.settings_battery_should_disable else R.string.settings_battery_disabled),
                    subtitleColor = if (isBatteryOptimized) Color(0xFFF44336) else Color(0xFF4CAF50),
                    onClick = onBatteryOptimizationClick
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = HeartMonitorColors.DarkBackground
                )

                SettingsItem(
                    icon = Icons.Default.PhoneAndroid,
                    title = stringResource(R.string.settings_oem_settings),
                    subtitle = stringResource(R.string.settings_oem_settings_desc),
                    onClick = onOemSetupClick
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Versiyon
        Text(
            text = stringResource(R.string.settings_version),
            fontSize = 12.sp,
            color = HeartMonitorColors.TextMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
