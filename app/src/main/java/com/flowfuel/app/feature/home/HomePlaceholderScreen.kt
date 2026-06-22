package com.flowfuel.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.theme.FFTheme

@Composable
fun HomePlaceholderScreen(
    onLogout: () -> Unit,
    onAddVehicle: () -> Unit = {},
) {
    Scaffold(topBar = { FFTopBar(title = "Home") }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(FFTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Você está logado.", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Home, Histórico, Veículos e Perfil virão nas próximas fases.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FFButton("Cadastrar veículo", onClick = onAddVehicle)
            FFButton("Sair", onClick = onLogout, variant = FFButtonVariant.Secondary)
        }
    }
}
