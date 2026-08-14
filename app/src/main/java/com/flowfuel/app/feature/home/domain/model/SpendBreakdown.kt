package com.flowfuel.app.feature.home.domain.model

import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent

data class SpendBreakdown(
    val totalSpent: Double,
    /** No máximo 6 fatias: até 5 categorias nomeadas + "Outros" agrupando o resto. */
    val slices: List<SpendSlice>,
)

data class SpendSlice(
    val label: String,
    val amount: Double,
)

private const val MAX_NAMED_SLICES = 5
private const val OTHER_LABEL = "Outros"

/**
 * Ordem fixa de exibição (fatias do donut e linhas da legenda) — a mesma
 * ordem usada para atribuir cor por categoria em [SpendBreakdownCard]. A
 * seleção de quais categorias entram como fatia nomeada é por valor (as 5
 * maiores); a ordem em que aparecem na tela não é.
 */
private val CATEGORY_DISPLAY_ORDER = listOf(
    EventCategory.FUEL,
    EventCategory.MAINTENANCE,
    EventCategory.OIL_CHANGE,
    EventCategory.WASH,
    EventCategory.TIRES,
    EventCategory.INSURANCE,
    EventCategory.TAX,
    EventCategory.DOCUMENTS,
).map { it.label }

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
    val keptLabels = sorted.take(MAX_NAMED_SLICES).map { it.key }.toSet()
    val foldedTail = sorted.drop(MAX_NAMED_SLICES).sumOf { it.value } + otherAmount

    val namedSlices = CATEGORY_DISPLAY_ORDER
        .filter { it in keptLabels }
        .map { label -> SpendSlice(label, amountsByLabel.getValue(label)) }

    val slices = namedSlices +
        if (foldedTail > 0.0) listOf(SpendSlice(OTHER_LABEL, foldedTail)) else emptyList()

    return SpendBreakdown(
        totalSpent = fuelSpent + events.sumOf { it.amount ?: 0.0 },
        slices = slices,
    )
}
