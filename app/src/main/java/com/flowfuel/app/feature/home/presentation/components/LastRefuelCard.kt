package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.DashboardData

@Composable
fun LastRefuelCard(dashboard: DashboardData, modifier: Modifier = Modifier) {
    FFCard(modifier = modifier, title = "Último abastecimento", variant = FFCardVariant.Flat) {
        if (dashboard.totalRefuels == 0 || dashboard.lastRefuelDate == null) {
            Text(
                text = "Nenhum abastecimento registrado ainda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                LastRefuelRow(label = "Data", value = formatDate(dashboard.lastRefuelDate))
                if (dashboard.lastRefuelEnergyAmount != null) {
                    val unit = dashboard.lastRefuelEnergyUnit ?: "L"
                    LastRefuelRow(
                        label = if (unit == "kWh") "Energia" else "Litros",
                        value = "%.2f %s".format(dashboard.lastRefuelEnergyAmount, unit).replace('.', ','),
                    )
                }
                if (dashboard.lastRefuelAmount != null) {
                    LastRefuelRow(label = "Valor pago", value = formatBrl(dashboard.lastRefuelAmount))
                }
                if (dashboard.lastRefuelEnergyAmount != null && dashboard.lastRefuelAmount != null
                    && dashboard.lastRefuelEnergyAmount > 0.0
                ) {
                    val pricePerUnit = dashboard.lastRefuelAmount / dashboard.lastRefuelEnergyAmount
                    LastRefuelRow(
                        label = if (dashboard.lastRefuelEnergyUnit == "kWh") "Preço/kWh" else "Preço/litro",
                        value = formatBrl(pricePerUnit),
                        highlight = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun LastRefuelRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
