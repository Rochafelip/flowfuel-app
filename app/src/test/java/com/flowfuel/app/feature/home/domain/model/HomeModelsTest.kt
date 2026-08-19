package com.flowfuel.app.feature.home.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeModelsTest {

    @Test
    fun `troca de oleo em dia mostra km restantes`() {
        val item = UpcomingMaintenanceItem(type = UpcomingMaintenanceType.OIL_CHANGE, remainingKm = 320)
        val status = item.toStatusText()
        assertEquals("Troca de óleo", status.title)
        assertEquals("Em 320 km", status.subtitle)
    }

    @Test
    fun `rodizio de pneus atrasado por km`() {
        val item = UpcomingMaintenanceItem(
            type = UpcomingMaintenanceType.TIRE_ROTATION, remainingKm = -150, isOverdue = true,
        )
        val status = item.toStatusText()
        assertEquals("Rodízio de pneus", status.title)
        assertEquals("Atrasado 150 km", status.subtitle)
    }

    @Test
    fun `licenciamento vence em N dias`() {
        val item = UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, remainingDays = 18)
        val status = item.toStatusText()
        assertEquals("Licenciamento", status.title)
        assertEquals("Vence em 18 dias", status.subtitle)
    }

    @Test
    fun `licenciamento vence hoje`() {
        val item = UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, remainingDays = 0)
        assertEquals("Vence hoje", item.toStatusText().subtitle)
    }

    @Test
    fun `licenciamento atrasado por dias`() {
        val item = UpcomingMaintenanceItem(
            type = UpcomingMaintenanceType.LICENSING, remainingDays = -5, isOverdue = true,
        )
        assertEquals("Venceu há 5 dias", item.toStatusText().subtitle)
    }

    @Test
    fun `licenciamento sem data configurada pede pra definir`() {
        val item = UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, needsSetup = true)
        assertEquals("Defina a data de licenciamento", item.toStatusText().subtitle)
    }
}
