package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase

class AutoRefuelStep3Screen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val tripKm: Double,
    private val liters: Double,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    override fun onGetTemplate(): Template = SearchTemplate.Builder(
        object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {}
            override fun onSearchSubmitted(searchText: String) {
                val price = searchText.trim().replace(",", ".").toDoubleOrNull()
                if (price == null || price <= 0) {
                    CarToast.makeText(
                        carContext,
                        "Informe o valor total válido (ex: 289,90)",
                        CarToast.LENGTH_SHORT,
                    ).show()
                } else {
                    screenManager.push(
                        AutoRefuelConfirmScreen(carContext, vehicle, tripKm, liters, price, createRefuel)
                    )
                }
            }
        }
    )
        .setHeaderAction(Action.BACK)
        .setSearchHint("Passo 3/3 — valor total em R$ (ex: 289,90)")
        .setShowKeyboardByDefault(true)
        .build()
}
