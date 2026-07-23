package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Card de veículo compartilhado com o usuário (não é dono). Usado em
 * `VehiclePickerScreen` e `VehiclesScreen` — mesmo visual nas duas telas
 * para deixar claro que é um veículo diferente de um próprio.
 *
 * O avatar sempre usa o ícone genérico de carro: `VehicleShare` não carrega
 * `vehicleType`/`photoUrl` do backend (limitação aceita, ver spec
 * `2026-07-23-ffborrowedvehiclecard-avatar-design.md`).
 */
@Composable
fun FFBorrowedVehicleCard(
    share: VehicleShare,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
        ) {
            VehiclePhotoAvatar(photoUrl = null, vehicleType = VehicleType.Car, size = 48.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                ) {
                    Text(
                        text = "${share.vehicleBrand} ${share.vehicleModel}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        color = FFTheme.semanticColors.info,
                        contentColor = FFTheme.semanticColors.onInfo,
                        shape = FFTheme.extraShapes.pill,
                    ) {
                        Text(
                            text = "Emprestado",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(
                                start = FFTheme.spacing.sm,
                                end = FFTheme.spacing.sm,
                                top = 2.dp,
                                bottom = 2.dp,
                            ),
                        )
                    }
                }
                Text(
                    text = "até ${share.expiresAt?.formatShareExpiry() ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val shareExpiryFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

private fun String.formatShareExpiry(): String =
    runCatching { LocalDate.parse(take(10)).format(shareExpiryFormatter) }.getOrDefault(this)
