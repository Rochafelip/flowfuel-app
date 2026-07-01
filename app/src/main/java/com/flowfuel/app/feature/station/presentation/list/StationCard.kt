package com.flowfuel.app.feature.station.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import java.util.Locale

@Composable
fun StationCard(
    station: Station,
    onRouteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FFTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (station.type == StationType.Electric) {
                        Icons.Filled.EvStation
                    } else {
                        Icons.Filled.LocalGasStation
                    },
                    contentDescription = if (station.type == StationType.Electric) {
                        "Estação de recarga elétrica"
                    } else {
                        "Posto de combustível"
                    },
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = formatDistance(station.distanceMeters),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (station.rating != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = String.format(Locale("pt", "BR"), "%.1f", station.rating),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            FFButton(
                text = "Traçar rota",
                onClick = onRouteClick,
                variant = FFButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal fun formatDistance(meters: Int): String = if (meters < 1000) {
    "$meters m"
} else {
    String.format(Locale("pt", "BR"), "%.1f km", meters / 1000.0)
}
