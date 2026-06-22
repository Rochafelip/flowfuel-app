package com.flowfuel.app.feature.vehicleevent.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.LocalCarWash
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.TireRepair
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory

internal val EventCategory.icon: ImageVector
    get() = when (this) {
        EventCategory.FUEL -> Icons.Outlined.LocalGasStation
        EventCategory.MAINTENANCE -> Icons.Outlined.Build
        EventCategory.OIL_CHANGE -> Icons.Outlined.Opacity
        EventCategory.WASH -> Icons.Outlined.LocalCarWash
        EventCategory.TIRES -> Icons.Outlined.TireRepair
        EventCategory.INSURANCE -> Icons.Outlined.Security
        EventCategory.TAX -> Icons.Outlined.Receipt
        EventCategory.DOCUMENTS -> Icons.AutoMirrored.Outlined.Article
        EventCategory.OTHER -> Icons.Outlined.MoreHoriz
    }

@Composable
fun EventCategoryChip(category: EventCategory, modifier: Modifier = Modifier) {
    val containerColor = when (category) {
        EventCategory.FUEL -> MaterialTheme.colorScheme.primaryContainer
        EventCategory.MAINTENANCE -> MaterialTheme.colorScheme.secondaryContainer
        EventCategory.OIL_CHANGE -> MaterialTheme.colorScheme.tertiaryContainer
        EventCategory.WASH -> MaterialTheme.colorScheme.secondaryContainer
        EventCategory.TIRES -> MaterialTheme.colorScheme.tertiaryContainer
        EventCategory.INSURANCE -> MaterialTheme.colorScheme.primaryContainer
        EventCategory.TAX -> MaterialTheme.colorScheme.errorContainer
        EventCategory.DOCUMENTS -> MaterialTheme.colorScheme.surfaceVariant
        EventCategory.OTHER -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (category) {
        EventCategory.FUEL -> MaterialTheme.colorScheme.onPrimaryContainer
        EventCategory.MAINTENANCE -> MaterialTheme.colorScheme.onSecondaryContainer
        EventCategory.OIL_CHANGE -> MaterialTheme.colorScheme.onTertiaryContainer
        EventCategory.WASH -> MaterialTheme.colorScheme.onSecondaryContainer
        EventCategory.TIRES -> MaterialTheme.colorScheme.onTertiaryContainer
        EventCategory.INSURANCE -> MaterialTheme.colorScheme.onPrimaryContainer
        EventCategory.TAX -> MaterialTheme.colorScheme.onErrorContainer
        EventCategory.DOCUMENTS -> MaterialTheme.colorScheme.onSurfaceVariant
        EventCategory.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = FFTheme.extraShapes.pill,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FFTheme.spacing.sm, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = category.label,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
