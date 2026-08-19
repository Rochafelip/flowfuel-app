package com.flowfuel.app.feature.station.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.flowfuel.app.core.designsystem.theme.FFTheme
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FFTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm, Alignment.CenterHorizontally),
    ) {
        STATION_RADIUS_PRESETS_METERS.forEach { radiusMeters ->
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
