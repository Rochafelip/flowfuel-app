package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

/**
 * Grid 2 colunas com número variável de tiles — Consumo médio/Preço médio
 * são omitidos pelo chamador para veículos HYBRID (ou quando o valor vem
 * null), em vez de mostrar um tile com "—" (spec seção 04).
 */
@Composable
fun IndicatorsGrid(items: List<IndicatorItem>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap)) {
                row.forEach { item -> IndicatorCard(item, modifier = Modifier.weight(1f)) }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
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
        items = listOf(
            IndicatorItem("Consumo médio", "12,50", "km/L"),
            IndicatorItem("Preço médio", "R$ 5,89"),
            IndicatorItem("Odômetro", "67.270", "km"),
            IndicatorItem("Último abastecimento", "há 3 dias"),
        ),
    )
}
