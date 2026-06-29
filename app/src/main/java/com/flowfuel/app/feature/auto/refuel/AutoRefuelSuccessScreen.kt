package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

class AutoRefuelSuccessScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = MessageTemplate.Builder(
        "Abastecimento registrado com sucesso!"
    )
        .setTitle("FlowFuel")
        .setHeaderAction(Action.APP_ICON)
        .addAction(
            Action.Builder()
                .setTitle("Voltar ao painel")
                .setOnClickListener { screenManager.popToRoot() }
                .build()
        )
        .build()
}
