package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType

@Composable
fun VehicleHeader(
    vehicle: ActiveVehicleData,
    daysSinceLastRefuel: Int?,
    onVehicleClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = FFTheme.spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.clickable(onClick = onVehicleClick)) {
            Text(
                text = "${vehicle.brand} ${vehicle.model}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = daysSinceRefuelLabel(daysSinceLastRefuel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Sobre o app",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Reservado para o futuro: hoje é só um ícone estático, sem contagem/lógica de notificações.
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Notificações",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun daysSinceRefuelLabel(days: Int?): String = when {
    days == null -> "Pronto para rodar"
    days == 0 -> "Abastecido hoje"
    days == 1 -> "Último abastecimento foi ontem"
    else -> "Há $days dias sem abastecer"
}

@Preview(showBackground = true)
@Composable
private fun VehicleHeaderPreview() {
    VehicleHeader(
        vehicle = ActiveVehicleData(
            id = 1, brand = "Volkswagen", model = "Fox", fuelSubType = null, capacity = null,
            licensePlate = "ABC1D23", energyType = "COMBUSTION", currentKm = 67270,
            photoUrl = null, vehicleType = VehicleType.Car,
        ),
        daysSinceLastRefuel = 3,
        onVehicleClick = {},
        onInfoClick = {},
    )
}
