package com.flowfuel.app.feature.auto.dashboard

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase

class AutoDashboardScreen(
    carContext: CarContext,
    private val getActiveVehicle: GetActiveVehicleUseCase,
    private val getDashboard: GetDashboardUseCase,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {
    override fun onGetTemplate(): Template =
        MessageTemplate.Builder("Carregando…").setTitle("FlowFuel").build()
}
