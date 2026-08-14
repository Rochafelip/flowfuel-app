package com.flowfuel.app.feature.station.presentation.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFBottomSheet
import com.flowfuel.app.core.designsystem.components.FFEmptyState
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFSkeletonBlock
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.station.domain.model.GeocodeResult

/**
 * BottomSheet de pesquisa de bairro/cidade — busca só dispara ao confirmar
 * (Enter/ação de busca do teclado), nunca a cada tecla digitada: o backend
 * tem rate limit de 1 req/seg agregado em todos os usuários do app.
 */
@Composable
fun LocationSearchBottomSheet(
    state: LocationSearchState,
    onSearch: (String) -> Unit,
    onLocationSelected: (GeocodeResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    FFBottomSheet(onDismiss = onDismiss) {
        Text("Buscar localidade", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(FFTheme.spacing.md))

        FFTextField(
            value = query,
            onValueChange = { query = it },
            label = "Bairro ou cidade",
            placeholder = "Ex: Boa Viagem, Recife",
            leadingIcon = Icons.Default.Search,
            imeAction = ImeAction.Search,
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        )
        Spacer(Modifier.height(FFTheme.spacing.md))

        when (state) {
            LocationSearchState.Idle -> Unit

            LocationSearchState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
                repeat(3) { FFSkeletonBlock(height = 56.dp) }
            }

            is LocationSearchState.Success -> Column {
                state.results.forEach { result ->
                    ListItem(
                        headlineContent = { Text(result.displayName) },
                        leadingContent = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                        modifier = Modifier.clickable { onLocationSelected(result) },
                    )
                }
            }

            LocationSearchState.Empty -> FFEmptyState(
                title = "Nenhum lugar encontrado",
                description = "Tente um nome diferente ou mais específico.",
            )

            is LocationSearchState.Error -> FFErrorState(
                message = state.error.userMessage(),
                onRetry = { onSearch(query) },
            )
        }
    }
}
