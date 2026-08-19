package com.flowfuel.app.feature.auto

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.feature.auto.menu.AutoMenuScreen
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetUpcomingMaintenanceUseCase
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsUseCase
import kotlinx.coroutines.runBlocking

class AutoSession(
    private val sessionStore: SessionStore,
    private val getActiveVehicle: GetActiveVehicleUseCase,
    private val createRefuel: CreateRefuelUseCase,
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val locationProvider: LocationProvider,
    private val getVehicleEvents: GetVehicleEventsUseCase,
    private val getUpcomingMaintenance: GetUpcomingMaintenanceUseCase,
    private val getDashboard: GetDashboardUseCase,
) : androidx.car.app.Session() {

    override fun onCreateScreen(intent: Intent): Screen = runBlocking {
        createInitialScreen(carContext, sessionStore.accessToken())
    }

    internal fun createInitialScreen(carContext: CarContext, token: String?): Screen =
        if (token.isNullOrBlank()) AutoLoginScreen(carContext)
        else AutoMenuScreen(
            carContext,
            getActiveVehicle,
            createRefuel,
            getNearbyStations,
            locationProvider,
            getVehicleEvents,
            getUpcomingMaintenance,
            getDashboard,
        )
}
