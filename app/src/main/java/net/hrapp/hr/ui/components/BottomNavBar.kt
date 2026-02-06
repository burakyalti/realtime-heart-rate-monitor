package net.hrapp.hr.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import net.hrapp.hr.R
import net.hrapp.hr.ui.theme.HeartMonitorColors

sealed class BottomNavItem(
    val route: String,
    @StringRes val titleResId: Int,
    val icon: ImageVector
) {
    data object Live : BottomNavItem("live", R.string.nav_live, Icons.Default.Favorite)
    data object Monitor : BottomNavItem("monitor", R.string.nav_monitor, Icons.Default.Visibility)
    data object Settings : BottomNavItem("settings", R.string.nav_settings, Icons.Default.Settings)
}

@Composable
fun BottomNavBar(
    currentRoute: String,
    isServerMode: Boolean,
    onItemClick: (BottomNavItem) -> Unit
) {
    // Server modda: Canlı, İzleme, Ayarlar
    // Client modda: İzleme, Ayarlar (Canlı gizli)
    val items = if (isServerMode) {
        listOf(BottomNavItem.Live, BottomNavItem.Monitor, BottomNavItem.Settings)
    } else {
        listOf(BottomNavItem.Monitor, BottomNavItem.Settings)
    }

    NavigationBar(
        containerColor = HeartMonitorColors.CardBackground,
        contentColor = HeartMonitorColors.TextPrimary
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            val title = stringResource(item.titleResId)

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = title
                    )
                },
                label = { Text(title) },
                selected = selected,
                onClick = { onItemClick(item) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = HeartMonitorColors.HeartPink,
                    selectedTextColor = HeartMonitorColors.HeartPink,
                    unselectedIconColor = HeartMonitorColors.TextMuted,
                    unselectedTextColor = HeartMonitorColors.TextMuted,
                    indicatorColor = HeartMonitorColors.HeartPink.copy(alpha = 0.2f)
                )
            )
        }
    }
}
