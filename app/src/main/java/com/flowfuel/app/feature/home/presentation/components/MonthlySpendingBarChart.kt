package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.MonthlySpendingEntry
import java.time.YearMonth

private const val MIN_BAR_HEIGHT_FRACTION = 0.04f

/** Altura da barra como fração de [maxAmount], com piso mínimo para nunca "sumir" — exceto quando a série inteira é zero. */
internal fun barHeightFraction(amount: Double, maxAmount: Double): Float {
    if (maxAmount <= 0.0) return 0f
    return (amount / maxAmount).toFloat().coerceAtLeast(MIN_BAR_HEIGHT_FRACTION)
}

/**
 * Gráfico de barras dos últimos 6 meses. Sempre 6 barras, mês mais antigo à
 * esquerda; se todos os valores forem 0, mostra texto no lugar do gráfico.
 * Reutilizado tanto pelo card fixo (seção 03) quanto pela página "Mês" do
 * carrossel de gastos — mesmo conteúdo, de propósito.
 */
@Composable
fun MonthlySpendingBarChart(entries: List<MonthlySpendingEntry>, modifier: Modifier = Modifier) {
    val allZero = entries.all { it.amount <= 0.0 }
    if (entries.isEmpty() || allZero) {
        Text(
            text = "Sem gastos nos últimos 6 meses.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    val maxAmount = entries.maxOf { it.amount }
    val currentMonth = remember(entries) { YearMonth.now().toString() }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
    ) {
        entries.forEach { entry ->
            val isCurrent = entry.month == currentMonth
            val barColor = if (isCurrent) FFTheme.semanticColors.brandGreen else MaterialTheme.colorScheme.surfaceVariant
            val labelColor = if (isCurrent) FFTheme.semanticColors.brandGreen else MaterialTheme.colorScheme.onSurfaceVariant

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatInteger(Math.round(entry.amount).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = labelColor,
                )
                Spacer(Modifier.height(FFTheme.spacing.xs))
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .fillMaxHeight(barHeightFraction(entry.amount, maxAmount))
                            .background(barColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                    )
                }
                Spacer(Modifier.height(FFTheme.spacing.xs))
                Text(
                    text = formatMonthAbbrev(entry.month),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = labelColor,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MonthlySpendingBarChartPreview() {
    MonthlySpendingBarChart(
        entries = listOf(
            MonthlySpendingEntry("2026-03", 210.0),
            MonthlySpendingEntry("2026-04", 380.0),
            MonthlySpendingEntry("2026-05", 190.0),
            MonthlySpendingEntry("2026-06", 410.0),
            MonthlySpendingEntry("2026-07", 330.0),
            MonthlySpendingEntry("2026-08", 543.0),
        ),
        modifier = Modifier.height(140.dp),
    )
}
