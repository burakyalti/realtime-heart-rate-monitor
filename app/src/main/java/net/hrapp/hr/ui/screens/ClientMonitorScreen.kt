package net.hrapp.hr.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier
) {
    // Check if URL is configured (not the default example.com)
    val isUrlConfigured = !liveUrl.contains("example.com")

    if (!isUrlConfigured) {
        // Show placeholder when URL is not configured
        UrlNotConfiguredPlaceholder(modifier = modifier)
    } else {
        // Show WebView when URL is configured
        WebViewContent(liveUrl = liveUrl, modifier = modifier)
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
