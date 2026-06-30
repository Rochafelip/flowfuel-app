package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.InputCallback
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Template
import androidx.car.app.model.signin.InputSignInMethod
import androidx.car.app.model.signin.SignInTemplate
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase

class AutoRefuelStep3Screen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val tripKm: Double,
    private val liters: Double,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    private var inputText: String = ""

    internal fun testAdvance(text: String) = advance(text)

    private fun advance(text: String) {
        val price = text.trim().replace(",", ".").toDoubleOrNull()
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

    override fun onGetTemplate(): Template {
        val method = InputSignInMethod.Builder(
            object : InputCallback {
                override fun onInputTextChanged(text: String) { inputText = text }
                override fun onInputSubmitted(text: String) { advance(text) }
            }
        )
            .setHint("Ex: 289,90")
            .setKeyboardType(InputSignInMethod.KEYBOARD_NUMBER)
            .setShowKeyboardByDefault(true)
            .build()

        return SignInTemplate.Builder(method)
            .setTitle("Passo 3 de 3")
            .setHeaderAction(Action.BACK)
            .setInstructions("Valor total pago em R$")
            .addAction(
                Action.Builder()
                    .setTitle("Próximo")
                    .setOnClickListener(ParkedOnlyOnClickListener.create { advance(inputText) })
                    .build()
            )
            .build()
    }
}
