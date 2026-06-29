package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase

class AutoRefuelStep2Screen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val tripKm: Double,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    override fun onGetTemplate(): Template = SearchTemplate.Builder(
        object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {}
            override fun onSearchSubmitted(searchText: String) {
                val liters = searchText.trim().replace(",", ".").toDoubleOrNull()
                if (liters == null || liters <= 0) {
                    CarToast.makeText(
                        carContext,
                        "Informe litros abastecidos válidos (ex: 45,5)",
                        CarToast.LENGTH_SHORT,
                    ).show()
                } else {
                    screenManager.push(
                        AutoRefuelStep3Screen(carContext, vehicle, tripKm, liters, createRefuel)
                    )
                }
            }
        }
    )
        .setHeaderAction(Action.BACK)
        .setSearchHint("Passo 2/3 — litros abastecidos (ex: 45,5)")
        .setShowKeyboardByDefault(true)
        .build()
}
