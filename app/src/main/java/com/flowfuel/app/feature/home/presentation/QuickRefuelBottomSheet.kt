package com.flowfuel.app.feature.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.CurrencyBrlVisualTransformation
import com.flowfuel.app.core.designsystem.components.FFBottomSheet
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFNumberField
import com.flowfuel.app.core.designsystem.components.FFNumberKind
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.ui.userMessage

@Composable
fun QuickRefuelBottomSheet(
    form: RefuelFormState,
    isSubmitting: Boolean,
    submitError: AppError?,
    energyType: String,
    onOdometerInputModeChange: (OdometerInputMode) -> Unit,
    onTripKmChange: (String) -> Unit,
    onOdometerChange: (String) -> Unit,
    onLitersChange: (String) -> Unit,
    onTotalPriceInput: (String) -> Unit,
    onFullTankToggle: (Boolean) -> Unit,
    onRefuelTypeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isHybrid = energyType.equals("HYBRID", ignoreCase = true)
    val isElectric = energyType.equals("ELECTRIC", ignoreCase = true)

    // Para híbrido, os labels acompanham o chip selecionado em tempo real.
    // Enquanto nenhuma opção é escolhida, assume-se combustível (FUEL).
    val effectiveElectric = isElectric || (isHybrid && form.refuelType == "ELECTRIC")

    val quantityLabel = if (effectiveElectric) "kWh carregados" else "Litros abastecidos"
    val quantityError = if (effectiveElectric) "Informe a quantidade de kWh" else "Informe a quantidade de litros"
    // form.totalPriceRaw é o valor TOTAL pago (não o preço por unidade) — o
    // preço por unidade é calculado a partir dele em HomeRepositoryImpl
    // (totalPrice / liters), por isso o rótulo precisa dizer "valor total".
    val priceLabel    = "Valor total pago"

    FFBottomSheet(onDismiss = onDismiss) {
        // ── Título ────────────────────────────────────────────────────────────
        Text(
            text = "Registrar abastecimento",
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(Modifier.height(FFTheme.spacing.md))

        Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {

            // ── Toggle Percurso / Odômetro ────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                FilterChip(
                    selected = form.odometerInputMode == OdometerInputMode.TRIP,
                    onClick  = { onOdometerInputModeChange(OdometerInputMode.TRIP) },
                    label    = { Text("Percurso") },
                )
                FilterChip(
                    selected = form.odometerInputMode == OdometerInputMode.ODOMETER,
                    onClick  = { onOdometerInputModeChange(OdometerInputMode.ODOMETER) },
                    label    = { Text("Odômetro") },
                )
            }

            if (form.odometerInputMode == OdometerInputMode.TRIP) {
                FFNumberField(
                    value         = form.tripKm,
                    onValueChange = onTripKmChange,
                    label         = "Km percorridos",
                    kind          = FFNumberKind.Decimal,
                    errorText     = if (form.tripKmError) "Informe os km percorridos" else null,
                    helper        = "Use vírgula ou ponto como separador decimal",
                    imeAction     = ImeAction.Next,
                )
            } else {
                FFNumberField(
                    value         = form.odometer,
                    onValueChange = onOdometerChange,
                    label         = "Odômetro (km)",
                    kind          = FFNumberKind.Odometer,
                    errorText     = if (form.odometerError) "Informe a leitura do odômetro"
                                    else form.serverErrors?.firstOrNull { it.field == "odometer" }?.message,
                    imeAction     = ImeAction.Next,
                )
            }

            // ── Quantidade (litros ou kWh) ─────────────────────────────────────
            FFNumberField(
                value = form.liters,
                onValueChange = onLitersChange,
                label = quantityLabel,
                kind = FFNumberKind.Decimal,
                errorText = if (form.litersError) quantityError
                            else form.serverErrors?.firstOrNull { it.field == "liters" }?.message,
                helper = "Use vírgula ou ponto como separador decimal",
                imeAction = ImeAction.Next,
            )

            FFTextField(
                value = form.totalPriceRaw,
                onValueChange = onTotalPriceInput,
                label = priceLabel,
                keyboardType = KeyboardType.Number,
                visualTransformation = CurrencyBrlVisualTransformation(),
                errorText = if (form.totalPriceError) "Informe o valor pago"
                            else form.serverErrors?.firstOrNull { it.field == "totalPrice" }?.message,
                imeAction = ImeAction.Done,
            )

            // ── Tipo de abastecimento (apenas híbridos) ───────────────────────
            if (isHybrid) {
                HorizontalDivider(modifier = Modifier.padding(vertical = FFTheme.spacing.xs))
                Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
                    Text(
                        text = "Tipo de abastecimento",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                        FilterChip(
                            selected = form.refuelType == "FUEL",
                            onClick = { onRefuelTypeChange("FUEL") },
                            label = { Text("Combustível") },
                        )
                        FilterChip(
                            selected = form.refuelType == "ELECTRIC",
                            onClick = { onRefuelTypeChange("ELECTRIC") },
                            label = { Text("Elétrico") },
                        )
                    }
                    if (form.refuelTypeError) {
                        Text(
                            text = "Selecione o tipo de abastecimento",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // ── Tanque cheio ──────────────────────────────────────────────────
            HorizontalDivider(modifier = Modifier.padding(vertical = FFTheme.spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tanque cheio",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Abastecimento completo até o limite",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = form.fullTank,
                    onCheckedChange = onFullTankToggle,
                )
            }

            if (submitError != null) {
                val message = submitError.userMessage()
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(FFTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.height(FFTheme.spacing.xs))

            // ── Botão registrar ───────────────────────────────────────────────
            FFButton(
                text = if (isSubmitting) "Registrando…" else "Registrar abastecimento",
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                loading = isSubmitting,
                enabled = form.canSubmit(isHybrid),
                variant = FFButtonVariant.Primary,
            )
        }

        Spacer(Modifier.height(FFTheme.spacing.lg))
    }
}