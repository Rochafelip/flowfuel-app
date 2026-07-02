package com.flowfuel.app.feature.station.presentation.list

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowfuel.app.feature.station.domain.model.StationType

private val STATION_TYPES = listOf(StationType.Fuel, StationType.Electric)

/**
 * Toggle exclusivo — sempre exatamente um tipo selecionado, sem estado "todos".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationTypeFilterRow(
    selectedType: StationType,
    onSelect: (StationType) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        STATION_TYPES.forEachIndexed { index, type ->
            val content = type.badgeContent()
            SegmentedButton(
                selected = type == selectedType,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = STATION_TYPES.size),
                icon = {
                    Icon(imageVector = content.icon, contentDescription = null)
                },
                label = { Text(content.label) },
            )
        }
    }
}
