package com.flowfuel.app.feature.station.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
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
    FFCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StationTypeBadge(station.type)
            Text(
                text = formatDistance(station.distanceMeters),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(FFTheme.spacing.xs))
        Text(station.name, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(FFTheme.spacing.sm))
        FFButton(
            text = "Traçar rota",
            onClick = onRouteClick,
            variant = FFButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StationTypeBadge(type: StationType, modifier: Modifier = Modifier) {
    val content = type.badgeContent()
    val (containerColor, contentColor) = when (type) {
        StationType.Fuel -> FFTheme.semanticColors.warning to FFTheme.semanticColors.onWarning
        StationType.Electric -> FFTheme.semanticColors.info to FFTheme.semanticColors.onInfo
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(FFTheme.spacing.xs),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FFTheme.spacing.sm, vertical = FFTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = content.icon,
                contentDescription = content.contentDescription,
                modifier = Modifier.size(16.dp),
            )
            Text(content.label, style = MaterialTheme.typography.labelMedium)
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
