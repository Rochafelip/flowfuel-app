package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceItem
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceType
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.presentation.components.icon

@Composable
fun UpcomingEventsSection(
    items: List<UpcomingMaintenanceItem>,
    onCardClick: (UpcomingMaintenanceType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
        Text(
            text = "Próximos eventos",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
            items.forEach { item ->
                UpcomingEventCard(
                    item = item,
                    onClick = { onCardClick(item.type) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun UpcomingEventCard(item: UpcomingMaintenanceItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val presentation = item.toPresentation()
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(presentation.accent.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = presentation.icon,
                    contentDescription = null,
                    tint = presentation.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = presentation.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = presentation.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private data class CardPresentation(
    val icon: ImageVector,
    val accent: Color,
    val title: String,
    val subtitle: String,
)

@Composable
private fun UpcomingMaintenanceItem.toPresentation(): CardPresentation {
    val accent = when {
        isOverdue -> MaterialTheme.colorScheme.error
        type == UpcomingMaintenanceType.OIL_CHANGE -> FFTheme.semanticColors.warning
        type == UpcomingMaintenanceType.TIRE_ROTATION -> FFTheme.semanticColors.success
        else -> MaterialTheme.colorScheme.secondary
    }
    val icon = when (type) {
        UpcomingMaintenanceType.OIL_CHANGE -> EventCategory.OIL_CHANGE.icon
        UpcomingMaintenanceType.TIRE_ROTATION -> EventCategory.TIRES.icon
        UpcomingMaintenanceType.LICENSING -> EventCategory.DOCUMENTS.icon
    }
    val title = when (type) {
        UpcomingMaintenanceType.OIL_CHANGE -> "Troca de óleo"
        UpcomingMaintenanceType.TIRE_ROTATION -> "Rodízio de pneus"
        UpcomingMaintenanceType.LICENSING -> "Licenciamento"
    }
    val subtitle = when {
        needsSetup -> "Defina a data de licenciamento"
        isOverdue && remainingKm != null -> "Atrasado ${-remainingKm} km"
        isOverdue && remainingDays != null -> overdueDaysLabel(-remainingDays)
        remainingKm != null -> "Em $remainingKm km"
        remainingDays != null -> dueDaysLabel(remainingDays)
        else -> "—"
    }
    return CardPresentation(icon, accent, title, subtitle)
}

private fun dueDaysLabel(days: Int): String = when (days) {
    0 -> "Vence hoje"
    1 -> "Vence em 1 dia"
    else -> "Vence em $days dias"
}

private fun overdueDaysLabel(days: Int): String = when (days) {
    0 -> "Venceu hoje"
    1 -> "Venceu há 1 dia"
    else -> "Venceu há $days dias"
}

@Preview(showBackground = true)
@Composable
private fun UpcomingEventsSectionPreview() {
    UpcomingEventsSection(
        items = listOf(
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.OIL_CHANGE, remainingKm = 320),
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.TIRE_ROTATION, remainingKm = 900),
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, remainingDays = 18),
        ),
        onCardClick = {},
    )
}

@Preview(showBackground = true, name = "Overdue + prompt")
@Composable
private fun UpcomingEventsSectionOverduePreview() {
    UpcomingEventsSection(
        items = listOf(
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.OIL_CHANGE, remainingKm = -150, isOverdue = true),
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.TIRE_ROTATION, remainingKm = 4200),
            UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, needsSetup = true),
        ),
        onCardClick = {},
    )
}
