package com.flowfuel.app.feature.auth.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.theme.FFTheme

@Composable
fun DeleteAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val canDelete = input == "DELETE"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Excluir conta permanentemente?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                Text(
                    text  = "Esta ação é irreversível. Ao excluir sua conta:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("• Todos os seus dados serão removidos")
                    Text("• Seu histórico de abastecimentos será perdido")
                    Text("• Seus veículos cadastrados serão excluídos")
                }
                Spacer(Modifier.height(FFTheme.spacing.sm))
                FFTextField(
                    value         = input,
                    onValueChange = { input = it },
                    label         = "Confirmação",
                    placeholder   = "Digite DELETE para confirmar",
                    imeAction     = ImeAction.Done,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = onConfirm,
                enabled  = canDelete,
                colors   = ButtonDefaults.textButtonColors(
                    contentColor         = MaterialTheme.colorScheme.error,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                ),
            ) { Text("Excluir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
