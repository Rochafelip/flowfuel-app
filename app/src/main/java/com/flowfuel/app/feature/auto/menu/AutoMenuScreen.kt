package com.flowfuel.app.feature.auto.menu

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.flowfuel.app.R
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auto.driverinfo.AutoDriverInfoScreen
import com.flowfuel.app.feature.auto.events.AutoEventsScreen
import com.flowfuel.app.feature.auto.refuel.AutoRefuelStep1Screen
import com.flowfuel.app.feature.auto.stations.AutoStationsScreen
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCase
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsUseCase
import kotlinx.coroutines.launch

class AutoMenuScreen(
    carContext: CarContext,
    private val getActiveVehicle: GetActiveVehicleUseCase,
    private val createRefuel: CreateRefuelUseCase,
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val locationProvider: LocationProvider,
    private val getVehicleEvents: GetVehicleEventsUseCase,
    private val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase,
) : Screen(carContext) {

    private sealed interface State {
        data object Loading : State
        data class Success(val vehicle: ActiveVehicleData) : State
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

            when (val result = getActiveVehicle()) {
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
        is State.Success -> successTemplate(s.vehicle)
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

    private fun successTemplate(vehicle: ActiveVehicleData): Template {
        val title = "${vehicle.brand} ${vehicle.model}${vehicle.licensePlate?.let { " ($it)" } ?: ""}"
        return ListTemplate.Builder()
            .setSingleList(
                ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle("Registrar abastecimento")
                            .setImage(icon(R.drawable.ic_auto_add))
                            .setOnClickListener {
                                screenManager.push(AutoRefuelStep1Screen(carContext, vehicle, createRefuel))
                            }
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle("Postos próximos")
                            .setImage(icon(R.drawable.ic_auto_station))
                            .setOnClickListener {
                                screenManager.push(
                                    AutoStationsScreen(carContext, vehicle, getNearbyStations, locationProvider)
                                )
                            }
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle("Eventos")
                            .setImage(icon(R.drawable.ic_auto_history))
                            .setOnClickListener {
                                screenManager.push(AutoEventsScreen(carContext, vehicle.id, getVehicleEvents))
                            }
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle("Informações importantes")
                            .setImage(icon(R.drawable.ic_auto_info))
                            .setOnClickListener {
                                screenManager.push(
                                    AutoDriverInfoScreen(carContext, vehicle, getUpcomingMaintenance)
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
