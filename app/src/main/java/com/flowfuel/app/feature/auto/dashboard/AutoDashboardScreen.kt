package com.flowfuel.app.feature.auto.dashboard

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.flowfuel.app.R
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

        return GridTemplate.Builder()
            .setSingleList(
                ItemList.Builder()
                    .addItem(
                        GridItem.Builder().setTitle("Consumo médio").setText(consumptionText)
                            .setImage(icon(R.drawable.ic_auto_fuel)).build()
                    )
                    .addItem(
                        GridItem.Builder().setTitle("Gasto total").setText(spentText)
                            .setImage(icon(R.drawable.ic_auto_money)).build()
                    )
                    .addItem(
                        GridItem.Builder().setTitle("Abastecimentos").setText(totalRefuelsText)
                            .setImage(icon(R.drawable.ic_auto_calendar)).build()
                    )
                    .addItem(
                        GridItem.Builder().setTitle("Último abastecimento").setText(lastRefuelText)
                            .setImage(icon(R.drawable.ic_auto_history)).build()
                    )
                    .addItem(
                        GridItem.Builder()
                            .setTitle("Registrar abastecimento")
                            .setImage(icon(R.drawable.ic_auto_add))
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

    private fun icon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
}
