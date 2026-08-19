package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Template
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import java.util.Locale

private const val MAX_DIGITS = 6

class AutoRefuelStep1Screen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    private enum class Mode { TRIP, ODOMETER }

    private var mode: Mode = Mode.TRIP
    private var rawDigits: String = ""

    internal fun testDigit(d: Char) = onDigit(d)
    internal fun testBackspace() = onBackspace()
    internal fun testConfirm() = onConfirm()
    internal fun testToggleMode() = toggleMode()

    private fun onDigit(d: Char) {
        if (rawDigits.length < MAX_DIGITS) rawDigits += d
        invalidate()
    }

    private fun onBackspace() {
        rawDigits = rawDigits.dropLast(1)
        invalidate()
    }

    private fun toggleMode() {
        mode = if (mode == Mode.TRIP) Mode.ODOMETER else Mode.TRIP
        rawDigits = ""
        invalidate()
    }

    private fun onConfirm() {
        val value = rawDigitsToTenths(rawDigits)
        if (value <= 0) {
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
        val value = rawDigitsToTenths(rawDigits)
        val formatted = String.format(Locale("pt", "BR"), "%.1f", value)
        val label = if (mode == Mode.TRIP) "Percurso" else "Odômetro"
        val toggleTitle = if (mode == Mode.TRIP) "Digitar odômetro total" else "Digitar percurso"

        val toggleItem = keypadItem(carContext, glyph = "⇄", title = toggleTitle) { toggleMode() }
        val items = buildKeypadItemList(
            carContext,
            onDigit = ::onDigit,
            onBackspace = ::onBackspace,
            onConfirm = ::onConfirm,
            extraItem = toggleItem,
        )

        return GridTemplate.Builder()
            .setSingleList(items)
            .setTitle("$label: $formatted km")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
