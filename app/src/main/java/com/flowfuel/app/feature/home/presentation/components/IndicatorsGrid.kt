package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.components.FFStatTile
import com.flowfuel.app.core.designsystem.theme.FFTheme

data class IndicatorItem(
    val label: String,
    val value: String,
    val unit: String? = null,
)

@Composable
fun IndicatorsGrid(
    consumption: IndicatorItem,
    averagePrice: IndicatorItem,
    odometer: IndicatorItem,
    lastRefuel: IndicatorItem,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
            IndicatorCard(consumption, modifier = Modifier.weight(1f))
            IndicatorCard(averagePrice, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
            IndicatorCard(odometer, modifier = Modifier.weight(1f))
            IndicatorCard(lastRefuel, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun IndicatorCard(item: IndicatorItem, modifier: Modifier = Modifier) {
    FFStatTile(
        label = item.label,
        value = item.value,
        unit = item.unit,
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Composable
private fun IndicatorsGridPreview() {
    IndicatorsGrid(
        consumption = IndicatorItem("Consumo médio", "12.5", "km/L"),
        averagePrice = IndicatorItem("Preço médio", "R$ 5,89"),
        odometer = IndicatorItem("Odômetro", "67.270", "km"),
        lastRefuel = IndicatorItem("Último abastecimento", "Há 3 dias"),
    )
}
