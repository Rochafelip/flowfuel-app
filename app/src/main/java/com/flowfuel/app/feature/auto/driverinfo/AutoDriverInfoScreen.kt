package com.flowfuel.app.feature.auto.driverinfo

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceItem
import com.flowfuel.app.feature.home.domain.model.toStatusText
import com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCase
import kotlinx.coroutines.launch

class AutoDriverInfoScreen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase,
) : Screen(carContext) {

    private sealed interface State {
        data object Loading : State
        data class Success(val items: List<UpcomingMaintenanceItem>) : State
        data class Error(val error: AppError) : State
    }

    private var state: State = State.Loading

    init {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) loadData()
        })
    }

    internal fun loadData() {
        lifecycleScope.launch {
            state = State.Loading
            invalidate()

            when (val result = getUpcomingMaintenance(vehicle.id, vehicle.currentKm)) {
                is AppResult.Success -> {
                    state = State.Success(result.value)
                    invalidate()
                }
                is AppResult.Failure -> {
                    state = State.Error(result.error)
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template = when (val s = state) {
        State.Loading -> loadingTemplate()
        is State.Error -> errorTemplate(s.error)
        is State.Success -> successTemplate(s.items)
    }

    private fun loadingTemplate(): Template =
        MessageTemplate.Builder("Carregando…")
            .setTitle("Informações importantes")
            .setHeaderAction(Action.BACK)
            .setLoading(true)
            .build()

    private fun errorTemplate(error: AppError): Template {
        val msg = if (error == AppError.Unauthorized)
            "Sessão expirada. Abra o FlowFuel no celular para entrar novamente."
        else
            "Erro ao carregar informações. Verifique sua conexão."
        val builder = MessageTemplate.Builder(msg)
            .setTitle("Informações importantes")
            .setHeaderAction(Action.BACK)
        if (error != AppError.Unauthorized) {
            builder.addAction(
                Action.Builder()
                    .setTitle("Tentar novamente")
                    .setOnClickListener { loadData() }
                    .build()
            )
        }
        return builder.build()
    }

    private fun successTemplate(items: List<UpcomingMaintenanceItem>): Template {
        val listBuilder = ItemList.Builder()
        items.forEach { item ->
            val statusText = item.toStatusText()
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(statusText.title)
                    .addText(statusText.subtitle)
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Informações importantes")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
