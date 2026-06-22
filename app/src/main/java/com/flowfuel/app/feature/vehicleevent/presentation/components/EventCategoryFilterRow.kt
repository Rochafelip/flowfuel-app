package com.flowfuel.app.feature.vehicleevent.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.LocalCarWash
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.TireRepair
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory

private val EventCategory.filterIcon: ImageVector
    get() = when (this) {
        EventCategory.FUEL -> Icons.Outlined.LocalGasStation
        EventCategory.MAINTENANCE -> Icons.Outlined.Build
        EventCategory.OIL_CHANGE -> Icons.Outlined.Opacity
        EventCategory.WASH -> Icons.Outlined.LocalCarWash
        EventCategory.TIRES -> Icons.Outlined.TireRepair
        EventCategory.INSURANCE -> Icons.Outlined.Security
        EventCategory.TAX -> Icons.Outlined.Receipt
        EventCategory.DOCUMENTS -> Icons.AutoMirrored.Outlined.Article
        EventCategory.OTHER -> Icons.Outlined.MoreHoriz
    }

@Composable
private fun EventCategory.filterSelectedContainerColor(): Color = when (this) {
    EventCategory.FUEL -> MaterialTheme.colorScheme.primaryContainer
    EventCategory.MAINTENANCE -> MaterialTheme.colorScheme.secondaryContainer
    EventCategory.OIL_CHANGE -> MaterialTheme.colorScheme.tertiaryContainer
    EventCategory.WASH -> MaterialTheme.colorScheme.secondaryContainer
    EventCategory.TIRES -> MaterialTheme.colorScheme.tertiaryContainer
    EventCategory.INSURANCE -> MaterialTheme.colorScheme.primaryContainer
    EventCategory.TAX -> MaterialTheme.colorScheme.errorContainer
    EventCategory.DOCUMENTS -> MaterialTheme.colorScheme.surfaceVariant
    EventCategory.OTHER -> MaterialTheme.colorScheme.surfaceVariant
}

/**
 * Filtro horizontal de categoria com chip "Todas" + um chip por [EventCategory].
 *
 * @param selected null = "Todas" (sem filtro)
 */
@Composable
fun EventCategoryFilterRow(
    selected: EventCategory?,
    onSelect: (EventCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Todas") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
        items(EventCategory.entries) { category ->
            val isSelected = category == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else category) },
                label = { Text(category.label) },
                leadingIcon = { Icon(category.filterIcon, contentDescription = null) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = category.filterSelectedContainerColor(),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}
