package com.flowfuel.app.feature.auth.presentation.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.notification.domain.DeviceTokenRepository
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetPendingVehicleSharesUseCase
import com.flowfuel.app.feature.auth.domain.model.UserProfile
import com.flowfuel.app.feature.auth.domain.usecase.DeleteAccountUseCase
import com.flowfuel.app.feature.auth.domain.usecase.DeleteProfilePictureUseCase
import com.flowfuel.app.feature.auth.domain.usecase.GetProfileStatsUseCase
import com.flowfuel.app.feature.auth.domain.usecase.GetProfileUseCase
import com.flowfuel.app.feature.auth.domain.usecase.LogoutUseCase
import com.flowfuel.app.feature.auth.domain.usecase.ProfileStats
import com.flowfuel.app.feature.auth.domain.usecase.UploadProfilePictureUseCase
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

// ─── State ─────────────────────────────────────────────────────────────────────

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Content(
        val profile: UserProfile,
        val stats: ProfileStats? = null,
        val isLoggingOut: Boolean = false,
        val isUploadingPhoto: Boolean = false,
        val isDeletingPhoto: Boolean = false,
        val showDeleteDialog: Boolean = false,
        val isDeletingAccount: Boolean = false,
        val pendingShareCount: Int = 0,
    ) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

// ─── Effects ───────────────────────────────────────────────────────────────────

sealed interface ProfileEffect {
    data object NavigateToLogin : ProfileEffect
    data object NavigateToEditProfile : ProfileEffect
    data object NavigateToChangePassword : ProfileEffect
    data object NavigateToVehicles : ProfileEffect
    data object ShowUploadError : ProfileEffect
    data object ShowDeleteError : ProfileEffect
    /**
     * Navega direto pro convite (sem tela de lista intermediária): com um único
     * compartilhamento ativo por vez no backend, `GetPendingVehicleSharesUseCase`
     * normalmente devolve 0 ou 1 item, então basta o id do primeiro.
     */
    data class NavigateToShareInvite(val shareId: Int) : ProfileEffect
}

