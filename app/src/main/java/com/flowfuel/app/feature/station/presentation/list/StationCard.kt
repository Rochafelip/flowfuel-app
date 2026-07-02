package com.flowfuel.app.feature.station.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFCard
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
    val content = station.type.badgeContent()
    val iconTint = when (station.type) {
        StationType.Fuel -> FFTheme.semanticColors.warning
        StationType.Electric -> FFTheme.semanticColors.info
    }
    FFCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = content.icon,
                contentDescription = content.contentDescription,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDistance(station.distanceMeters),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(FFTheme.spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (station.rating != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = FFTheme.semanticColors.warning,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(formatRating(station.rating), style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Spacer(Modifier)
            }
            IconButton(onClick = onRouteClick) {
                Icon(imageVector = Icons.Filled.Navigation, contentDescription = "Traçar rota")
            }
        }
    }
}

internal data class StationTypeBadgeContent(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String,
)

internal fun StationType.badgeContent(): StationTypeBadgeContent = when (this) {
    StationType.Fuel -> StationTypeBadgeContent(
        label = "Combustível",
        icon = Icons.Filled.LocalGasStation,
        contentDescription = "Posto de combustível",
    )
    StationType.Electric -> StationTypeBadgeContent(
        label = "Elétrico",
        icon = Icons.Filled.EvStation,
        contentDescription = "Estação de recarga elétrica",
    )
}

internal fun formatDistance(meters: Int): String = if (meters < 1000) {
    "$meters m"
} else {
    String.format(Locale("pt", "BR"), "%.1f km", meters / 1000.0)
}

internal fun formatRating(rating: Double): String =
    String.format(Locale("pt", "BR"), "%.1f", rating)
