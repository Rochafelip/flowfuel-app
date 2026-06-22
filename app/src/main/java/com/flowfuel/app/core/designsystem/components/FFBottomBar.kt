package com.flowfuel.app.core.designsystem.components

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

data class FFBottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badgeCount: Int? = null,
)

@Composable
fun FFBottomBar(
    items: List<FFBottomItem>,
    currentRoute: String?,
    onSelect: (FFBottomItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item) },
                icon = {
                    BadgedBox(
                        badge = {
                            val count = item.badgeCount
                            if (count != null && count > 0) {
                                Badge { Text(if (count > 99) "99+" else count.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.icon,
                            contentDescription = item.label
                        )
                    }
                },
                label = { Text(item.label) },
                alwaysShowLabel = true,
            )
        }
    }
}
