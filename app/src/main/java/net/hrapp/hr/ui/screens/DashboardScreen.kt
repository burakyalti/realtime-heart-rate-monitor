package net.hrapp.hr.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.hrapp.hr.R
import net.hrapp.hr.ui.components.ConnectionState
import net.hrapp.hr.ui.components.HeartRateDisplay
import net.hrapp.hr.ui.theme.HeartMonitorColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    heartRate: Int?,
    connectionState: ConnectionState,
    isServiceRunning: Boolean,
    deviceMac: String,
    offlineCount: Int,
    batteryLevel: Int? = null,
    sensorContact: Boolean? = null,
    lastUpdateTime: Long? = null,
    rrIntervals: List<Int> = emptyList(),
    signalQuality: Float? = null,
    todayCount: Int = 0,
    heartRateHistory: List<Int> = emptyList(),
    minHeartRate: Int? = null,
    avgHeartRate: Int? = null,
    maxHeartRate: Int? = null,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onSettingsClick: () -> Unit,
    onEnableBluetooth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isBluetoothOff = connectionState == ConnectionState.BLUETOOTH_OFF

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.dashboard_title),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.dashboard_settings),
                            tint = Color.White
                        )
                    }
                }
            )
        },
        containerColor = HeartMonitorColors.SurfaceDark
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Status Row - Bluetooth + Device Connection
            StatusRow(
                isBluetoothOn = !isBluetoothOff,
                connectionState = connectionState,
                onBluetoothClick = onEnableBluetooth
            )

            // Bluetooth Warning Card
            if (isBluetoothOff) {
                Spacer(modifier = Modifier.height(12.dp))
                BluetoothWarningCard(onEnableBluetooth = onEnableBluetooth)
            }

            // No Sensor Contact Warning
            val isConnectedNoContact = connectionState == ConnectionState.CONNECTED && sensorContact == false
            if (isConnectedNoContact) {
                Spacer(modifier = Modifier.height(12.dp))
                NoSensorContactCard()
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Heart Rate Card (compact)
            HeartRateCardCompact(
                heartRate = heartRate,
                isConnected = connectionState == ConnectionState.CONNECTED
            )

            // Heart Rate Chart
            if (heartRateHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HeartRateChart(
                    heartRateHistory = heartRateHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }

            // Signal Quality Card
            if (connectionState == ConnectionState.CONNECTED && signalQuality != null) {
                Spacer(modifier = Modifier.height(12.dp))
                SignalQualityCard(quality = signalQuality)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Info Cards Row
            InfoCardsRow(
                batteryLevel = batteryLevel,
                sensorContact = sensorContact,
                todayCount = todayCount,
                lastUpdateTime = formatElapsedTime(lastUpdateTime)
            )

            // RR Intervals Card
            if (rrIntervals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                RrIntervalsCard(rrIntervals = rrIntervals)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Today's Statistics
            if (minHeartRate != null || avgHeartRate != null || maxHeartRate != null) {
                StatisticsCard(
                    minHeartRate = minHeartRate,
                    avgHeartRate = avgHeartRate,
                    maxHeartRate = maxHeartRate
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Offline Count Warning
            if (offlineCount > 0) {
                OfflineCountCard(offlineCount = offlineCount)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isServiceRunning) {
                    OutlinedButton(
                        onClick = onStopService,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(30.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = HeartMonitorColors.Critical
                        )
                    ) {
                        Text("Durdur", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onStartService,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(30.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HeartMonitorColors.Connected
                        )
                    ) {
                        Text("Başlat", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(if (isServiceRunning) R.string.dashboard_service_running else R.string.dashboard_service_stopped),
                color = if (isServiceRunning) HeartMonitorColors.Connected else HeartMonitorColors.Offline,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Device Info
            Text(
                text = "Wahoo TICKR Fit • $deviceMac",
                color = HeartMonitorColors.TextMuted,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusRow(
    isBluetoothOn: Boolean,
    connectionState: ConnectionState,
    onBluetoothClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Bluetooth Status
        BluetoothStatusBadge(
            isEnabled = isBluetoothOn,
            onClick = onBluetoothClick,
            modifier = Modifier.weight(1f)
        )

        // Device Connection Status
        DeviceConnectionBadge(
            connectionState = connectionState,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BluetoothStatusBadge(
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (isEnabled) HeartMonitorColors.LowHeartRate else HeartMonitorColors.Critical

    Row(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = !isEnabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
            contentDescription = "Bluetooth",
            tint = color,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = stringResource(R.string.dashboard_bluetooth),
                color = HeartMonitorColors.TextSecondary,
                fontSize = 10.sp
            )
            Text(
                text = stringResource(if (isEnabled) R.string.dashboard_bluetooth_on else R.string.dashboard_bluetooth_off),
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DeviceConnectionBadge(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    val color = when (connectionState) {
        ConnectionState.CONNECTED -> HeartMonitorColors.Connected
        ConnectionState.CONNECTING -> HeartMonitorColors.HighHeartRate
        ConnectionState.SCANNING -> HeartMonitorColors.LowHeartRate
        ConnectionState.DISCONNECTED -> HeartMonitorColors.Critical
        ConnectionState.BLUETOOTH_OFF -> HeartMonitorColors.Offline
    }

    val connectedText = stringResource(R.string.status_connected)
    val connectingText = stringResource(R.string.status_connecting)
    val scanningText = stringResource(R.string.status_searching)
    val disconnectedText = stringResource(R.string.status_disconnected)

    val statusText = when (connectionState) {
        ConnectionState.CONNECTED -> connectedText
        ConnectionState.CONNECTING -> connectingText
        ConnectionState.SCANNING -> scanningText
        ConnectionState.DISCONNECTED -> disconnectedText
        ConnectionState.BLUETOOTH_OFF -> "—"
    }

    // Pulse animation for connected state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Row(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (connectionState == ConnectionState.CONNECTED)
                Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = stringResource(R.string.dashboard_device),
            tint = color,
            modifier = Modifier
                .size(20.dp)
                .scale(if (connectionState == ConnectionState.CONNECTED) pulseScale else 1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = stringResource(R.string.dashboard_device_label),
                color = HeartMonitorColors.TextSecondary,
                fontSize = 10.sp
            )
            Text(
                text = statusText,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BluetoothWarningCard(
    onEnableBluetooth: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = HeartMonitorColors.Critical.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = HeartMonitorColors.Critical.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.BluetoothDisabled,
            contentDescription = null,
            tint = HeartMonitorColors.Critical,
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.dashboard_bluetooth_off),
            color = HeartMonitorColors.Critical,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.dashboard_bluetooth_warning_message),
            color = HeartMonitorColors.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onEnableBluetooth,
            colors = ButtonDefaults.buttonColors(
                containerColor = HeartMonitorColors.LowHeartRate
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.dashboard_btn_enable_bluetooth), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HeartRateCardCompact(
    heartRate: Int?,
    isConnected: Boolean,
    minThreshold: Int = 50,
    maxThreshold: Int = 120
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = HeartMonitorColors.CardBackground,
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = HeartMonitorColors.DarkBackground,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        HeartRateDisplay(
            heartRate = heartRate,
            isConnected = isConnected,
            compact = true,
            minThreshold = minThreshold,
            maxThreshold = maxThreshold
        )
    }
}

@Composable
private fun HeartRateChart(
    heartRateHistory: List<Int>,
    modifier: Modifier = Modifier
) {
    if (heartRateHistory.isEmpty()) return

    val chartColor = HeartMonitorColors.HeartPink
    val gridColor = HeartMonitorColors.TextMuted.copy(alpha = 0.2f)

    // Calculate min/max for Y axis
    val minHr = (heartRateHistory.minOrNull() ?: 40) - 5
    val maxHr = (heartRateHistory.maxOrNull() ?: 120) + 5
    val range = (maxHr - minHr).coerceAtLeast(20)

    Column(
        modifier = modifier
            .background(
                color = HeartMonitorColors.CardBackground,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        // Title
        Text(
            text = stringResource(R.string.dashboard_last_measurements, heartRateHistory.size),
            color = HeartMonitorColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Chart
        Box(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val width = size.width
                val height = size.height
                val dataPoints = heartRateHistory.size

                if (dataPoints < 2) return@Canvas

                // Draw horizontal grid lines
                val gridLines = 4
                for (i in 0..gridLines) {
                    val y = height * i / gridLines
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                // Create path for heart rate line
                val path = Path()
                val stepX = width / (dataPoints - 1).coerceAtLeast(1)

                heartRateHistory.forEachIndexed { index, hr ->
                    val x = index * stepX
                    val normalizedY = (hr - minHr).toFloat() / range
                    val y = height - (normalizedY * height)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                // Draw the line
                drawPath(
                    path = path,
                    color = chartColor,
                    style = Stroke(width = 2.5f)
                )

                // Draw gradient fill under the line
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            chartColor.copy(alpha = 0.3f),
                            chartColor.copy(alpha = 0.05f)
                        )
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Min/Max labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.dashboard_stat_min, heartRateHistory.minOrNull() ?: 0),
                color = HeartMonitorColors.LowHeartRate,
                fontSize = 10.sp
            )
            Text(
                text = stringResource(R.string.dashboard_stat_avg, heartRateHistory.average().toInt()),
                color = HeartMonitorColors.TextMuted,
                fontSize = 10.sp
            )
            Text(
                text = stringResource(R.string.dashboard_stat_max, heartRateHistory.maxOrNull() ?: 0),
                color = HeartMonitorColors.Critical,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun HeartRateCard(
    heartRate: Int?,
    isConnected: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = HeartMonitorColors.CardBackground,
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                width = 1.dp,
                color = HeartMonitorColors.DarkBackground,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        HeartRateDisplay(
            heartRate = heartRate,
            isConnected = isConnected
        )
    }
}

@Composable
private fun InfoCardsRow(
    batteryLevel: Int?,
    sensorContact: Boolean?,
    todayCount: Int,
    lastUpdateTime: String?
) {
    val contactYes = stringResource(R.string.dashboard_contact_yes)
    val contactNo = stringResource(R.string.dashboard_contact_no)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoCard(
            emoji = "🔋",
            value = if (batteryLevel != null) "$batteryLevel%" else "--",
            label = stringResource(R.string.dashboard_info_battery),
            modifier = Modifier.weight(1f)
        )
        InfoCard(
            emoji = "👆",
            value = when (sensorContact) {
                true -> contactYes
                false -> contactNo
                null -> "--"
            },
            label = stringResource(R.string.dashboard_info_contact),
            modifier = Modifier.weight(1f)
        )
        InfoCard(
            emoji = "📊",
            value = formatNumber(todayCount),
            label = stringResource(R.string.dashboard_info_today),
            modifier = Modifier.weight(1f)
        )
        InfoCard(
            emoji = "⏱️",
            value = lastUpdateTime ?: "--",
            label = stringResource(R.string.dashboard_info_last_update),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InfoCard(
    emoji: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                color = HeartMonitorColors.CardBackground,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = emoji,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = HeartMonitorColors.TextMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun StatisticsCard(
    minHeartRate: Int?,
    avgHeartRate: Int?,
    maxHeartRate: Int?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = HeartMonitorColors.CardBackground,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.dashboard_today_stats),
            color = HeartMonitorColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                value = minHeartRate?.toString() ?: "--",
                label = stringResource(R.string.dashboard_stat_label_min),
                color = HeartMonitorColors.LowHeartRate
            )
            StatItem(
                value = avgHeartRate?.toString() ?: "--",
                label = stringResource(R.string.dashboard_stat_label_avg),
                color = HeartMonitorColors.Connected
            )
            StatItem(
                value = maxHeartRate?.toString() ?: "--",
                label = stringResource(R.string.dashboard_stat_label_max),
                color = HeartMonitorColors.Critical
            )
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = color,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = HeartMonitorColors.TextMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun OfflineCountCard(offlineCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = HeartMonitorColors.HighHeartRate.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = HeartMonitorColors.HighHeartRate.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "📦", fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.dashboard_offline_title, offlineCount),
                color = HeartMonitorColors.HighHeartRate,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.dashboard_offline_subtitle),
                color = HeartMonitorColors.TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

private fun formatNumber(number: Int): String {
    return when {
        number >= 1000000 -> String.format("%.1fM", number / 1000000.0)
        number >= 1000 -> String.format("%.1fK", number / 1000.0)
        else -> number.toString()
    }
}

private fun formatElapsedTime(timestamp: Long?): String {
    if (timestamp == null) return "--"

    val elapsed = System.currentTimeMillis() - timestamp
    val seconds = elapsed / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        seconds < 5 -> "şimdi"
        seconds < 60 -> "${seconds}s"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        else -> "${hours / 24}g"
    }
}

@Composable
private fun RrIntervalsCard(
    rrIntervals: List<Int>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = HeartMonitorColors.CardBackground,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dashboard_rr_intervals),
                color = HeartMonitorColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            // Calculate HRV (RMSSD) if we have enough data
            if (rrIntervals.size >= 2) {
                val hrv = calculateRmssd(rrIntervals)
                Text(
                    text = "HRV: ${hrv}ms",
                    color = HeartMonitorColors.LowHeartRate,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Show RR intervals as chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rrIntervals.take(5).forEach { interval ->
                Box(
                    modifier = Modifier
                        .background(
                            color = HeartMonitorColors.HeartPink.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${interval}ms",
                        color = HeartMonitorColors.HeartPink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (rrIntervals.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            val avgRr = rrIntervals.average().toInt()
            Text(
                text = stringResource(R.string.dashboard_rr_avg, avgRr),
                color = HeartMonitorColors.TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

private fun calculateRmssd(rrIntervals: List<Int>): Int {
    if (rrIntervals.size < 2) return 0

    var sumSquaredDiff = 0.0
    for (i in 1 until rrIntervals.size) {
        val diff = rrIntervals[i] - rrIntervals[i - 1]
        sumSquaredDiff += diff * diff
    }

    return kotlin.math.sqrt(sumSquaredDiff / (rrIntervals.size - 1)).toInt()
}

@Composable
private fun NoSensorContactCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = HeartMonitorColors.HighHeartRate.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = HeartMonitorColors.HighHeartRate.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "👆",
            fontSize = 32.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.dashboard_no_sensor_contact_title),
            color = HeartMonitorColors.HighHeartRate,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.dashboard_no_sensor_contact_message),
            color = HeartMonitorColors.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SignalQualityCard(quality: Float) {
    // Signal quality labels
    val excellentLabel = stringResource(R.string.dashboard_quality_excellent)
    val goodLabel = stringResource(R.string.dashboard_quality_good)
    val fairLabel = stringResource(R.string.dashboard_quality_fair)
    val lowLabel = stringResource(R.string.dashboard_quality_poor)
    val noiseLabel = stringResource(R.string.dashboard_quality_noise)

    // Kaliteye göre stage ve renk belirle
    val (stage, stageColor, stageIcon) = when {
        quality >= 0.9f -> Triple(excellentLabel, HeartMonitorColors.Connected, "🟢")
        quality >= 0.7f -> Triple(goodLabel, Color(0xFF4CAF50), "🟢")
        quality >= 0.5f -> Triple(fairLabel, Color(0xFFFFA726), "🟡")
        quality >= 0.3f -> Triple(lowLabel, Color(0xFFFF7043), "🟠")
        else -> Triple(noiseLabel, HeartMonitorColors.Critical, "🔴")
    }

    val qualityPercent = (quality * 100).toInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = stageColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = stageColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stageIcon,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_signal_quality),
                    color = HeartMonitorColors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = "$stage ($qualityPercent%)",
                color = stageColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    color = HeartMonitorColors.CardBackground,
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = quality.coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                stageColor.copy(alpha = 0.7f),
                                stageColor
                            )
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stage description
        val descExcellent = stringResource(R.string.dashboard_quality_desc_excellent)
        val descGood = stringResource(R.string.dashboard_quality_desc_good)
        val descFair = stringResource(R.string.dashboard_quality_desc_fair)
        val descLow = stringResource(R.string.dashboard_quality_desc_poor)
        val descNoise = stringResource(R.string.dashboard_quality_desc_noise)

        val description = when {
            quality >= 0.9f -> descExcellent
            quality >= 0.7f -> descGood
            quality >= 0.5f -> descFair
            quality >= 0.3f -> descLow
            else -> descNoise
        }

        Text(
            text = description,
            color = HeartMonitorColors.TextMuted,
            fontSize = 11.sp
        )
    }
}

/**
 * DashboardScreen content without Scaffold - for use inside MainScreen tabs
 */
@Composable
fun DashboardScreenContent(
    heartRate: Int?,
    connectionState: ConnectionState,
    isServiceRunning: Boolean,
    deviceMac: String,
    offlineCount: Int,
    batteryLevel: Int? = null,
    sensorContact: Boolean? = null,
    lastUpdateTime: Long? = null,
    rrIntervals: List<Int> = emptyList(),
    signalQuality: Float? = null,
    todayCount: Int = 0,
    heartRateHistory: List<Int> = emptyList(),
    minHeartRate: Int? = null,
    avgHeartRate: Int? = null,
    maxHeartRate: Int? = null,
    minThreshold: Int = 50,
    maxThreshold: Int = 120,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onEnableBluetooth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isBluetoothOff = connectionState == ConnectionState.BLUETOOTH_OFF

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HeartMonitorColors.SurfaceDark)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Status Row - Bluetooth + Device Connection
        StatusRow(
            isBluetoothOn = !isBluetoothOff,
            connectionState = connectionState,
            onBluetoothClick = onEnableBluetooth
        )

        // Bluetooth Warning Card
        if (isBluetoothOff) {
            Spacer(modifier = Modifier.height(12.dp))
            BluetoothWarningCard(onEnableBluetooth = onEnableBluetooth)
        }

        // No Sensor Contact Warning
        val isConnectedNoContact = connectionState == ConnectionState.CONNECTED && sensorContact == false
        if (isConnectedNoContact) {
            Spacer(modifier = Modifier.height(12.dp))
            NoSensorContactCard()
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Heart Rate Card (compact)
        HeartRateCardCompact(
            heartRate = heartRate,
            isConnected = connectionState == ConnectionState.CONNECTED,
            minThreshold = minThreshold,
            maxThreshold = maxThreshold
        )

        // Heart Rate Chart
        if (heartRateHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HeartRateChart(
                heartRateHistory = heartRateHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
        }

        // Signal Quality Card
        if (connectionState == ConnectionState.CONNECTED && signalQuality != null) {
            Spacer(modifier = Modifier.height(12.dp))
            SignalQualityCard(quality = signalQuality)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Info Cards Row
        InfoCardsRow(
            batteryLevel = batteryLevel,
            sensorContact = sensorContact,
            todayCount = todayCount,
            lastUpdateTime = formatElapsedTime(lastUpdateTime)
        )

        // RR Intervals Card
        if (rrIntervals.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            RrIntervalsCard(rrIntervals = rrIntervals)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Today's Statistics
        if (minHeartRate != null || avgHeartRate != null || maxHeartRate != null) {
            StatisticsCard(
                minHeartRate = minHeartRate,
                avgHeartRate = avgHeartRate,
                maxHeartRate = maxHeartRate
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Offline Count Warning
        if (offlineCount > 0) {
            OfflineCountCard(offlineCount = offlineCount)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isServiceRunning) {
                OutlinedButton(
                    onClick = onStopService,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = HeartMonitorColors.Critical
                    )
                ) {
                    Text(stringResource(R.string.dashboard_btn_stop), fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onStartService,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HeartMonitorColors.Connected
                    )
                ) {
                    Text(stringResource(R.string.dashboard_btn_start), fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(if (isServiceRunning) R.string.dashboard_service_running else R.string.dashboard_service_stopped),
            color = if (isServiceRunning) HeartMonitorColors.Connected else HeartMonitorColors.Offline,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Device Info
        Text(
            text = "Wahoo TICKR Fit • $deviceMac",
            color = HeartMonitorColors.TextMuted,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
