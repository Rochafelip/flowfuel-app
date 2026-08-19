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

class AutoRefuelStep1Screen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    private enum class Mode { TRIP, ODOMETER }

    private var mode: Mode = Mode.TRIP
    private var inputText: String = ""

    internal fun testAdvance(text: String) = advance(text)
    internal fun testToggleMode() = toggleMode()

    private fun toggleMode() {
        mode = if (mode == Mode.TRIP) Mode.ODOMETER else Mode.TRIP
        inputText = ""
        invalidate()
    }

    private fun advance(text: String) {
        val value = text.trim().replace(",", ".").toDoubleOrNull()
        if (value == null || value <= 0) {
            val message = if (mode == Mode.TRIP)
                "Informe km percorridos válidos (ex: 150)"
            else
                "Informe a leitura do odômetro válida (ex: ${vehicle.currentKm})"
            CarToast.makeText(carContext, message, CarToast.LENGTH_SHORT).show()
        } else {
            val odometerInput = if (mode == Mode.TRIP) OdometerInput.Trip(value) else OdometerInput.Odometer(value)
            screenManager.push(
                AutoRefuelStep2Screen(carContext, vehicle, odometerInput, createRefuel)
            )
        }
    }

    override fun onGetTemplate(): Template {
        val hint = if (mode == Mode.TRIP) "Ex: 150" else "Ex: ${vehicle.currentKm}"
        val instructions = if (mode == Mode.TRIP)
            "Km percorridos desde o último abastecimento"
        else
            "Odômetro atual (km)"
        val toggleLabel = if (mode == Mode.TRIP) "Digitar odômetro total" else "Digitar percurso"

        val method = InputSignInMethod.Builder(
            object : InputCallback {
                override fun onInputTextChanged(text: String) { inputText = text }
                override fun onInputSubmitted(text: String) { advance(text) }
            }
        )
            .setHint(hint)
            .setKeyboardType(InputSignInMethod.KEYBOARD_NUMBER)
            .setShowKeyboardByDefault(true)
            .build()

        return SignInTemplate.Builder(method)
            .setTitle("Passo 1 de 3")
            .setHeaderAction(Action.BACK)
            .setInstructions(instructions)
            .addAction(
                Action.Builder()
                    .setTitle("Próximo")
                    .setOnClickListener(ParkedOnlyOnClickListener.create { advance(inputText) })
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(toggleLabel)
                    .setOnClickListener(ParkedOnlyOnClickListener.create { toggleMode() })
                    .build()
            )
            .build()
    }
}
