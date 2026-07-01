package com.flowfuel.app.feature.export.data

import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFormattingTest {

    private fun vehicle(licensePlate: String?, energyType: EnergyType) = Vehicle(
        id = 1,
        brand = "Toyota",
        model = "Corolla",
        manufactureYear = 2022,
        modelYear = 2022,
        licensePlate = licensePlate,
        color = "Prata",
        type = VehicleType.Car,
        energyType = energyType,
        fuelType = FuelType.Gasoline,
        odometerKm = 10000,
        tankCapacityL = 50.0,
        batteryCapacityKwh = null,
        isActive = true,
    )

    @Test
    fun `vehicleLabel includes plate when present`() {
        val label = vehicleLabel(vehicle(licensePlate = "ABC1D23", energyType = EnergyType.Combustion))

        assertEquals("Toyota Corolla — ABC1D23", label)
    }

    @Test
    fun `vehicleLabel omits plate when null or blank`() {
        assertEquals("Toyota Corolla", vehicleLabel(vehicle(licensePlate = null, energyType = EnergyType.Combustion)))
        assertEquals("Toyota Corolla", vehicleLabel(vehicle(licensePlate = "  ", energyType = EnergyType.Combustion)))
    }

    @Test
    fun `periodLabel formats both dates when present`() {
        val label = periodLabel("2026-01-05", "2026-01-31")

        assertEquals("05/01/2026 – 31/01/2026", label)
    }

    @Test
    fun `periodLabel falls back to full history when either date missing`() {
        assertEquals("Todo o histórico", periodLabel(null, null))
        assertEquals("Todo o histórico", periodLabel("2026-01-05", null))
    }

    @Test
    fun `energyUnit and consumptionUnit depend on energy type`() {
        val electric = vehicle(licensePlate = null, energyType = EnergyType.Electric)
        val combustion = vehicle(licensePlate = null, energyType = EnergyType.Combustion)

        assertEquals("kWh", energyUnit(electric))
        assertEquals("km/kWh", consumptionUnit(electric))
        assertEquals("L", energyUnit(combustion))
        assertEquals("km/L", consumptionUnit(combustion))
    }
}
