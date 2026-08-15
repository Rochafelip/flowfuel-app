package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.components.FFPagerDotsIndicator
import com.flowfuel.app.core.designsystem.components.FFTrend
import com.flowfuel.app.core.designsystem.components.FFTrendBadge
import com.flowfuel.app.core.designsystem.theme.FFChartColors
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.SpendBreakdown
import com.flowfuel.app.feature.home.domain.model.SpendBreakdownOverview
import com.flowfuel.app.feature.home.domain.model.SpendSlice
import kotlin.math.abs

/**
 * buildSpendBreakdown nunca gera mais que 5 fatias nomeadas + "Outros" — reservar
 * sempre esse tanto de linhas na legenda (mesmo vazias) evita que o card mude de
 * altura ao trocar de página no pager (Mês costuma ter menos categorias que Total).
 */
private const val MAX_LEGEND_ROWS = 6

@Composable
fun SpendBreakdownCard(
    overview: SpendBreakdownOverview,
    fuelSpent: Double,
    costPerKm: Double?,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val pageCount = 3
    val pagerState = rememberPagerState(pageCount = { pageCount })

    FFCard(modifier = modifier, variant = FFCardVariant.Flat) {
        Column {
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> MonthPage(overview = overview, costPerKm = costPerKm, isDark = isDark)
                    1 -> FuelPage(fuelSpent = fuelSpent)
                    else -> TotalPage(breakdown = overview.total, isDark = isDark)
                }
            }
            Spacer(Modifier.height(FFTheme.spacing.sm))
            FFPagerDotsIndicator(pagerState = pagerState, pageCount = pageCount)
        }
    }
}

@Composable
private fun MonthPage(
    overview: SpendBreakdownOverview,
    costPerKm: Double?,
    isDark: Boolean,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Gasto do Mês",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (overview.percentDelta != null) {
                // Gasto subindo é ruim (positiveIsGood = false): Up vira vermelho, Down vira verde.
                val trend = when {
                    overview.percentDelta > 0.5 -> FFTrend.Up
                    overview.percentDelta < -0.5 -> FFTrend.Down
                    else -> FFTrend.Flat
                }
                FFTrendBadge(
                    trend = trend,
                    label = "${formatPercent(abs(overview.percentDelta))} vs. mês anterior",
                    positiveIsGood = false,
                )
            }
        }
        Spacer(Modifier.height(FFTheme.spacing.xs))
        Text(
            text = formatBrl(overview.monthly.totalSpent),
            style = FFTheme.numericTypography.numericLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (costPerKm != null && costPerKm > 0.0) {
            Text(
                text = "${formatBrl(costPerKm)}/km",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(FFTheme.spacing.sm))
        BreakdownRow(breakdown = overview.monthly, isDark = isDark)
    }
}

@Composable
private fun FuelPage(fuelSpent: Double) {
    Column {
        Text(
            text = "Gasto de Combustível",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(FFTheme.spacing.xs))
        Text(
            text = formatBrl(fuelSpent),
            style = FFTheme.numericTypography.numericLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TotalPage(breakdown: SpendBreakdown, isDark: Boolean) {
    Column {
        Text(
            text = "Gastos Totais",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(FFTheme.spacing.xs))
        Text(
            text = formatBrl(breakdown.totalSpent),
            style = FFTheme.numericTypography.numericLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(FFTheme.spacing.sm))
        BreakdownRow(breakdown = breakdown, isDark = isDark)
    }
}

@Composable
private fun BreakdownRow(breakdown: SpendBreakdown, isDark: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SpendBreakdownDonut(
            slices = breakdown.slices,
            colorFor = { index, sliceLabel -> sliceColor(index, sliceLabel, isDark) },
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.width(FFTheme.spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
            for (index in 0 until MAX_LEGEND_ROWS) {
                val slice = breakdown.slices.getOrNull(index)
                if (slice == null) {
                    // Linha vazia: só reserva altura, não é visível nem clicável.
                    SpendLegendRow(color = Color.Transparent, label = "", amountLabel = "", percentLabel = "")
                } else {
                    val percent = if (breakdown.totalSpent > 0)
                        slice.amount / breakdown.totalSpent * 100 else 0.0
                    SpendLegendRow(
                        color = sliceColor(index, slice.label, isDark),
                        label = slice.label,
                        amountLabel = formatBrl(slice.amount),
                        percentLabel = formatPercent(percent),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpendBreakdownDonut(
    slices: List<SpendSlice>,
    colorFor: (Int, String) -> Color,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.amount }
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.18f
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        var startAngle = -90f
        slices.forEachIndexed { index, slice ->
            val sweep = if (total > 0) (slice.amount / total * 360.0).toFloat() else 0f
            drawArc(
                color = colorFor(index, slice.label),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun SpendLegendRow(color: Color, label: String, amountLabel: String, percentLabel: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(text = amountLabel, style = MaterialTheme.typography.bodySmall)
        Text(
            text = percentLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Cor por posição (rank), não por categoria — "Outros" sempre usa o cinza
 * neutro, independente de onde cai na lista; as demais fatias (já
 * ordenadas por valor decrescente em [buildSpendBreakdown]) usam a cor do
 * slot correspondente ao índice.
 */
private fun sliceColor(index: Int, label: String, isDark: Boolean): Color {
    if (label == "Outros") return if (isDark) FFChartColors.OtherDark else FFChartColors.OtherLight
    return when (index) {
        0 -> if (isDark) FFChartColors.Rank1Dark else FFChartColors.Rank1Light
        1 -> if (isDark) FFChartColors.Rank2Dark else FFChartColors.Rank2Light
        2 -> if (isDark) FFChartColors.Rank3Dark else FFChartColors.Rank3Light
        3 -> if (isDark) FFChartColors.Rank4Dark else FFChartColors.Rank4Light
        else -> if (isDark) FFChartColors.Rank5Dark else FFChartColors.Rank5Light
    }
}

@Preview(showBackground = true)
@Composable
private fun SpendBreakdownCardPreview() {
    SpendBreakdownCard(
        overview = SpendBreakdownOverview(
            monthly = SpendBreakdown(
                totalSpent = 542.80,
                slices = listOf(
                    SpendSlice("Combustível", 298.50),
                    SpendSlice("Manutenção", 135.80),
                    SpendSlice("Outros", 108.50),
                ),
            ),
            total = SpendBreakdown(
                totalSpent = 1720.65,
                slices = listOf(
                    SpendSlice("Combustível", 890.0),
                    SpendSlice("Manutenção", 420.0),
                    SpendSlice("Seguro", 300.0),
                    SpendSlice("Outros", 110.65),
                ),
            ),
            percentDelta = 12.0,
        ),
        fuelSpent = 298.50,
        costPerKm = 0.68,
    )
}
