package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.components.FFTrend
import com.flowfuel.app.core.designsystem.components.FFTrendBadge
import com.flowfuel.app.core.designsystem.theme.FFTheme
import kotlin.math.abs

@Composable
fun FinancialSummaryCard(
    currentMonthTotalLabel: String,
    percentDelta: Double?,
    modifier: Modifier = Modifier,
) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Gasto do mês") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            Text(
                text = currentMonthTotalLabel,
                style = FFTheme.numericTypography.numericLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (percentDelta != null) {
                // Gasto subindo é ruim (positiveIsGood = false): Up vira vermelho, Down vira verde.
                val trend = when {
                    percentDelta > 0.5 -> FFTrend.Up
                    percentDelta < -0.5 -> FFTrend.Down
                    else -> FFTrend.Flat
                }
                FFTrendBadge(
                    trend = trend,
                    label = "%.0f%% vs. mês anterior".format(abs(percentDelta)),
                    positiveIsGood = false,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FinancialSummaryCardPreview() {
    FinancialSummaryCard(currentMonthTotalLabel = "R$ 350,00", percentDelta = 12.0)
}
