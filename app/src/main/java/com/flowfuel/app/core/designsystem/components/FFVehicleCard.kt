package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.theme.FFTheme

@Composable
fun FFVehicleCard(
    nickname: String,
    plate: String,
    odometerKm: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.md)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
            ) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                    Text(nickname, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (isActive) {
                        Surface(
                            color = FFTheme.semanticColors.success,
                            contentColor = FFTheme.semanticColors.onSuccess,
                            shape = FFTheme.extraShapes.pill,
                        ) {
                            Text(
                                "Ativo",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = FFTheme.spacing.sm, end = FFTheme.spacing.sm, top = 2.dp, bottom = 2.dp)
                            )
                        }
                    }
                }
                Text(plate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$odometerKm km", style = FFTheme.numericTypography.numericSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
