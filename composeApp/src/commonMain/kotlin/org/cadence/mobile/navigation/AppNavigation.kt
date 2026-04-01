package org.cadence.mobile.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.cadence.mobile.theme.Background
import org.cadence.mobile.theme.Gold
import org.cadence.mobile.theme.Muted
import org.cadence.mobile.theme.Surface

@Composable
fun AppNavigationBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
) {
    NavigationBar(
        containerColor = Surface,
        contentColor = Gold,
    ) {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Gold,
                    selectedTextColor = Gold,
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted,
                    indicatorColor = Background,
                ),
            )
        }
    }
}
