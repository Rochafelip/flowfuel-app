package com.flowfuel.app.feature.home.domain.model

import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent

data class SpendBreakdown(
    val totalSpent: Double,
    /**
     * No máximo 6 fatias: até 5 categorias nomeadas + "Outros" agrupando o
     * resto. Ordem é por valor decrescente (rank), com "Outros" sempre por
     * último independente do valor dele — a cor de cada fatia em
     * [SpendBreakdownCard] segue essa mesma posição (rank), não a
     * categoria: a cor de "Combustível" pode mudar entre carregamentos se o
     * rank dele mudar. Decisão consciente do usuário.
     */
    val slices: List<SpendSlice>,
)

data class SpendSlice(
    val label: String,
    val amount: Double,
)

data class SpendBreakdownOverview(
    val monthly: SpendBreakdown,
    val total: SpendBreakdown,
    /** Delta percentual do mês atual vs. mês anterior completo; null se o mês anterior não teve gastos. */
    val percentDelta: Double? = null,
)

/**
 * Resultado de [com.flowfuel.app.feature.home.domain.usecase.GetMonthlySpendBreakdownUseCase]:
 * o detalhamento por categoria do mês atual, mais o total do mês anterior (só para o
 * cálculo de [percentDelta]).
 */
data class MonthlyFinancialSummary(
    val breakdown: SpendBreakdown,
    val previousMonthTotal: Double,
) {
    val percentDelta: Double?
        get() = if (previousMonthTotal > 0.0)
            ((breakdown.totalSpent - previousMonthTotal) / previousMonthTotal) * 100.0
        else null
}

private const val MAX_NAMED_SLICES = 5
private const val OTHER_LABEL = "Outros"

/**
 * Funde o gasto com abastecimentos ([fuelSpent]) com eventos manuais de
 * categoria FUEL na mesma fatia "Combustível" (ver
 * docs/superpowers/specs/2026-08-14-spend-breakdown-donut-design.md),
 * agrupa o resto dos eventos por categoria e recolhe tudo além das 5
 * maiores + a categoria nativa "Outros" numa única fatia "Outros" ao final.
 */
fun buildSpendBreakdown(fuelSpent: Double, events: List<VehicleEvent>): SpendBreakdown {
    val amountsByLabel = linkedMapOf<String, Double>()
    amountsByLabel[EventCategory.FUEL.label] = fuelSpent
    for (event in events) {
        val label = event.category.label
        amountsByLabel[label] = (amountsByLabel[label] ?: 0.0) + (event.amount ?: 0.0)
    }

    val otherAmount = amountsByLabel.remove(EventCategory.OTHER.label) ?: 0.0
    val sorted = amountsByLabel.entries.sortedByDescending { it.value }
    val foldedTail = sorted.drop(MAX_NAMED_SLICES).sumOf { it.value } + otherAmount

    val namedSlices = sorted.take(MAX_NAMED_SLICES).map { (label, amount) -> SpendSlice(label, amount) }

    val slices = namedSlices +
        if (foldedTail > 0.0) listOf(SpendSlice(OTHER_LABEL, foldedTail)) else emptyList()

    return SpendBreakdown(
        totalSpent = fuelSpent + events.sumOf { it.amount ?: 0.0 },
        slices = slices,
    )
}
