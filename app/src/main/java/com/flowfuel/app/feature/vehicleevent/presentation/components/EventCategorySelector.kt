package com.flowfuel.app.feature.vehicleevent.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory

@Composable
private fun EventCategory.selectedContainerColor(): Color = when (this) {
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

@Composable
fun EventCategorySelector(
    selected: EventCategory,
    onSelect: (EventCategory) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    categories: List<EventCategory> = EventCategory.entries,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(categories) { category ->
            val isSelected = category == selected
            FilterChip(
                selected = isSelected,
                onClick = { if (enabled) onSelect(category) },
                label = { Text(category.label) },
                leadingIcon = {
                    Icon(category.icon, contentDescription = null)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = category.selectedContainerColor(),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}
