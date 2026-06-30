package com.flowfuel.app.feature.auto.dashboard

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.refuel.AutoRefuelStep1Screen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class AutoDashboardScreen(
    carContext: CarContext,
    private val getActiveVehicle: GetActiveVehicleUseCase,
    private val getDashboard: GetDashboardUseCase,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    private sealed interface State {
        data object Loading : State
        data class Success(val vehicle: ActiveVehicleData, val dashboard: DashboardData) : State
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

            val vehicleResult = getActiveVehicle()
            if (vehicleResult is AppResult.Failure) {
                state = State.Error(vehicleResult.error)
                invalidate()
                return@launch
            }
            val vehicle = (vehicleResult as AppResult.Success).value

            when (val dash = getDashboard(vehicle.id)) {
                is AppResult.Success -> {
                    state = State.Success(vehicle, dash.value)
                    invalidate()
                }
                is AppResult.Failure -> {
                    state = State.Error(dash.error)
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template = when (val s = state) {
        is State.Loading -> loadingTemplate()
        is State.Error -> errorTemplate(s.error)
        is State.Success -> successTemplate(s.vehicle, s.dashboard)
    }

    private fun loadingTemplate(): Template =
        MessageTemplate.Builder("Carregando…")
            .setTitle("FlowFuel")
            .setHeaderAction(Action.APP_ICON)
            .setLoading(true)
            .build()

    private fun errorTemplate(error: AppError): Template {
        val msg = if (error == AppError.Unauthorized)
            "Sessão expirada. Abra o FlowFuel no celular para entrar novamente."
        else
            "Erro ao carregar dados. Verifique sua conexão."
        val builder = MessageTemplate.Builder(msg)
            .setTitle("FlowFuel")
            .setHeaderAction(Action.APP_ICON)
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

    private fun successTemplate(v: ActiveVehicleData, d: DashboardData): Template {
        val brLocale = Locale("pt", "BR")
        val currencyFmt = NumberFormat.getCurrencyInstance(brLocale)
        val title = "${v.brand} ${v.model}${v.licensePlate?.let { " ($it)" } ?: ""}"

        val consumptionText = if (d.averageConsumption != null && d.consumptionUnit != null)
            String.format(brLocale, "%.1f %s", d.averageConsumption, d.consumptionUnit)
        else "—"

        val spentText = currencyFmt.format(d.totalSpent)

        val totalRefuelsText = if (d.totalRefuels > 0)
            "${d.totalRefuels} abastecimento${if (d.totalRefuels > 1) "s" else ""}"
        else
            "Nenhum ainda"

        val lastRefuelText = if (d.lastRefuelDate != null && d.lastRefuelEnergyAmount != null) {
            val raw = d.lastRefuelDate
            val date = raw.takeIf { it.length >= 10 }
                ?.let { "${it.substring(8, 10)}/${it.substring(5, 7)}" }
                ?: raw
            val unit = d.lastRefuelEnergyUnit ?: "L"
            val energy = String.format(brLocale, "%.1f %s", d.lastRefuelEnergyAmount, unit)
            val price = d.lastRefuelAmount?.let { " • ${currencyFmt.format(it)}" } ?: ""
            "$date • $energy$price"
        } else {
            "Nenhum ainda"
        }

        return PaneTemplate.Builder(
            Pane.Builder()
                .addRow(Row.Builder().setTitle("Consumo médio").addText(consumptionText).build())
                .addRow(Row.Builder().setTitle("Gasto total").addText(spentText).build())
                .addRow(Row.Builder().setTitle("Abastecimentos").addText(totalRefuelsText).build())
                .addRow(Row.Builder().setTitle("Último abastecimento").addText(lastRefuelText).build())
                .addAction(
                    Action.Builder()
                        .setTitle("Registrar abastecimento")
                        .setOnClickListener {
                            screenManager.push(
                                AutoRefuelStep1Screen(
                                    carContext,
                                    vehicle = v,
                                    createRefuel = createRefuel,
                                )
                            )
                        }
                        .build()
                )
                .build()
        )
            .setTitle(title)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
