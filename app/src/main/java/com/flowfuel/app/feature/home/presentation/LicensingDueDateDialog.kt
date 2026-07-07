package com.flowfuel.app.feature.home.presentation

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.time.Instant
import java.time.ZoneOffset

/**
 * Diálogo dedicado para definir a data de licenciamento, aberto direto a partir do
 * cartão de lembrete na Home — não a tela de edição de veículo, já que este campo é
 * armazenado só localmente (ver [com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStore]),
 * diferente dos demais campos daquela tela, que são sincronizados com o backend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensingDueDateDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val datePickerState = rememberDatePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onConfirm(date.toString())
                }
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@Preview(showBackground = true)
@Composable
private fun LicensingDueDateDialogPreview() {
    LicensingDueDateDialog(onConfirm = {}, onDismiss = {})
}
