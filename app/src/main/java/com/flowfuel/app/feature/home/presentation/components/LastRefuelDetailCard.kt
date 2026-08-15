package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.DashboardData

@Composable
fun LastRefuelDetailCard(dashboard: DashboardData, modifier: Modifier = Modifier) {
    val unit = dashboard.lastRefuelEnergyUnit ?: "L"
    val isElectric = unit.equals("kWh", ignoreCase = true)

    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Último abastecimento") {
        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
            DetailRow("Data", dashboard.lastRefuelDate?.let(::formatDate) ?: "—")
            DetailRow(
                if (isElectric) "Energia" else "Litros",
                dashboard.lastRefuelEnergyAmount?.let { "${formatDecimal2(it)} $unit" } ?: "—",
            )
            DetailRow("Valor pago", dashboard.lastRefuelAmount?.let(::formatBrl) ?: "—")
            DetailRow(
                "Preço por unidade",
                dashboard.lastRefuelPricePerUnit?.let { "${formatBrl(it)}/$unit" } ?: "—",
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Preview(showBackground = true)
@Composable
private fun LastRefuelDetailCardPreview() {
    LastRefuelDetailCard(
        dashboard = DashboardData(
            averageConsumption = 12.5,
            consumptionUnit = "km/L",
            totalSpent = 1200.0,
            fuelSpent = 1200.0,
            totalRefuels = 5,
            lastRefuelDate = "2026-08-15",
            lastRefuelEnergyAmount = 42.3,
            lastRefuelAmount = 294.83,
            lastRefuelEnergyUnit = "L",
            lastRefuelPricePerUnit = 6.97,
        ),
    )
}
