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
fun RecentActivityCard(
    items: List<VehicleTimelineItem>,
    vehicleEnergyType: String,
    modifier: Modifier = Modifier,
) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Atividade recente") {
        if (items.isEmpty()) {
            Text(
                text = "Nenhuma atividade registrada ainda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
                items.forEach { item -> RecentActivityRow(item, vehicleEnergyType) }
            }
        }
    }
}

private data class RowData(
    val icon: ImageVector,
    val title: String,
    val amount: Double?,
    val date: String,
    /** Litros/kWh + preço por unidade, só para abastecimentos. */
    val detail: String? = null,
)

@Composable
private fun RecentActivityRow(item: VehicleTimelineItem, vehicleEnergyType: String) {
    val row = when (item) {
        is VehicleTimelineItem.RefuelEntry -> {
            val unit = refuelUnit(item.refuel.refuelType, vehicleEnergyType)
            val litersLabel = "%.2f %s".format(item.refuel.energyAmount, unit).replace('.', ',')
            RowData(
                icon = Icons.Default.LocalGasStation,
                title = "Abastecimento",
                amount = item.refuel.totalPrice,
                date = item.refuel.date,
                detail = "$litersLabel · ${formatBrl(item.refuel.pricePerUnit)}/$unit",
            )
        }
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
            Column {
                Text(formatDate(row.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                row.detail?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        trailingContent = {
            row.amount?.let {
                Text(formatBrl(it), style = FFTheme.numericTypography.numericSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** Mesma regra de [com.flowfuel.app.feature.home.presentation.HomeScreen]: HYBRID usa o refuelType do próprio abastecimento; os demais, o tipo de energia do veículo. */
private fun refuelUnit(refuelType: String?, vehicleEnergyType: String): String = when {
    refuelType == "ELECTRIC" -> "kWh"
    refuelType == "FUEL" -> "L"
    vehicleEnergyType.equals("ELECTRIC", ignoreCase = true) -> "kWh"
    else -> "L"
}

private fun categoryIcon(category: EventCategory): ImageVector = when (category) {
    EventCategory.FUEL -> Icons.Default.LocalGasStation
    EventCategory.MAINTENANCE, EventCategory.OIL_CHANGE, EventCategory.TIRES -> Icons.Default.Build
    else -> Icons.Default.Receipt
}

@Preview(showBackground = true)
@Composable
private fun RecentActivityCardPreview() {
    RecentActivityCard(items = emptyList(), vehicleEnergyType = "COMBUSTION")
}
