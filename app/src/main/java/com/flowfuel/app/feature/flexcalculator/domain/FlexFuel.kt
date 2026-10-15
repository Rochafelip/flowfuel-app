package com.flowfuel.app.feature.flexcalculator.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

enum class FuelWinner { Ethanol, Gasoline, Tie }

data class RatioResult(val ratio: Double, val winner: FuelWinner)

data class ExactCostResult(
    val ethanolCostPerKm: Double,
    val gasolineCostPerKm: Double,
    val winner: FuelWinner,
    val savingsPercent: Double,
)

/** Cálculos da calculadora flex (etanol × gasolina). Funções puras, sem estado. */
object FlexFuel {

    private const val RATIO_THRESHOLD = 0.7

    /**
     * Aceita vírgula ou ponto como separador decimal. Retorna null para vazio,
     * não numérico, zero ou negativo.
     */
    fun parseDecimal(text: String): Double? {
        val normalized = text.trim().replaceFirst(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value <= 0.0) return null
        return value
    }

    fun ratioRule(ethanolPrice: Double, gasolinePrice: Double): RatioResult {
        val ratio = ethanolPrice / gasolinePrice
        // Arredonda a 4 casas para que 70,00% exato não vire gasolina por erro de ponto flutuante.
        val rounded = (ratio * 10_000).roundToLong() / 10_000.0
        val winner = when {
            rounded < RATIO_THRESHOLD -> FuelWinner.Ethanol
            rounded > RATIO_THRESHOLD -> FuelWinner.Gasoline
            else -> FuelWinner.Tie
        }
        return RatioResult(ratio, winner)
    }

    fun exactCost(
        ethanolPrice: Double,
        gasolinePrice: Double,
        ethanolKmPerLiter: Double,
        gasolineKmPerLiter: Double,
    ): ExactCostResult {
        val ethanolCostPerKm = ethanolPrice / ethanolKmPerLiter
        val gasolineCostPerKm = gasolinePrice / gasolineKmPerLiter

        if (abs(ethanolCostPerKm - gasolineCostPerKm) < 1e-9) {
            return ExactCostResult(ethanolCostPerKm, gasolineCostPerKm, FuelWinner.Tie, 0.0)
        }

        val winner = if (ethanolCostPerKm < gasolineCostPerKm) FuelWinner.Ethanol else FuelWinner.Gasoline
        val cheaper = min(ethanolCostPerKm, gasolineCostPerKm)
        val dearer = max(ethanolCostPerKm, gasolineCostPerKm)
        val savingsPercent = (dearer - cheaper) / dearer * 100

        return ExactCostResult(ethanolCostPerKm, gasolineCostPerKm, winner, savingsPercent)
    }
}
