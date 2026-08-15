package com.flowfuel.app.feature.home.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun `formatBrl formata em pt-BR`() {
        val nbsp = Char(160)
        val expected = "R" + "$" + nbsp + "480,00"
        assertEquals(expected, formatBrl(480.0))
    }

    @Test
    fun `formatInteger agrupa milhar sem decimais`() {
        assertEquals("50.000", formatInteger(50_000))
        assertEquals("67.270", formatInteger(67_270))
        assertEquals("0", formatInteger(0))
    }

    @Test
    fun `formatDecimal2 usa sempre 2 casas decimais com virgula`() {
        assertEquals("12,50", formatDecimal2(12.5))
        assertEquals("42,30", formatDecimal2(42.3))
        assertEquals("0,00", formatDecimal2(0.0))
    }

    @Test
    fun `formatPercent arredonda para inteiro sem casas decimais`() {
        assertEquals("27%", formatPercent(27.4))
        assertEquals("28%", formatPercent(27.5))
        assertEquals("0%", formatPercent(0.0))
    }

    @Test
    fun `formatLastRefuelLabel cobre os 4 casos`() {
        assertEquals("Nenhum abastecimento ainda", formatLastRefuelLabel(null))
        assertEquals("Hoje", formatLastRefuelLabel(0))
        assertEquals("Hoje", formatLastRefuelLabel(-1))
        assertEquals("Ontem", formatLastRefuelLabel(1))
        assertEquals("Há 5 dias", formatLastRefuelLabel(5))
    }

    @Test
    fun `formatActivityDate lida com timestamp e com data pura`() {
        assertEquals("15/08/2026", formatActivityDate("2026-08-15T10:30:00"))
        assertEquals("15/08/2026", formatActivityDate("2026-08-15"))
    }

    @Test
    fun `formatDate converte iso para dd-mm-yyyy`() {
        assertEquals("15/08/2026", formatDate("2026-08-15T10:30:00"))
    }

    @Test
    fun `formatMonthAbbrev converte yyyy-MM para abreviacao pt-BR`() {
        assertEquals("jan", formatMonthAbbrev("2026-01"))
        assertEquals("mar", formatMonthAbbrev("2026-03"))
        assertEquals("ago", formatMonthAbbrev("2026-08"))
        assertEquals("dez", formatMonthAbbrev("2026-12"))
    }
}
