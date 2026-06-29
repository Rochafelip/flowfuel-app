package com.flowfuel.app.feature.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

class AutoLoginScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template =
        MessageTemplate.Builder("Abra o app FlowFuel no celular para fazer login.")
            .setTitle("FlowFuel")
            .setHeaderAction(Action.APP_ICON)
            .build()
}
