package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.flowfuel.app.core.designsystem.theme.FFTheme

data class FFBottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badgeCount: Int? = null,
)

/**
 * `floatingActionButton`, quando fornecido, é inserido inline entre as abas —
 * logo após a aba cuja rota é [fabAfterRoute] (ou, se nulo, "docado" na ponta
 * da barra, comportamento nativo de `BottomAppBar`). As abas em [items]
 * continuam todas visíveis ao redor dele.
 */
@Composable
fun FFBottomBar(
    items: List<FFBottomItem>,
    currentRoute: String?,
    onSelect: (FFBottomItem) -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: @Composable (() -> Unit)? = null,
    fabAfterRoute: String? = null,
) {
    BottomAppBar(
        modifier = modifier,
        floatingActionButton = floatingActionButton.takeIf { fabAfterRoute == null },
        actions = {
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
                if (floatingActionButton != null && fabAfterRoute == item.route) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = FFTheme.spacing.xs)
                            .fillMaxHeight()
                            .zIndex(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        floatingActionButton()
                    }
                }
            }
        },
    )
}
