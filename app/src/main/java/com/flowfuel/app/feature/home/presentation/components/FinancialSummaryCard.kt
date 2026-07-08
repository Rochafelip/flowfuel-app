package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    totalSpentLabel: String,
    modifier: Modifier = Modifier,
) {
    val pageCount = 2
    val pagerState = rememberPagerState(pageCount = { pageCount })

    FFCard(modifier = modifier, variant = FFCardVariant.Flat) {
        Column {
            HorizontalPager(state = pagerState) { page ->
                Column {
                    Text(
                        text = if (page == 0) "Gasto do mês" else "Gasto total",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = FFTheme.spacing.sm),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                    ) {
                        Text(
                            text = if (page == 0) currentMonthTotalLabel else totalSpentLabel,
                            style = FFTheme.numericTypography.numericLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (page == 0 && percentDelta != null) {
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
            Spacer(Modifier.height(FFTheme.spacing.sm))
            PagerDotsIndicator(pagerState = pagerState, pageCount = pageCount)
        }
    }
}

@Composable
private fun PagerDotsIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { index ->
            val active = pagerState.currentPage == index
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FinancialSummaryCardPreview() {
    FinancialSummaryCard(
        currentMonthTotalLabel = "R$ 350,00",
        percentDelta = 12.0,
        totalSpentLabel = "R$ 12.480,00",
    )
}
