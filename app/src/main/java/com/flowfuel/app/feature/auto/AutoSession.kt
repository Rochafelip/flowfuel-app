package com.flowfuel.app.feature.auto

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.feature.auto.dashboard.AutoDashboardScreen
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import kotlinx.coroutines.runBlocking

class AutoSession(
    private val sessionStore: SessionStore,
    private val getActiveVehicle: GetActiveVehicleUseCase,
    private val getDashboard: GetDashboardUseCase,
    private val createRefuel: CreateRefuelUseCase,
) : androidx.car.app.Session() {

    override fun onCreateScreen(intent: Intent): Screen = runBlocking {
        createInitialScreen(carContext, sessionStore.accessToken())
    }

    internal fun createInitialScreen(carContext: CarContext, token: String?): Screen =
        if (token.isNullOrBlank()) AutoLoginScreen(carContext)
        else AutoDashboardScreen(carContext, getActiveVehicle, getDashboard, createRefuel)
}
