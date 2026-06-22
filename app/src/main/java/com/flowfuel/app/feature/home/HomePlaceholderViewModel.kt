package com.flowfuel.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfuel.app.feature.auth.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomePlaceholderViewModel @Inject constructor(
    private val logout: LogoutUseCase,
) : ViewModel() {
    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            logout.invoke()
            onDone()
        }
    }
}
