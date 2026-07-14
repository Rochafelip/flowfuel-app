package com.flowfuel.app.core.notification.presentation

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.core.notification.data.NotificationPrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NotificationPermissionUiState {
    data object Idle : NotificationPermissionUiState
    data object ShowRationale : NotificationPermissionUiState
}

@HiltViewModel
class NotificationPermissionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsStore: NotificationPrefsStore,
) : ViewModel() {

    private val _state = MutableStateFlow<NotificationPermissionUiState>(NotificationPermissionUiState.Idle)
    val state: StateFlow<NotificationPermissionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (shouldShowRationale()) {
                _state.value = NotificationPermissionUiState.ShowRationale
            }
        }
    }

    fun onRationaleShown() {
        _state.value = NotificationPermissionUiState.Idle
        viewModelScope.launch { prefsStore.markRationaleShown() }
    }

    private suspend fun shouldShowRationale(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return !prefsStore.hasShownRationale()
    }
}
