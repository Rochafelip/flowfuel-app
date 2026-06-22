package com.flowfuel.app.feature.vehicleevent.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowfuel.app.feature.vehicleevent.domain.model.EventDateFilter
import com.flowfuel.app.feature.vehicleevent.domain.model.chipLabel

private val presetFilters = listOf(
    EventDateFilter.All,
    EventDateFilter.Last30Days,
    EventDateFilter.Last3Months,
    EventDateFilter.ThisYear,
)

/**
 * Filtro horizontal temporal.
 *
 * @param selected filtro ativo — [EventDateFilter.All] = sem filtro
 * @param onSelect chamado para Tudo / 30 dias / 3 meses / Este ano
 * @param onCustomClick chamado ao tocar no chip "Personalizado" (abre o date picker)
 */
@Composable
fun EventDateFilterRow(
    selected: EventDateFilter,
    onSelect: (EventDateFilter) -> Unit,
    onCustomClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(presetFilters.size) { index ->
            val filter = presetFilters[index]
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.chipLabel()) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        item {
            val isCustom = selected is EventDateFilter.Custom
            FilterChip(
                selected = isCustom,
                onClick = onCustomClick,
                label = { Text(if (isCustom) selected.chipLabel() else "Personalizado") },
                leadingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}
