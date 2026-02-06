package net.hrapp.hr.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.hrapp.hr.R
import net.hrapp.hr.ui.theme.HeartMonitorColors

@Composable
fun HeartRateDisplay(
    heartRate: Int?,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    minThreshold: Int = 50,
    maxThreshold: Int = 120
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (heartRate != null && heartRate > 0) {
                    60000 / heartRate
                } else {
                    1000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // "Yüksek" uyarısı için maxThreshold'a yakın değer (20 BPM altı)
    val warningThreshold = maxThreshold - 20

    // Determine status color based on heart rate and thresholds
    val statusColor = when {
        !isConnected || heartRate == null -> HeartMonitorColors.Offline
        heartRate < minThreshold -> HeartMonitorColors.LowHeartRate
        heartRate > maxThreshold -> HeartMonitorColors.Critical
        heartRate > warningThreshold -> HeartMonitorColors.HighHeartRate
        else -> HeartMonitorColors.Connected
    }

    val statusText = when {
        !isConnected || heartRate == null -> stringResource(R.string.hr_no_connection)
        heartRate < minThreshold -> stringResource(R.string.hr_status_low)
        heartRate > maxThreshold -> stringResource(R.string.hr_status_high_warning)
        heartRate > warningThreshold -> stringResource(R.string.hr_status_high)
        else -> stringResource(R.string.hr_status_normal)
    }

    // Compact mode sizes
    val iconSize = if (compact) 36.dp else 56.dp
    val fontSize = if (compact) 56.sp else 96.sp
    val bpmFontSize = if (compact) 16.sp else 24.sp
    val topSpacing = if (compact) 8.dp else 24.dp
    val bottomSpacing = if (compact) 8.dp else 16.dp

    if (compact) {
        // Compact horizontal layout
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Heart icon
            Box(contentAlignment = Alignment.Center) {
                if (isConnected && heartRate != null) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier
                            .size(iconSize)
                            .scale(scale * 1.3f)
                            .blur(12.dp),
                        tint = HeartMonitorColors.HeartPink.copy(alpha = 0.5f)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Heart",
                    modifier = Modifier
                        .size(iconSize)
                        .scale(if (isConnected && heartRate != null) scale else 1f),
                    tint = if (isConnected) HeartMonitorColors.HeartPink else HeartMonitorColors.Offline
                )
            }

            Spacer(modifier = Modifier.size(16.dp))

            // Heart rate number
            Text(
                text = heartRate?.toString() ?: "--",
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = if (isConnected && heartRate != null) statusColor else HeartMonitorColors.Offline
            )

            Spacer(modifier = Modifier.size(8.dp))

            Column {
                Text(
                    text = stringResource(R.string.hr_unit_bpm),
                    fontSize = bpmFontSize,
                    color = HeartMonitorColors.TextSecondary
                )
                StatusBadge(
                    text = statusText,
                    color = statusColor,
                    compact = true
                )
            }
        }
    } else {
        // Original vertical layout
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Heart Icon with Glow
            Box(contentAlignment = Alignment.Center) {
                if (isConnected && heartRate != null) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier
                            .size(iconSize)
                            .scale(scale * 1.3f)
                            .blur(20.dp)
                            .offset(y = 4.dp),
                        tint = HeartMonitorColors.HeartPink.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Heart",
                    modifier = Modifier
                        .size(iconSize)
                        .scale(if (isConnected && heartRate != null) scale else 1f),
                    tint = if (isConnected) HeartMonitorColors.HeartPink else HeartMonitorColors.Offline
                )
            }

            Spacer(modifier = Modifier.height(topSpacing))

            // Large Heart Rate Number with Glow
            Box(contentAlignment = Alignment.Center) {
                if (isConnected && heartRate != null) {
                    Text(
                        text = heartRate.toString(),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = statusColor.copy(alpha = 0.4f),
                        modifier = Modifier.blur(16.dp)
                    )
                }
                Text(
                    text = heartRate?.toString() ?: "--",
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected && heartRate != null) statusColor else HeartMonitorColors.Offline
                )
            }

            Text(
                text = stringResource(R.string.hr_unit_bpm),
                fontSize = bpmFontSize,
                fontWeight = FontWeight.Normal,
                color = HeartMonitorColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(bottomSpacing))

            StatusBadge(text = statusText, color = statusColor)
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(if (compact) 12.dp else 20.dp)
            )
            .padding(
                horizontal = if (compact) 8.dp else 16.dp,
                vertical = if (compact) 4.dp else 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = color,
            fontSize = if (compact) 10.sp else 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
