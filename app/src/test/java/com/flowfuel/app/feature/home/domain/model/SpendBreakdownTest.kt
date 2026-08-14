package com.flowfuel.app.feature.home.domain.model

import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class SpendBreakdownTest {

    private fun event(category: EventCategory, amount: Double?) = VehicleEvent(
        id = 1,
        vehicleId = 1,
        category = category,
        title = category.label,
        description = null,
        amount = amount,
        eventDate = "2026-01-01",
        odometerKm = null,
        notes = null,
        receiptUrl = null,
        createdAt = null,
        updatedAt = null,
    )

    @Test
    fun `merges FUEL-category events into the same Combustível slice as fuelSpent`() {
        val events = listOf(event(EventCategory.FUEL, 50.0))

        val breakdown = buildSpendBreakdown(fuelSpent = 100.0, events = events)

        assertEquals(150.0, breakdown.totalSpent, 0.001)
        assertEquals(listOf(SpendSlice("Combustível", 150.0)), breakdown.slices)
    }

    @Test
    fun `folds categories beyond the top 5 plus the native Outros category into a single Outros slice`() {
        val events = listOf(
            event(EventCategory.MAINTENANCE, 100.0),
            event(EventCategory.OIL_CHANGE, 90.0),
            event(EventCategory.WASH, 80.0),
            event(EventCategory.TIRES, 70.0),
            event(EventCategory.INSURANCE, 60.0),
            event(EventCategory.TAX, 10.0),
            event(EventCategory.DOCUMENTS, 5.0),
            event(EventCategory.OTHER, 3.0),
        )

        val breakdown = buildSpendBreakdown(fuelSpent = 200.0, events = events)

        // top 5 named: Combustível(200), Manutenção(100), Troca de Óleo(90), Lavagem(80), Pneus(70)
        // resto (Seguro 60 + Imposto 10 + Documentos 5 + Outros nativo 3) = 78 -> 1 fatia "Outros"
        assertEquals(6, breakdown.slices.size)
        assertEquals(SpendSlice("Outros", 78.0), breakdown.slices.last())
        assertEquals(618.0, breakdown.totalSpent, 0.001)
    }

    @Test
    fun `does not fold when there are 5 or fewer categories with spend`() {
        val events = listOf(
            event(EventCategory.MAINTENANCE, 100.0),
            event(EventCategory.OIL_CHANGE, 50.0),
        )

        val breakdown = buildSpendBreakdown(fuelSpent = 200.0, events = events)

        assertEquals(3, breakdown.slices.size)
        assertEquals(
            setOf("Combustível", "Manutenção", "Troca de Óleo"),
            breakdown.slices.map { it.label }.toSet(),
        )
    }

    @Test
    fun `returns a single Combustível slice when there are no events`() {
        val breakdown = buildSpendBreakdown(fuelSpent = 148.42, events = emptyList())

        assertEquals(listOf(SpendSlice("Combustível", 148.42)), breakdown.slices)
        assertEquals(148.42, breakdown.totalSpent, 0.001)
    }
}
