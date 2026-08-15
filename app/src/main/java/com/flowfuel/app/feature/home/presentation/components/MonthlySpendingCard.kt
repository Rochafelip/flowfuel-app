package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.MonthlySpendingEntry

/** Card fixo (não faz parte do carrossel) com o gráfico de barras dos últimos 6 meses — spec seção 03. */
@Composable
fun MonthlySpendingCard(entries: List<MonthlySpendingEntry>, modifier: Modifier = Modifier) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Gastos por mês") {
        Column {
            MonthlySpendingBarChart(entries = entries, modifier = Modifier.fillMaxWidth().height(120.dp))
            Spacer(Modifier.height(FFTheme.spacing.xs))
            Text(
                text = "Valores em R$",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MonthlySpendingCardPreview() {
    MonthlySpendingCard(
        entries = listOf(
            MonthlySpendingEntry("2026-03", 210.0),
            MonthlySpendingEntry("2026-04", 380.0),
            MonthlySpendingEntry("2026-05", 190.0),
            MonthlySpendingEntry("2026-06", 410.0),
            MonthlySpendingEntry("2026-07", 330.0),
            MonthlySpendingEntry("2026-08", 543.0),
        ),
    )
}
