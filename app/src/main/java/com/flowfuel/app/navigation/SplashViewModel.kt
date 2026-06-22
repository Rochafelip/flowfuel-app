package com.flowfuel.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StartDestination {
    data object Loading       : StartDestination
    data object Onboarding    : StartDestination
    data object Login         : StartDestination
    /** Logado, mas sem veículo ativo → mostrar lista para escolher */
    data object VehiclePicker : StartDestination
    /** Logado com veículo ativo já definido → ir direto para Home */
    data object Home          : StartDestination
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _start = MutableStateFlow<StartDestination>(StartDestination.Loading)
    val start: StateFlow<StartDestination> = _start.asStateFlow()

    init {
        viewModelScope.launch {
            val onboarded       = sessionStore.onboardedFlow.first()
            val session         = sessionStore.sessionFlow.first()
            val activeVehicleId = sessionStore.activeVehicleIdFlow.first()

            _start.value = when {
                !onboarded          -> StartDestination.Onboarding
                !session.isLoggedIn -> StartDestination.Login
                // Sessão ativa + veículo já escolhido → pula o picker
                activeVehicleId != null -> StartDestination.Home
                // Sessão ativa, mas nenhum veículo selecionado ainda
                else -> StartDestination.VehiclePicker
            }
        }
    }
}