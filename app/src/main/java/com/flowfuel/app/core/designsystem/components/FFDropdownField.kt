package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Item genérico de uma lista carregada de forma assíncrona (ex.: marca/modelo/ano da FIPE). */
data class FFDropdownOption(val code: String, val label: String)

/**
 * Dropdown de seleção única com opções carregadas de forma assíncrona.
 * Suporta estado de carregamento (spinner no trailing) e de erro (ícone de
 * retry no trailing + texto de suporte), além de `enabled=false` para
 * representar um passo da cascata ainda não liberado (ex.: Modelo antes de
 * escolher Marca).
 */
@Composable
fun FFDropdownField(
    label: String,
    selectedLabel: String?,
    options: List<FFDropdownOption>,
    onOptionSelected: (FFDropdownOption) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    errorText: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val menuEnabled = enabled && !loading

    ExposedDropdownMenuBox(
        expanded = expanded && menuEnabled,
        onExpandedChange = { if (menuEnabled) expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = menuEnabled,
            label = { Text(label) },
            isError = errorText != null,
            supportingText = errorText?.let { { Text(it) } },
            trailingIcon = {
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    errorText != null -> IconButton(onClick = { onRetry?.invoke() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                    else -> ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, menuEnabled)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded && menuEnabled,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
