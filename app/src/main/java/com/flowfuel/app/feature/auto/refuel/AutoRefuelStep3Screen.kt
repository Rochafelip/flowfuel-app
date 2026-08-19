package com.flowfuel.app.feature.auto.refuel

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Template
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import java.text.NumberFormat
import java.util.Locale

private const val MAX_DIGITS = 7

class AutoRefuelStep3Screen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val odometerInput: OdometerInput,
    private val liters: Double,
    private val createRefuel: CreateRefuelUseCase,
) : Screen(carContext) {

    private var rawDigits: String = ""

    internal fun testDigit(d: Char) = onDigit(d)
    internal fun testBackspace() = onBackspace()
    internal fun testConfirm() = onConfirm()
    internal fun testLiters() = liters

    private fun onDigit(d: Char) {
        if (rawDigits.length < MAX_DIGITS) rawDigits += d
        invalidate()
    }

    private fun onBackspace() {
        rawDigits = rawDigits.dropLast(1)
        invalidate()
    }

    private fun onConfirm() {
        val price = rawDigitsToCents(rawDigits)
        if (price <= 0) {
            CarToast.makeText(
                carContext,
                "Informe o valor total válido (ex: 289,90)",
                CarToast.LENGTH_SHORT,
            ).show()
        } else {
            screenManager.push(
                AutoRefuelConfirmScreen(carContext, vehicle, odometerInput, liters, price, createRefuel)
            )
        }
    }

    override fun onGetTemplate(): Template {
        val price = rawDigitsToCents(rawDigits)
        val formatted = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(price)

        val items = buildKeypadItemList(
            carContext,
            onDigit = ::onDigit,
            onBackspace = ::onBackspace,
            onConfirm = ::onConfirm,
        )

        return GridTemplate.Builder()
            .setSingleList(items)
            .setTitle("Valor: $formatted")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
