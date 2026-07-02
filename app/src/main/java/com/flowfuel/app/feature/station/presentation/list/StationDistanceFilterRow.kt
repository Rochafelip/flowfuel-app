package com.flowfuel.app.feature.station.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowfuel.app.feature.station.domain.model.STATION_RADIUS_PRESETS_METERS

internal fun formatRadiusLabel(radiusMeters: Int): String = "${radiusMeters / 1000} km"

/**
 * Filtro horizontal de raio de busca — presets fixos (sem raio customizado).
 */
@Composable
fun StationDistanceFilterRow(
    selectedRadiusMeters: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(STATION_RADIUS_PRESETS_METERS) { radiusMeters ->
            FilterChip(
                selected = radiusMeters == selectedRadiusMeters,
                onClick = { onSelect(radiusMeters) },
                label = { Text(formatRadiusLabel(radiusMeters)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}
