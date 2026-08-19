package com.flowfuel.app.feature.auto.stations

import android.content.Intent
import android.net.Uri
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
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import com.flowfuel.app.feature.station.presentation.list.formatDistance
import kotlinx.coroutines.launch

private const val STATION_RADIUS_METERS = 3_000
private const val MAX_STATIONS_SHOWN = 6

class AutoStationsScreen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val locationProvider: LocationProvider,
) : Screen(carContext) {

    private sealed interface State {
        data object Loading : State
        data class Success(val stations: List<Station>) : State
        data object PermissionDenied : State
        data object Empty : State
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

            when (val locationResult = locationProvider.getCurrentLocation()) {
                LocationResult.PermissionDenied -> {
                    state = State.PermissionDenied
                    invalidate()
                }
                LocationResult.Unavailable -> {
                    state = State.Empty
                    invalidate()
                }
                is LocationResult.Available -> {
                    val wantedType = if (vehicle.energyType == "ELECTRIC") StationType.Electric else StationType.Fuel
                    when (val result = getNearbyStations(locationResult.location, STATION_RADIUS_METERS)) {
                        is AppResult.Success -> {
                            val stations = result.value
                                .filter { it.type == wantedType }
                                .take(MAX_STATIONS_SHOWN)
                            state = if (stations.isEmpty()) State.Empty else State.Success(stations)
                            invalidate()
                        }
                        is AppResult.Failure -> {
                            state = State.Error(result.error)
                            invalidate()
                        }
                    }
                }
            }
        }
    }

    internal fun testNavigateTo(station: Station) = navigateTo(station)

    private fun navigateTo(station: Station) {
        carContext.startCarApp(
            Intent(CarContext.ACTION_NAVIGATE)
                .setData(Uri.parse("geo:${station.latitude},${station.longitude}"))
        )
    }

    override fun onGetTemplate(): Template = when (val s = state) {
        State.Loading -> loadingTemplate()
        State.PermissionDenied -> messageTemplate(
            "Ative a permissão de localização no FlowFuel do celular para ver postos próximos."
        )
        State.Empty -> messageTemplate("Nenhum posto encontrado a até 3 km.")
        is State.Error -> errorTemplate(s.error)
        is State.Success -> successTemplate(s.stations)
    }

    private fun loadingTemplate(): Template =
        MessageTemplate.Builder("Carregando…")
            .setTitle("Postos próximos")
            .setHeaderAction(Action.BACK)
            .setLoading(true)
            .build()

    private fun messageTemplate(message: String): Template =
        MessageTemplate.Builder(message)
            .setTitle("Postos próximos")
            .setHeaderAction(Action.BACK)
            .build()

    private fun errorTemplate(error: AppError): Template {
        val msg = if (error == AppError.Unauthorized)
            "Sessão expirada. Abra o FlowFuel no celular para entrar novamente."
        else
            "Erro ao carregar postos. Verifique sua conexão."
        val builder = MessageTemplate.Builder(msg)
            .setTitle("Postos próximos")
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

    private fun successTemplate(stations: List<Station>): Template {
        val listBuilder = ItemList.Builder()
        stations.forEach { station ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(station.name)
                    .addText(formatDistance(station.distanceMeters))
                    .setOnClickListener { navigateTo(station) }
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Postos próximos")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
