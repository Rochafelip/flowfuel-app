package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.components.FFPagerDotsIndicator
import com.flowfuel.app.core.designsystem.theme.FFChartColors
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.SpendBreakdown
import com.flowfuel.app.feature.home.domain.model.SpendBreakdownOverview
import com.flowfuel.app.feature.home.domain.model.SpendSlice

@Composable
fun SpendBreakdownCard(overview: SpendBreakdownOverview, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val pageCount = 2
    val pagerState = rememberPagerState(pageCount = { pageCount })

    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Composição de gastos") {
        Column {
            HorizontalPager(state = pagerState) { page ->
                val label = if (page == 0) "Mês" else "Total"
                val breakdown = if (page == 0) overview.monthly else overview.total
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = FFTheme.spacing.sm),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpendBreakdownDonut(
                            slices = breakdown.slices,
                            totalLabel = formatBrl(breakdown.totalSpent),
                            colorFor = { index, sliceLabel -> sliceColor(index, sliceLabel, isDark) },
                            modifier = Modifier.size(140.dp),
                        )
                        Spacer(Modifier.width(FFTheme.spacing.md))
                        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
                            breakdown.slices.forEachIndexed { index, slice ->
                                val percent = if (breakdown.totalSpent > 0)
                                    slice.amount / breakdown.totalSpent * 100 else 0.0
                                SpendLegendRow(
                                    color = sliceColor(index, slice.label, isDark),
                                    label = slice.label,
                                    amountLabel = formatBrl(slice.amount),
                                    percentLabel = "%.0f%%".format(percent),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(FFTheme.spacing.sm))
            FFPagerDotsIndicator(pagerState = pagerState, pageCount = pageCount)
        }
    }
}

@Composable
private fun SpendBreakdownDonut(
    slices: List<SpendSlice>,
    totalLabel: String,
    colorFor: (Int, String) -> Color,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.amount }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
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
        Text(
            text = totalLabel.replaceFirst(Regex("\\s"), "\n"),
            style = FFTheme.numericTypography.numericSmall.copy(
                fontSize = 13.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(84.dp),
        )
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
                totalSpent = 303.30,
                slices = listOf(
                    SpendSlice("Combustível", 148.42),
                    SpendSlice("Documentos", 154.88),
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
        ),
    )
}
