package net.hrapp.hr.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import net.hrapp.hr.R
import net.hrapp.hr.ui.theme.HeartMonitorColors

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ClientMonitorScreen(
    liveUrl: String,
    connectivityType: String = "ok",
    staleSeconds: Int = 0,
    modifier: Modifier = Modifier
) {
    // Check if URL is configured (not the default example.com)
    val isUrlConfigured = !liveUrl.contains("example.com")

    if (!isUrlConfigured) {
        // Show placeholder when URL is not configured
        UrlNotConfiguredPlaceholder(modifier = modifier)
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            // Show WebView when URL is configured
            WebViewContent(liveUrl = liveUrl, modifier = Modifier.fillMaxSize())

            // Connectivity alert overlay
            var isDismissed by remember(connectivityType) { mutableStateOf(false) }
            val showOverlay = !isDismissed && connectivityType != "ok"

            AnimatedVisibility(
                visible = showOverlay,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ConnectivityOverlay(
                    connectivityType = connectivityType,
                    staleSeconds = staleSeconds,
                    onDismiss = { isDismissed = true }
                )
            }
        }
    }
}

@Composable
private fun UrlNotConfiguredPlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HeartMonitorColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Heart icon with pulse animation feel
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = HeartMonitorColors.HeartPink.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.client_not_configured_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = HeartMonitorColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.client_not_configured_message),
                fontSize = 14.sp,
                color = HeartMonitorColors.TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Settings hint
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = HeartMonitorColors.TextMuted
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.client_not_configured_hint),
                fontSize = 12.sp,
                color = HeartMonitorColors.TextMuted
            )
        }
    }
}

@Composable
private fun WebViewContent(
    liveUrl: String,
    modifier: Modifier = Modifier
) {
    var loadingProgress by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HeartMonitorColors.Background)
    ) {
        // Loading indicator
        if (loadingProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = HeartMonitorColors.HeartPink,
                trackColor = HeartMonitorColors.CardBackground
            )
        }

        // WebView
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                        }

                        webViewClient = WebViewClient()

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadingProgress = newProgress
                            }
                        }

                        // Dark mode icin arka plan
                        setBackgroundColor(android.graphics.Color.parseColor("#0D0D0D"))

                        loadUrl(liveUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ConnectivityOverlay(
    connectivityType: String,
    staleSeconds: Int,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xEE1a1a2e))
                .padding(16.dp)
        ) {
            // Header row: icon + title + close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = HeartMonitorColors.HighHeartRate,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (connectivityType == "stale")
                        stringResource(R.string.popup_stale_title)
                    else
                        stringResource(R.string.popup_api_error_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeartMonitorColors.HighHeartRate,
                    modifier = Modifier.weight(1f)
                )

                // Close button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(HeartMonitorColors.TextMuted.copy(alpha = 0.3f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = HeartMonitorColors.TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (connectivityType == "stale") {
                // Stale data info
                if (staleSeconds > 0) {
                    Text(
                        text = stringResource(R.string.popup_stale_last_data, staleSeconds),
                        fontSize = 13.sp,
                        color = HeartMonitorColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = stringResource(R.string.popup_stale_causes),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HeartMonitorColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                val causes = listOf(
                    R.string.popup_stale_cause_sensor,
                    R.string.popup_stale_cause_connection,
                    R.string.popup_stale_cause_internet,
                    R.string.popup_stale_cause_app
                )
                causes.forEach { causeRes ->
                    Text(
                        text = "• ${stringResource(causeRes)}",
                        fontSize = 12.sp,
                        color = HeartMonitorColors.TextMuted,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            } else {
                // API error info
                Text(
                    text = stringResource(R.string.popup_api_error_causes),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HeartMonitorColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                val causes = listOf(
                    R.string.popup_api_error_cause_internet,
                    R.string.popup_api_error_cause_server
                )
                causes.forEach { causeRes ->
                    Text(
                        text = "• ${stringResource(causeRes)}",
                        fontSize = 12.sp,
                        color = HeartMonitorColors.TextMuted,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}
