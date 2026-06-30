package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.theme.FFTheme

enum class FFTrend { Up, Down, Flat }

@Composable
fun FFStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    trend: FFTrend? = null,
    deltaText: String? = null,
    positiveIsGood: Boolean = true,
) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat) {
        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
                FFAutoSizeText(
                    text = value,
                    style = FFTheme.numericTypography.numericLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
            if (trend != null && deltaText != null) {
                val good = when (trend) {
                    FFTrend.Up -> positiveIsGood
                    FFTrend.Down -> !positiveIsGood
                    FFTrend.Flat -> true
                }
                val color: Color = if (good) FFTheme.semanticColors.success else MaterialTheme.colorScheme.error
                val icon = when (trend) {
                    FFTrend.Up -> Icons.AutoMirrored.Filled.TrendingUp
                    FFTrend.Down -> Icons.AutoMirrored.Filled.TrendingDown
                    FFTrend.Flat -> Icons.AutoMirrored.Filled.TrendingFlat
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
                    Icon(icon, contentDescription = null, tint = color)
                    Text(deltaText, style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
        }
    }
}
