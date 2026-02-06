package net.hrapp.hr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.hrapp.hr.R

enum class ConnectionState {
    BLUETOOTH_OFF,
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

@Composable
fun ConnectionStatus(
    state: ConnectionState,
    deviceName: String?,
    deviceMac: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
            // Status indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                            ConnectionState.CONNECTING -> Color(0xFFFFC107)
                            ConnectionState.SCANNING -> Color(0xFF2196F3)
                            ConnectionState.DISCONNECTED -> Color(0xFFF44336)
                            ConnectionState.BLUETOOTH_OFF -> Color(0xFF9E9E9E)
                        }
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = when (state) {
                        ConnectionState.CONNECTED -> stringResource(R.string.status_connected)
                        ConnectionState.CONNECTING -> stringResource(R.string.status_connecting)
                        ConnectionState.SCANNING -> stringResource(R.string.status_searching)
                        ConnectionState.DISCONNECTED -> stringResource(R.string.status_disconnected)
                        ConnectionState.BLUETOOTH_OFF -> stringResource(R.string.status_bluetooth_off)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (deviceName != null || deviceMac != null) {
                    Text(
                        text = deviceName ?: deviceMac ?: "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
