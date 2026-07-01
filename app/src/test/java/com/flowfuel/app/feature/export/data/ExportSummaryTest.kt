package com.flowfuel.app.feature.export.data

import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportSummaryTest {

    private fun refuel(totalPrice: Double, energyAmount: Double, consumption: Double?) = RefuelItem(
        id = 1,
        date = "2026-01-01",
        energyAmount = energyAmount,
        pricePerUnit = totalPrice / energyAmount,
        totalPrice = totalPrice,
        fullTank = true,
        refuelType = "FUEL",
        odometer = null,
        trip = null,
        consumption = consumption,
    )

    private fun event(amount: Double?, category: EventCategory) = VehicleEvent(
        id = 1,
        vehicleId = 1,
        category = category,
        title = "Evento",
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
    fun `buildRefuelsSummary sums spent and energy, averages non-null consumption`() {
        val items = listOf(
            refuel(totalPrice = 200.0, energyAmount = 40.0, consumption = 10.0),
            refuel(totalPrice = 100.0, energyAmount = 20.0, consumption = 12.0),
            refuel(totalPrice = 50.0, energyAmount = 10.0, consumption = null),
        )

        val summary = buildRefuelsSummary(items)

        assertEquals(350.0, summary.totalSpent, 0.0001)
        assertEquals(70.0, summary.totalEnergy, 0.0001)
        assertEquals(11.0, summary.averageConsumption!!, 0.0001)
        assertEquals(3, summary.count)
    }

    @Test
    fun `buildRefuelsSummary returns null average when no item has consumption`() {
        val items = listOf(refuel(totalPrice = 100.0, energyAmount = 20.0, consumption = null))

        val summary = buildRefuelsSummary(items)

        assertNull(summary.averageConsumption)
    }

    @Test
    fun `buildRefuelsSummary handles empty list`() {
        val summary = buildRefuelsSummary(emptyList())

        assertEquals(0.0, summary.totalSpent, 0.0001)
        assertEquals(0.0, summary.totalEnergy, 0.0001)
        assertNull(summary.averageConsumption)
        assertEquals(0, summary.count)
    }

    @Test
    fun `buildEventsSummary sums amount treating null as zero and counts by category desc`() {
        val items = listOf(
            event(amount = 150.0, category = EventCategory.MAINTENANCE),
            event(amount = 50.0, category = EventCategory.MAINTENANCE),
            event(amount = null, category = EventCategory.WASH),
            event(amount = 300.0, category = EventCategory.INSURANCE),
        )

        val summary = buildEventsSummary(items)

        assertEquals(500.0, summary.totalSpent, 0.0001)
        assertEquals(4, summary.count)
        assertEquals(
            listOf(EventCategory.MAINTENANCE to 2, EventCategory.INSURANCE to 1, EventCategory.WASH to 1)
                .sortedByDescending { it.second },
            summary.countByCategory,
        )
    }
}
