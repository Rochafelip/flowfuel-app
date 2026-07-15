package com.flowfuel.app.feature.vehicle.presentation.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ShareInviteScreen(
    onBack: () -> Unit,
    viewModel: ShareInviteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                ShareInviteEffect.NavigateBack -> onBack()
            }
        }
    }

    Scaffold(
        topBar = { FFTopBar(title = "Convite de veículo", onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FFTheme.spacing.md, vertical = FFTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
        ) {
            when (val s = state) {
                ShareInviteUiState.Loading -> Text("Carregando...")
                is ShareInviteUiState.NotFound -> Text(s.message)
                is ShareInviteUiState.Content -> {
                    Text("${s.share.ownerName} quer compartilhar o ${s.share.vehicleBrand} ${s.share.vehicleModel} com você.")
                    FFButton(
                        text = "Aceitar",
                        onClick = viewModel::accept,
                        enabled = !s.isSubmitting,
                        loading = s.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FFButton(
                        text = "Recusar",
                        onClick = viewModel::reject,
                        enabled = !s.isSubmitting,
                        variant = FFButtonVariant.Destructive,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
