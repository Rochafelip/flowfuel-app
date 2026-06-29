package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase

class AutoRefuelStep1Screen(
    carContext: CarContext,
    val vehicleId: Int,
    val currentKm: Int,
    val energyType: String,
    val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {
    override fun onGetTemplate(): Template =
        MessageTemplate.Builder("TODO").setTitle("Abastecimento").build()
}