// ─── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getProfile: GetProfileUseCase,
    private val getProfileStats: GetProfileStatsUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val sessionStore: SessionStore,
    private val uploadProfilePicture: UploadProfilePictureUseCase,
    private val deleteProfilePicture: DeleteProfilePictureUseCase,
    private val deleteAccount: DeleteAccountUseCase,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val getPendingVehicleShares: GetPendingVehicleSharesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val _effects = Channel<ProfileEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // Id do primeiro compartilhamento pendente já buscado por [loadPendingShares],
    // reaproveitado por [onPendingSharesClicked] pra não buscar de novo.
    private var firstPendingShareId: Int? = null

    init {
        load()
    }

    fun load() {
        _state.update { ProfileUiState.Loading }
        viewModelScope.launch {
            Timber.d("Profile › buscando perfil do usuário")
            when (val result = getProfile()) {
                is AppResult.Success -> {
                    Timber.d("Profile › perfil recebido: ${result.value.email}")
                    _state.update { ProfileUiState.Content(result.value) }
                    loadStats()
                    loadPendingShares()
                }
                is AppResult.Failure -> {
                    Timber.e("Profile › erro ao buscar perfil: ${result.error}")
                    handleError(result.error)
                }
            }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            Timber.d("Profile › buscando estatísticas")
            val stats = getProfileStats()
            Timber.d("Profile › stats: veículos=${stats.vehiclesCount} abastec=${stats.refuelsCount} eventos=${stats.eventsCount}")
            _state.update { current ->
                (current as? ProfileUiState.Content)?.copy(stats = stats) ?: current
            }
        }
    }

    private fun loadPendingShares() {
        viewModelScope.launch {
            val result = getPendingVehicleShares()
            val pending = (result as? AppResult.Success)?.value.orEmpty()
            firstPendingShareId = pending.firstOrNull()?.id
            _state.update { current ->
                (current as? ProfileUiState.Content)?.copy(pendingShareCount = pending.size) ?: current
            }
        }
    }

    fun onPendingSharesClicked() {
        val shareId = firstPendingShareId ?: return
        viewModelScope.launch { _effects.send(ProfileEffect.NavigateToShareInvite(shareId)) }
    }

    fun logout() {
        val current = _state.value
        if (current is ProfileUiState.Content) {
            _state.update { current.copy(isLoggingOut = true) }
        }
        viewModelScope.launch {
            runCatching { FirebaseMessaging.getInstance().token.await() }
                .getOrNull()
                ?.let { deviceTokenRepository.unregisterToken(it) }
            sessionStore.clear()
            logoutUseCase()
            _effects.send(ProfileEffect.NavigateToLogin)
        }
    }

    fun onEditProfile() {
        viewModelScope.launch { _effects.send(ProfileEffect.NavigateToEditProfile) }
    }

    fun onChangePassword() {
        viewModelScope.launch { _effects.send(ProfileEffect.NavigateToChangePassword) }
    }

    fun onManageVehicles() {
        viewModelScope.launch { _effects.send(ProfileEffect.NavigateToVehicles) }
    }

    fun onPickImage(uri: Uri) {
        val current = _state.value as? ProfileUiState.Content ?: return
        _state.update { current.copy(isUploadingPhoto = true) }
        viewModelScope.launch {
            when (val result = uploadProfilePicture(uri)) {
                is AppResult.Success -> {
                    // Usa a URL retornada pelo upload diretamente (já absoluta e com
                    // cache-busting) — recarregar o perfil aqui reintroduziria a URL
                    // "limpa" e o Coil voltaria a servir a foto antiga do cache.
                    _state.update {
                        current.copy(
                            isUploadingPhoto = false,
                            profile = current.profile.copy(profilePictureUrl = result.value),
                        )
                    }
                }
                is AppResult.Failure -> {
                    Timber.e("Profile › erro ao enviar foto: ${result.error}")
                    _state.update { current.copy(isUploadingPhoto = false) }
                    _effects.send(ProfileEffect.ShowUploadError)
                }
            }
        }
    }

    fun onDeletePicture() {
        val current = _state.value as? ProfileUiState.Content ?: return
        _state.update { current.copy(isDeletingPhoto = true) }
        viewModelScope.launch {
            when (val result = deleteProfilePicture()) {
                is AppResult.Success -> {
                    _state.update {
                        current.copy(
                            isDeletingPhoto = false,
                            profile = current.profile.copy(profilePictureUrl = null),
                        )
                    }
                    load()
                }
                is AppResult.Failure -> {
                    Timber.e("Profile › erro ao remover foto: ${result.error}")
                    _state.update { current.copy(isDeletingPhoto = false) }
                    _effects.send(ProfileEffect.ShowDeleteError)
                }
            }
        }
    }

    fun onShowDeleteDialog() {
        val current = _state.value as? ProfileUiState.Content ?: return
        _state.update { current.copy(showDeleteDialog = true) }
    }

    fun onDismissDeleteDialog() {
        val current = _state.value as? ProfileUiState.Content ?: return
        _state.update { current.copy(showDeleteDialog = false) }
    }

    fun onDeleteAccountConfirmed() {
        val current = _state.value as? ProfileUiState.Content ?: return
        _state.update { current.copy(showDeleteDialog = false, isDeletingAccount = true) }
        viewModelScope.launch {
            deleteAccount()
            _effects.send(ProfileEffect.NavigateToLogin)
        }
    }

    private suspend fun handleError(error: AppError) {
        when (error) {
            AppError.Unauthorized -> {
                sessionStore.clear()
                _effects.send(ProfileEffect.NavigateToLogin)
            }
            AppError.Network -> _state.update { ProfileUiState.Error("Sem conexão. Verifique sua internet.") }
            is AppError.Api -> _state.update { ProfileUiState.Error("Erro ao carregar perfil (${error.code}).") }
            is AppError.RateLimited -> _state.update { ProfileUiState.Error("Muitas tentativas. Tente novamente mais tarde.") }
            is AppError.Unknown -> _state.update { ProfileUiState.Error("Erro inesperado. Tente novamente.") }
        }
    }
}
