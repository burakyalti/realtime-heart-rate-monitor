package net.hrapp.hr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import net.hrapp.hr.R
import net.hrapp.hr.ble.ScannedDevice
import net.hrapp.hr.ui.theme.HeartMonitorColors

@Composable
fun DevicePickerDialog(
    devices: List<ScannedDevice>,
    isScanning: Boolean,
    onDeviceSelect: (ScannedDevice) -> Unit,
    onDismiss: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    // Sort devices: Heart Rate devices first, then by signal strength
    val sortedDevices = devices.sortedWith(
        compareByDescending<ScannedDevice> { it.hasHeartRateService }
            .thenByDescending { it.rssi }
    ).distinctBy { it.address }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = HeartMonitorColors.CardBackground
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.device_picker_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = HeartMonitorColors.TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.device_picker_close),
                            tint = HeartMonitorColors.TextMuted
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = HeartMonitorColors.DarkBackground
                )

                // Scan status
                if (isScanning) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = HeartMonitorColors.HeartPink,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.device_picker_scanning),
                            fontSize = 14.sp,
                            color = HeartMonitorColors.TextMuted
                        )
                    }
                }

                // Device list
                if (sortedDevices.isEmpty() && !isScanning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = HeartMonitorColors.TextMuted
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.device_picker_no_devices),
                                color = HeartMonitorColors.TextMuted,
                                fontSize = 14.sp
                            )
                            Text(
                                text = stringResource(R.string.device_picker_start_hint),
                                color = HeartMonitorColors.TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    ) {
                        items(sortedDevices) { device ->
                            DeviceListItem(
                                device = device,
                                onClick = { onDeviceSelect(device) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action button
                Button(
                    onClick = if (isScanning) onStopScan else onStartScan,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) HeartMonitorColors.TextMuted else HeartMonitorColors.HeartPink
                    )
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isScanning)
                            stringResource(R.string.device_picker_btn_stop)
                        else
                            stringResource(R.string.device_picker_btn_start),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceListItem(
    device: ScannedDevice,
    onClick: () -> Unit
) {
    val signalLabel = when {
        device.rssi >= -50 -> stringResource(R.string.device_picker_signal_excellent)
        device.rssi >= -70 -> stringResource(R.string.device_picker_signal_good)
        device.rssi >= -85 -> stringResource(R.string.device_picker_signal_fair)
        else -> stringResource(R.string.device_picker_signal_weak)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon with Heart Rate indicator
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (device.hasHeartRateService)
                        HeartMonitorColors.HeartPink.copy(alpha = 0.2f)
                    else
                        HeartMonitorColors.TextMuted.copy(alpha = 0.2f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (device.hasHeartRateService)
                    Icons.Default.Favorite
                else
                    Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (device.hasHeartRateService)
                    HeartMonitorColors.HeartPink
                else
                    HeartMonitorColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Device info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = device.name,
                fontWeight = FontWeight.Medium,
                color = HeartMonitorColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = device.address,
                fontSize = 12.sp,
                color = HeartMonitorColors.TextMuted
            )
        }

        // Signal strength
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${device.rssi} dBm",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = getSignalColor(device.rssi)
            )
            Text(
                text = signalLabel,
                fontSize = 10.sp,
                color = HeartMonitorColors.TextMuted
            )
        }
    }
}

private fun getSignalColor(rssi: Int): Color {
    return when {
        rssi >= -50 -> HeartMonitorColors.Connected // Excellent
        rssi >= -70 -> Color(0xFF4CAF50) // Good (green)
        rssi >= -85 -> Color(0xFFFFC107) // Fair (yellow)
        else -> Color(0xFFF44336) // Poor (red)
    }
}
