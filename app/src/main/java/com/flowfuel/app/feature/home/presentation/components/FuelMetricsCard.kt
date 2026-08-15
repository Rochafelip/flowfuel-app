package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.HybridConsumptionBreakdown

@Composable
fun FuelMetricsCard(breakdown: HybridConsumptionBreakdown, modifier: Modifier = Modifier) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Combustível x Elétrico") {
        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md)) {
            FuelMetricsBlock(
                icon = Icons.Default.LocalGasStation,
                label = "Combustível",
                consumption = breakdown.fuelConsumption,
                consumptionUnit = breakdown.fuelConsumptionUnit ?: "km/L",
                averagePrice = breakdown.fuelAveragePrice,
                priceUnit = breakdown.fuelPriceUnit ?: "R$/L",
                totalSpent = breakdown.fuelTotalSpent,
            )
            FuelMetricsBlock(
                icon = Icons.Default.Bolt,
                label = "Elétrico",
                consumption = breakdown.electricConsumption,
                consumptionUnit = breakdown.electricConsumptionUnit ?: "km/kWh",
                averagePrice = breakdown.electricAveragePrice,
                priceUnit = breakdown.electricPriceUnit ?: "R$/kWh",
                totalSpent = breakdown.electricTotalSpent,
            )
        }
    }
}

@Composable
private fun FuelMetricsBlock(
    icon: ImageVector,
    label: String,
    consumption: Double?,
    consumptionUnit: String,
    averagePrice: Double?,
    priceUnit: String,
    totalSpent: Double?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
        Row {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MetricRow("Consumo médio", consumption?.let { "${formatDecimal2(it)} $consumptionUnit" } ?: "—")
        MetricRow("Preço médio", averagePrice?.let { "${formatBrl(it)} $priceUnit" } ?: "—")
        MetricRow("Total gasto", totalSpent?.let(::formatBrl) ?: "—")
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Preview(showBackground = true)
@Composable
private fun FuelMetricsCardPreview() {
    FuelMetricsCard(
        breakdown = HybridConsumptionBreakdown(
            fuelConsumption = 12.5,
            fuelConsumptionUnit = "km/L",
            fuelAveragePrice = 5.89,
            fuelPriceUnit = "R$/L",
            fuelTotalSpent = 890.0,
            electricConsumption = 6.2,
            electricConsumptionUnit = "km/kWh",
            electricAveragePrice = 0.85,
            electricPriceUnit = "R$/kWh",
            electricTotalSpent = 210.0,
        ),
    )
}
