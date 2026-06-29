// app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/components/RefuelTimelineCard.kt
package com.flowfuel.app.feature.vehicleevent.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RefuelTimelineCard(refuel: RefuelItem, onClick: () -> Unit) {
    FFCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RefuelTypeChip(refuelType = refuel.refuelType)
                val unit = if (refuel.refuelType == "ELECTRIC") "kWh" else "L"
                Text(
                    text = "${refuelEnergyFormat.format(refuel.energyAmount)} $unit · ${refuelCurrencyFormat.format(refuel.pricePerUnit)}/$unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = refuelCurrencyFormat.format(refuel.totalPrice),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = refuelFormatDate(refuel.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (refuel.odometer != null) {
                    Text(
                        text = "${refuelOdometerFormat.format(refuel.odometer.toLong())} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RefuelTypeChip(refuelType: String?) {
    val label = if (refuelType == "ELECTRIC") "Carga" else "Abastecimento"
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = FFTheme.extraShapes.pill,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FFTheme.spacing.sm, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalGasStation,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private val refuelCurrencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
private val refuelEnergyFormat = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
    maximumFractionDigits = 2
    minimumFractionDigits = 2
}
private val refuelOdometerFormat = NumberFormat.getIntegerInstance(Locale("pt", "BR"))
private val refuelDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("pt", "BR"))

private fun refuelFormatDate(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(refuelDateFormatter) }.getOrDefault(isoDate)
