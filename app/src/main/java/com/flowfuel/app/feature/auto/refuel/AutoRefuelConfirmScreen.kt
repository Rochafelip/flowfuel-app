package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.CreateRefuelRequest
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import kotlinx.coroutines.launch

class AutoRefuelConfirmScreen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val tripKm: Double,
    private val liters: Double,
    private val totalPrice: Double,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    private sealed interface State {
        data object Idle : State
        data object Submitting : State
        data class Error(val message: String, val isRetryable: Boolean) : State
    }

    private var state: State = State.Idle

    override fun onGetTemplate(): Template = when (val s = state) {
        is State.Idle, is State.Submitting -> MessageTemplate.Builder(
            buildString {
                appendLine("Percurso: %.0f km".format(tripKm))
                appendLine("Litros: %.1f L".format(liters))
                append("Valor: R$ %.2f".format(totalPrice))
            }
        )
            .setTitle("Confirmar abastecimento")
            .setHeaderAction(Action.BACK)
            .setLoading(s is State.Submitting)
            .apply {
                if (s is State.Idle) {
                    addAction(
                        Action.Builder()
                            .setTitle("Confirmar")
                            .setOnClickListener { submit() }
                            .build()
                    )
                    addAction(
                        Action.Builder()
                            .setTitle("Corrigir")
                            .setOnClickListener {
                                screenManager.popToRoot()
                                screenManager.push(AutoRefuelStep1Screen(carContext, vehicle, createRefuel))
                            }
                            .build()
                    )
                }
            }
            .build()

        is State.Error -> MessageTemplate.Builder(s.message)
            .setTitle("Erro")
            .setHeaderAction(Action.BACK)
            .apply {
                if (s.isRetryable) {
                    addAction(
                        Action.Builder()
                            .setTitle("Tentar novamente")
                            .setOnClickListener {
                                state = State.Idle
                                invalidate()
                            }
                            .build()
                    )
                }
                addAction(
                    Action.Builder()
                        .setTitle("Cancelar")
                        .setOnClickListener { screenManager.popToRoot() }
                        .build()
                )
            }
            .build()
    }

    internal fun testSubmit() = submit()

    private fun submit() {
        if (state is State.Submitting) return
        state = State.Submitting
        invalidate()
        lifecycleScope.launch {
            val odometer = vehicle.currentKm.toDouble() + tripKm
            val refuelType = if (vehicle.energyType == "ELECTRIC") "ELECTRIC" else "FUEL"
            val request = CreateRefuelRequest(
                vehicleId = vehicle.id,
                odometer = odometer,
                liters = liters,
                totalPrice = totalPrice,
                fullTank = false,
                refuelType = refuelType,
            )
            when (val result = createRefuel(request)) {
                is AppResult.Success -> {
                    screenManager.popToRoot()
                    screenManager.push(AutoRefuelSuccessScreen(carContext))
                }
                is AppResult.Failure -> {
                    state = if (result.error == AppError.Unauthorized) {
                        State.Error("Sessão expirada. Abra o FlowFuel no celular para entrar novamente.", isRetryable = false)
                    } else {
                        State.Error("Não foi possível registrar. Verifique sua conexão e tente novamente.", isRetryable = true)
                    }
                    invalidate()
                }
            }
        }
    }
}
