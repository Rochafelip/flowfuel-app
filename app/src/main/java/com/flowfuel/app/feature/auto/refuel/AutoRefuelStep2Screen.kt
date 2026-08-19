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

class AutoRefuelStep2Screen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val odometerInput: OdometerInput,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    private var rawDigits: String = ""

    internal fun testDigit(d: Char) = onDigit(d)
    internal fun testBackspace() = onBackspace()
    internal fun testConfirm() = onConfirm()
    internal fun testOdometerInput() = odometerInput

    private fun onDigit(d: Char) {
        if (rawDigits.length < MAX_DIGITS) rawDigits += d
        invalidate()
    }

    private fun onBackspace() {
        rawDigits = rawDigits.dropLast(1)
        invalidate()
    }

    private fun onConfirm() {
        val liters = rawDigitsToTenths(rawDigits)
        if (liters <= 0) {
            CarToast.makeText(
                carContext,
                "Informe litros abastecidos válidos (ex: 45,5)",
                CarToast.LENGTH_SHORT,
            ).show()
        } else {
            screenManager.push(
                AutoRefuelStep3Screen(carContext, vehicle, odometerInput, liters, createRefuel)
            )
        }
    }

    override fun onGetTemplate(): Template {
        val liters = rawDigitsToTenths(rawDigits)
        val formatted = String.format(Locale("pt", "BR"), "%.1f", liters)

        val items = buildKeypadItemList(
            carContext,
            onDigit = ::onDigit,
            onBackspace = ::onBackspace,
            onConfirm = ::onConfirm,
        )

        return GridTemplate.Builder()
            .setSingleList(items)
            .setTitle("Litros: $formatted L")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
