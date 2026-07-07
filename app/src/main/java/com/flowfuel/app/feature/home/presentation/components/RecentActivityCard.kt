package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem

@Composable
fun RecentActivityCard(items: List<VehicleTimelineItem>, modifier: Modifier = Modifier) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Atividade recente") {
        if (items.isEmpty()) {
            Text(
                text = "Nenhuma atividade registrada ainda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
                items.forEach { item -> RecentActivityRow(item) }
            }
        }
    }
}

private data class RowData(val icon: ImageVector, val title: String, val amount: Double?, val date: String)

@Composable
private fun RecentActivityRow(item: VehicleTimelineItem) {
    val row = when (item) {
        is VehicleTimelineItem.RefuelEntry -> RowData(
            icon = Icons.Default.LocalGasStation,
            title = "Abastecimento",
            amount = item.refuel.totalPrice,
            date = item.refuel.date,
        )
        is VehicleTimelineItem.EventEntry -> RowData(
            icon = categoryIcon(item.event.category),
            title = item.event.title,
            amount = item.event.amount,
            date = item.event.eventDate,
        )
    }
    ListItem(
        leadingContent = { Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(row.title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Text(formatDate(row.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            row.amount?.let {
                Text(formatBrl(it), style = FFTheme.numericTypography.numericSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

private fun categoryIcon(category: EventCategory): ImageVector = when (category) {
    EventCategory.FUEL -> Icons.Default.LocalGasStation
    EventCategory.MAINTENANCE, EventCategory.OIL_CHANGE, EventCategory.TIRES -> Icons.Default.Build
    else -> Icons.Default.Receipt
}

@Preview(showBackground = true)
@Composable
private fun RecentActivityCardPreview() {
    RecentActivityCard(items = emptyList())
}
