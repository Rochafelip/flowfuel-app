package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.theme.FFTheme

@Composable
fun FFTrendBadge(
    trend: FFTrend,
    label: String,
    modifier: Modifier = Modifier,
    positiveIsGood: Boolean = true,
) {
    val isGood = when (trend) {
        FFTrend.Up -> positiveIsGood
        FFTrend.Down -> !positiveIsGood
        FFTrend.Flat -> true
    }
    val contentColor: Color = when {
        trend == FFTrend.Flat -> MaterialTheme.colorScheme.onSurfaceVariant
        isGood -> FFTheme.semanticColors.success
        else -> MaterialTheme.colorScheme.error
    }
    val backgroundColor: Color = contentColor.copy(alpha = 0.12f)
    val icon = when (trend) {
        FFTrend.Up -> Icons.AutoMirrored.Filled.TrendingUp
        FFTrend.Down -> Icons.AutoMirrored.Filled.TrendingDown
        FFTrend.Flat -> Icons.AutoMirrored.Filled.TrendingFlat
    }

    Row(
        modifier = modifier
            .clip(FFTheme.extraShapes.pill)
            .background(backgroundColor)
            .padding(horizontal = FFTheme.spacing.sm, vertical = FFTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}
