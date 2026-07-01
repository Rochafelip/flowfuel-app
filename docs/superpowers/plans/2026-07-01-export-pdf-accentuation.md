# Export: Correção de Acentuação + Exportação em PDF Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corrigir a exibição de acentos no CSV exportado (bug de encoding) e adicionar exportação em PDF (relatório elaborado com resumo) como segunda opção de formato.

**Architecture:** Lógica pura (cálculo de resumo, formatação de linhas de tabela, montagem de bytes CSV) fica isolada em funções top-level testáveis sem Robolectric. A geração do PDF usa `android.graphics.pdf.PdfDocument` nativo (sem dependência nova) numa classe dedicada, testada com Robolectric (smoke test — não valida pixel a pixel). `ExportRepositoryImpl` orquestra: busca dados → roteia por `ExportFormat` → (para PDF) busca o veículo → delega para o writer correspondente.

**Tech Stack:** Kotlin, Hilt, Coroutines, `android.graphics.pdf.PdfDocument`, JUnit4, MockK, Robolectric (já presentes no projeto).

## Global Constraints

- Formatação decimal em pt-BR: vírgula como separador (`"%.2f".format(x).replace('.', ',')`) — já usado no CSV, deve ser mantido nos novos textos do PDF.
- CSV deve manter compatibilidade byte-a-byte com o formato atual (mesmas colunas, mesmo separador `;`), exceto pelo prefixo BOM adicionado.
- Nenhuma dependência nova de biblioteca — PDF usa apenas `android.graphics.pdf.PdfDocument`.
- Nomes de arquivo seguem o padrão existente: `flowfuel-abastecimentos.<ext>` / `flowfuel-eventos.<ext>`.

---

### Task 1: CsvBuilder — fix de acentuação via BOM UTF-8

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/export/data/CsvBuilder.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/export/data/CsvBuilderTest.kt`

**Interfaces:**
- Produces: `fun buildCsvBytes(header: List<String>, rows: List<List<String>>): ByteArray` — usada pelo `ExportRepositoryImpl` (Task 5) para montar o CSV final (header + linhas, com BOM UTF-8 prefixado e escaping de campos com `;`, `"` ou quebra de linha).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.flowfuel.app.feature.export.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvBuilderTest {

    private val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    @Test
    fun `buildCsvBytes prefixes content with UTF-8 BOM`() {
        val bytes = buildCsvBytes(header = listOf("Data", "Preço"), rows = emptyList())

        assertArrayEquals(bom, bytes.copyOfRange(0, 3))
    }

    @Test
    fun `buildCsvBytes joins header and rows with semicolon preserving accents`() {
        val bytes = buildCsvBytes(
            header = listOf("Data", "Preço/unidade", "Tanque cheio"),
            rows = listOf(listOf("2026-01-05", "5,50", "Sim")),
        )

        val text = String(bytes.copyOfRange(3, bytes.size), Charsets.UTF_8)

        assertEquals("Data;Preço/unidade;Tanque cheio\n2026-01-05;5,50;Sim\n", text)
    }

    @Test
    fun `buildCsvBytes escapes fields containing semicolon, quotes or newline`() {
        val bytes = buildCsvBytes(
            header = listOf("Título"),
            rows = listOf(
                listOf("Troca de óleo; revisão"),
                listOf("Nota com \"aspas\""),
            ),
        )

        val text = String(bytes.copyOfRange(3, bytes.size), Charsets.UTF_8)

        assertEquals(
            "Título\n\"Troca de óleo; revisão\"\n\"Nota com \"\"aspas\"\"\"\n",
            text,
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.export.data.CsvBuilderTest"`
Expected: FAIL — compilation error, `buildCsvBytes` unresolved reference.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.flowfuel.app.feature.export.data

private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

fun buildCsvBytes(header: List<String>, rows: List<List<String>>): ByteArray {
    val text = buildString {
        appendLine(header.joinToString(";") { it.csvEscape() })
        rows.forEach { row -> appendLine(row.joinToString(";") { it.csvEscape() }) }
    }
    return UTF8_BOM + text.toByteArray(Charsets.UTF_8)
}

private fun String.csvEscape() =
    if (contains(';') || contains('"') || contains('\n')) "\"${replace("\"", "\"\"")}\""
    else this
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.export.data.CsvBuilderTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/export/data/CsvBuilder.kt app/src/test/java/com/flowfuel/app/feature/export/data/CsvBuilderTest.kt
git commit -m "fix(export): prefixar CSV com BOM UTF-8 para preservar acentuação"
```

---

### Task 2: ExportSummary — cálculo de resumo para abastecimentos e eventos

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/export/data/ExportSummary.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/export/data/ExportSummaryTest.kt`

**Interfaces:**
- Consumes: `RefuelItem` (`com.flowfuel.app.feature.history.domain.model.RefuelItem`, campos `totalPrice: Double`, `energyAmount: Double`, `consumption: Double?`), `VehicleEvent` (`com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent`, campos `amount: Double?`, `category: EventCategory`), `EventCategory` (`com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory`).
- Produces:
  - `data class RefuelsSummary(val totalSpent: Double, val totalEnergy: Double, val averageConsumption: Double?, val count: Int)`
  - `fun buildRefuelsSummary(items: List<RefuelItem>): RefuelsSummary`
  - `data class EventsSummary(val totalSpent: Double, val countByCategory: List<Pair<EventCategory, Int>>, val count: Int)`
  - `fun buildEventsSummary(items: List<VehicleEvent>): EventsSummary`
  - Usados pelo `PdfReportWriter` (Task 4) e pelo `ExportRepositoryImpl` (Task 5).

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.export.data.ExportSummaryTest"`
Expected: FAIL — compilation error, `buildRefuelsSummary`/`buildEventsSummary`/`RefuelsSummary`/`EventsSummary` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.flowfuel.app.feature.export.data

import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent

data class RefuelsSummary(
    val totalSpent: Double,
    val totalEnergy: Double,
    val averageConsumption: Double?,
    val count: Int,
)

fun buildRefuelsSummary(items: List<RefuelItem>): RefuelsSummary {
    val consumptions = items.mapNotNull { it.consumption }
    return RefuelsSummary(
        totalSpent = items.sumOf { it.totalPrice },
        totalEnergy = items.sumOf { it.energyAmount },
        averageConsumption = if (consumptions.isEmpty()) null else consumptions.average(),
        count = items.size,
    )
}

data class EventsSummary(
    val totalSpent: Double,
    val countByCategory: List<Pair<EventCategory, Int>>,
    val count: Int,
)

fun buildEventsSummary(items: List<VehicleEvent>): EventsSummary {
    val countByCategory = items.groupingBy { it.category }.eachCount()
        .toList()
        .sortedByDescending { it.second }
    return EventsSummary(
        totalSpent = items.sumOf { it.amount ?: 0.0 },
        countByCategory = countByCategory,
        count = items.size,
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.export.data.ExportSummaryTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/export/data/ExportSummary.kt app/src/test/java/com/flowfuel/app/feature/export/data/ExportSummaryTest.kt
git commit -m "feat(export): calcular resumo de abastecimentos e eventos para o relatório PDF"
```

---

### Task 3: ExportFormatting — label do veículo, período e unidades

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/export/data/ExportFormatting.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/export/data/ExportFormattingTest.kt`

**Interfaces:**
- Consumes: `Vehicle` (`com.flowfuel.app.feature.vehicle.domain.model.Vehicle`, campos `brand: String`, `model: String`, `licensePlate: String?`, `energyType: EnergyType`), `EnergyType` (`com.flowfuel.app.feature.vehicle.domain.model.EnergyType`).
- Produces:
  - `fun vehicleLabel(vehicle: Vehicle): String`
  - `fun periodLabel(startDate: String?, endDate: String?): String`
  - `fun energyUnit(vehicle: Vehicle): String`
  - `fun consumptionUnit(vehicle: Vehicle): String`
  - Usados pelo `ExportRepositoryImpl` (Task 5) para montar o cabeçalho do PDF.

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.export.data.ExportFormattingTest"`
Expected: FAIL — compilation error, functions unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.flowfuel.app.feature.export.data

import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val PERIOD_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun vehicleLabel(vehicle: Vehicle): String {
    val plate = vehicle.licensePlate?.trim()?.takeIf { it.isNotEmpty() }
    return if (plate != null) "${vehicle.brand} ${vehicle.model} — $plate" else "${vehicle.brand} ${vehicle.model}"
}

fun periodLabel(startDate: String?, endDate: String?): String {
    if (startDate == null || endDate == null) return "Todo o histórico"
    val start = LocalDate.parse(startDate).format(PERIOD_FORMATTER)
    val end = LocalDate.parse(endDate).format(PERIOD_FORMATTER)
    return "$start – $end"
}

fun energyUnit(vehicle: Vehicle): String = if (vehicle.energyType == EnergyType.Electric) "kWh" else "L"

fun consumptionUnit(vehicle: Vehicle): String = if (vehicle.energyType == EnergyType.Electric) "km/kWh" else "km/L"
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.export.data.ExportFormattingTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/export/data/ExportFormatting.kt app/src/test/java/com/flowfuel/app/feature/export/data/ExportFormattingTest.kt
git commit -m "feat(export): helpers de formatação de veículo, período e unidades para o PDF"
```

---

### Task 4: PdfReportWriter — geração do PDF com PdfDocument

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/feature/export/data/pdf/PdfReportWriter.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/export/data/pdf/PdfReportWriterTest.kt`

**Interfaces:**
- Consumes: `RefuelsSummary`, `EventsSummary` (Task 2, mesmo pacote `com.flowfuel.app.feature.export.data`).
- Produces:
  - `class PdfReportWriter @Inject constructor()`
  - `fun writeRefuelsReport(vehicleLabel: String, periodLabel: String, summary: RefuelsSummary, energyUnit: String, consumptionUnit: String, tableHeader: List<String>, tableRows: List<List<String>>): ByteArray`
  - `fun writeEventsReport(vehicleLabel: String, periodLabel: String, summary: EventsSummary, tableHeader: List<String>, tableRows: List<List<String>>): ByteArray`
  - Usado pelo `ExportRepositoryImpl` (Task 5).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.flowfuel.app.feature.export.data.pdf

import com.flowfuel.app.feature.export.data.EventsSummary
import com.flowfuel.app.feature.export.data.RefuelsSummary
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PdfReportWriterTest {

    private val writer = PdfReportWriter()

    private fun isValidPdf(bytes: ByteArray) =
        bytes.size > 4 && String(bytes.copyOfRange(0, 4), Charsets.ISO_8859_1) == "%PDF"

    @Test
    fun `writeRefuelsReport produces valid pdf bytes`() {
        val bytes = writer.writeRefuelsReport(
            vehicleLabel = "Toyota Corolla — ABC1D23",
            periodLabel = "01/01/2026 – 31/01/2026",
            summary = RefuelsSummary(totalSpent = 450.0, totalEnergy = 120.0, averageConsumption = 12.5, count = 3),
            energyUnit = "L",
            consumptionUnit = "km/L",
            tableHeader = listOf("Data", "Tipo", "Quantidade"),
            tableRows = listOf(listOf("2026-01-05", "FUEL", "40,00")),
        )

        assertTrue(isValidPdf(bytes))
    }

    @Test
    fun `writeRefuelsReport handles large row counts without throwing`() {
        val manyRows = (1..80).map { listOf("2026-01-${"%02d".format((it % 28) + 1)}", "FUEL", "40,00") }

        val bytes = writer.writeRefuelsReport(
            vehicleLabel = "Toyota Corolla",
            periodLabel = "Todo o histórico",
            summary = RefuelsSummary(totalSpent = 1000.0, totalEnergy = 800.0, averageConsumption = 11.0, count = 80),
            energyUnit = "L",
            consumptionUnit = "km/L",
            tableHeader = listOf("Data", "Tipo", "Quantidade"),
            tableRows = manyRows,
        )

        assertTrue(isValidPdf(bytes))
    }

    @Test
    fun `writeEventsReport produces valid pdf bytes`() {
        val bytes = writer.writeEventsReport(
            vehicleLabel = "Toyota Corolla",
            periodLabel = "Todo o histórico",
            summary = EventsSummary(
                totalSpent = 300.0,
                countByCategory = listOf(EventCategory.MAINTENANCE to 2, EventCategory.INSURANCE to 1),
                count = 3,
            ),
            tableHeader = listOf("Data", "Categoria", "Título"),
            tableRows = listOf(listOf("2026-01-05", "Manutenção", "Troca de correia")),
        )

        assertTrue(isValidPdf(bytes))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.export.data.pdf.PdfReportWriterTest"`
Expected: FAIL — compilation error, `PdfReportWriter` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.flowfuel.app.feature.export.data.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.flowfuel.app.feature.export.data.EventsSummary
import com.flowfuel.app.feature.export.data.RefuelsSummary
import java.io.ByteArrayOutputStream
import javax.inject.Inject

private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private const val LINE_HEIGHT = 16f

class PdfReportWriter @Inject constructor() {

    fun writeRefuelsReport(
        vehicleLabel: String,
        periodLabel: String,
        summary: RefuelsSummary,
        energyUnit: String,
        consumptionUnit: String,
        tableHeader: List<String>,
        tableRows: List<List<String>>,
    ): ByteArray {
        val summaryLines = listOf(
            "Total gasto: R$ ${summary.totalSpent.formatDecimal()}",
            "Total abastecido: ${summary.totalEnergy.formatDecimal()} $energyUnit",
            "Consumo médio: ${summary.averageConsumption?.formatDecimal() ?: "-"} $consumptionUnit",
            "Abastecimentos: ${summary.count}",
        )
        return write("Relatório de Abastecimentos", vehicleLabel, periodLabel, summaryLines, tableHeader, tableRows)
    }

    fun writeEventsReport(
        vehicleLabel: String,
        periodLabel: String,
        summary: EventsSummary,
        tableHeader: List<String>,
        tableRows: List<List<String>>,
    ): ByteArray {
        val summaryLines = buildList {
            add("Total gasto: R$ ${summary.totalSpent.formatDecimal()}")
            add("Eventos: ${summary.count}")
            summary.countByCategory.forEach { (category, count) -> add("${category.label}: $count") }
        }
        return write("Relatório de Eventos", vehicleLabel, periodLabel, summaryLines, tableHeader, tableRows)
    }

    private fun write(
        title: String,
        vehicleLabel: String,
        periodLabel: String,
        summaryLines: List<String>,
        tableHeader: List<String>,
        tableRows: List<List<String>>,
    ): ByteArray {
        val document = PdfDocument()
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
        val labelPaint = Paint().apply { textSize = 12f; color = Color.DKGRAY }
        val headerPaint = Paint().apply { textSize = 10f; isFakeBoldText = true; color = Color.BLACK }
        val cellPaint = Paint().apply { textSize = 9f; color = Color.BLACK }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        val colWidth = contentWidth / tableHeader.size

        var page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create()
        )
        var canvas = page.canvas
        var y = MARGIN

        fun drawTableHeaderRow() {
            tableHeader.forEachIndexed { i, text -> canvas.drawText(text, MARGIN + i * colWidth, y, headerPaint) }
            y += LINE_HEIGHT
            canvas.drawLine(MARGIN, y - 4f, MARGIN + contentWidth, y - 4f, linePaint)
        }

        fun newPage() {
            document.finishPage(page)
            page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create()
            )
            canvas = page.canvas
            y = MARGIN
        }

        canvas.drawText(title, MARGIN, y, titlePaint); y += 24f
        canvas.drawText(vehicleLabel, MARGIN, y, labelPaint); y += LINE_HEIGHT
        canvas.drawText(periodLabel, MARGIN, y, labelPaint); y += LINE_HEIGHT + 8f
        summaryLines.forEach { line -> canvas.drawText(line, MARGIN, y, labelPaint); y += LINE_HEIGHT }
        y += 8f
        drawTableHeaderRow()

        tableRows.forEach { row ->
            if (y > PAGE_HEIGHT - MARGIN) {
                newPage()
                drawTableHeaderRow()
            }
            row.forEachIndexed { i, text -> canvas.drawText(text, MARGIN + i * colWidth, y, cellPaint) }
            y += LINE_HEIGHT
        }

        document.finishPage(page)

        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    private fun Double.formatDecimal() = "%.2f".format(this).replace('.', ',')
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.export.data.pdf.PdfReportWriterTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/export/data/pdf/PdfReportWriter.kt app/src/test/java/com/flowfuel/app/feature/export/data/pdf/PdfReportWriterTest.kt
git commit -m "feat(export): gerar relatório PDF de abastecimentos e eventos com PdfDocument"
```

---

### Task 5: ExportFormat.PDF + wiring no ExportRepositoryImpl

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/export/domain/ExportRepository.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/export/data/ExportRepositoryImpl.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/export/data/ExportRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `buildCsvBytes` (Task 1), `buildRefuelsSummary`/`buildEventsSummary` (Task 2), `vehicleLabel`/`periodLabel`/`energyUnit`/`consumptionUnit` (Task 3), `PdfReportWriter` (Task 4), `VehicleRepository.getVehicleById(id: Int): AppResult<Vehicle>` (já existe em `com.flowfuel.app.feature.vehicle.domain.VehicleRepository`).
- Produces: `ExportFormat.PDF` — consumido pela UI (Task 6).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.flowfuel.app.feature.export.data

import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.export.data.pdf.PdfReportWriter
import com.flowfuel.app.feature.export.domain.ExportFormat
import com.flowfuel.app.feature.history.domain.HistoryRepository
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.model.RefuelPage
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExportRepositoryImplTest {

    private val historyRepository: HistoryRepository = mockk()
    private val eventRepository: VehicleEventRepository = mockk()
    private val vehicleRepository: VehicleRepository = mockk()
    private val pdfReportWriter: PdfReportWriter = mockk()
    private lateinit var repository: ExportRepositoryImpl

    @Before
    fun setUp() {
        repository = ExportRepositoryImpl(
            historyRepository = historyRepository,
            eventRepository = eventRepository,
            vehicleRepository = vehicleRepository,
            pdfReportWriter = pdfReportWriter,
            context = ApplicationProvider.getApplicationContext(),
        )
    }

    private fun vehicle() = Vehicle(
        id = 1, brand = "Toyota", model = "Corolla", manufactureYear = 2022, modelYear = 2022,
        licensePlate = "ABC1D23", color = "Prata", type = VehicleType.Car, energyType = EnergyType.Combustion,
        fuelType = FuelType.Gasoline, odometerKm = 10000, tankCapacityL = 50.0, batteryCapacityKwh = null,
        isActive = true,
    )

    private fun refuelItem() = RefuelItem(
        id = 1, date = "2026-01-05", energyAmount = 40.0, pricePerUnit = 5.5, totalPrice = 220.0,
        fullTank = true, refuelType = "FUEL", odometer = 10500.0, trip = 400.0, consumption = 10.0,
    )

    @Test
    fun `exportRefuels CSV does not fetch vehicle`() = runTest {
        coEvery { historyRepository.getRefuelHistory(1, 0, 100, null, null) } returns
            AppResult.Success(RefuelPage(items = listOf(refuelItem()), hasMore = false, currentPage = 0))

        val result = repository.exportRefuels(vehicleId = 1, format = ExportFormat.CSV)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 0) { vehicleRepository.getVehicleById(any()) }
    }

    @Test
    fun `exportRefuels PDF fetches vehicle and calls pdf writer`() = runTest {
        coEvery { historyRepository.getRefuelHistory(1, 0, 100, null, null) } returns
            AppResult.Success(RefuelPage(items = listOf(refuelItem()), hasMore = false, currentPage = 0))
        coEvery { vehicleRepository.getVehicleById(1) } returns AppResult.Success(vehicle())
        every {
            pdfReportWriter.writeRefuelsReport(
                vehicleLabel = any(), periodLabel = any(), summary = any(),
                energyUnit = any(), consumptionUnit = any(), tableHeader = any(), tableRows = any(),
            )
        } returns ByteArray(0)

        val result = repository.exportRefuels(vehicleId = 1, format = ExportFormat.PDF)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { vehicleRepository.getVehicleById(1) }
    }

    @Test
    fun `exportRefuels PDF propagates vehicle fetch failure`() = runTest {
        coEvery { historyRepository.getRefuelHistory(1, 0, 100, null, null) } returns
            AppResult.Success(RefuelPage(items = emptyList(), hasMore = false, currentPage = 0))
        coEvery { vehicleRepository.getVehicleById(1) } returns AppResult.Failure(AppError.Network)

        val result = repository.exportRefuels(vehicleId = 1, format = ExportFormat.PDF)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Network)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.export.data.ExportRepositoryImplTest"`
Expected: FAIL — compilation error (`ExportFormat.PDF` unresolved, `ExportRepositoryImpl` construtor não bate com os novos parâmetros).

- [ ] **Step 3: Write minimal implementation**

Modify `app/src/main/java/com/flowfuel/app/feature/export/domain/ExportRepository.kt` — troque o enum:

```kotlin
enum class ExportFormat(val value: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    PDF("pdf", "application/pdf"),
}
```

Replace the full contents of `app/src/main/java/com/flowfuel/app/feature/export/data/ExportRepositoryImpl.kt`:

```kotlin
package com.flowfuel.app.feature.export.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.export.data.pdf.PdfReportWriter
import com.flowfuel.app.feature.export.domain.ExportFormat
import com.flowfuel.app.feature.export.domain.ExportRepository
import com.flowfuel.app.feature.history.domain.HistoryRepository
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val REFUELS_TABLE_HEADER = listOf(
    "Data", "Tipo", "Quantidade", "Preço/unidade", "Total (R$)",
    "Tanque cheio", "Odômetro (km)", "Km percorridos", "Consumo",
)

private val EVENTS_TABLE_HEADER = listOf(
    "Data", "Categoria", "Título", "Descrição", "Valor (R$)", "Odômetro (km)", "Notas",
)

@Singleton
class ExportRepositoryImpl @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val eventRepository: VehicleEventRepository,
    private val vehicleRepository: VehicleRepository,
    private val pdfReportWriter: PdfReportWriter,
    @ApplicationContext private val context: Context,
) : ExportRepository {

    override suspend fun exportRefuels(
        vehicleId: Int,
        format: ExportFormat,
        startDate: String?,
        endDate: String?,
    ): AppResult<Uri> {
        val fetchResult = fetchAllRefuels(vehicleId, startDate, endDate)
        if (fetchResult is AppResult.Failure) return fetchResult
        val items = (fetchResult as AppResult.Success).value

        return when (format) {
            ExportFormat.CSV -> runCatching {
                val bytes = buildCsvBytes(REFUELS_TABLE_HEADER, items.map(::refuelsTableRow))
                AppResult.Success(saveFile(bytes, "flowfuel-abastecimentos.csv"))
            }.getOrElse { e -> Timber.e(e, "export save failure"); AppResult.Failure(AppError.Unknown(e)) }

            ExportFormat.PDF -> {
                val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
                if (vehicleResult is AppResult.Failure) return vehicleResult
                val vehicle = (vehicleResult as AppResult.Success).value
                runCatching {
                    val bytes = pdfReportWriter.writeRefuelsReport(
                        vehicleLabel = vehicleLabel(vehicle),
                        periodLabel = periodLabel(startDate, endDate),
                        summary = buildRefuelsSummary(items),
                        energyUnit = energyUnit(vehicle),
                        consumptionUnit = consumptionUnit(vehicle),
                        tableHeader = REFUELS_TABLE_HEADER,
                        tableRows = items.map(::refuelsTableRow),
                    )
                    AppResult.Success(saveFile(bytes, "flowfuel-abastecimentos.pdf"))
                }.getOrElse { e -> Timber.e(e, "export save failure"); AppResult.Failure(AppError.Unknown(e)) }
            }
        }
    }

    override suspend fun exportEvents(
        vehicleId: Int,
        format: ExportFormat,
        type: String?,
        startDate: String?,
        endDate: String?,
    ): AppResult<Uri> {
        val category = type?.let { t -> EventCategory.entries.find { it.apiValue == t } }
        val fetchResult = fetchAllEvents(vehicleId, category, startDate, endDate)
        if (fetchResult is AppResult.Failure) return fetchResult
        val items = (fetchResult as AppResult.Success).value

        return when (format) {
            ExportFormat.CSV -> runCatching {
                val bytes = buildCsvBytes(EVENTS_TABLE_HEADER, items.map(::eventsTableRow))
                AppResult.Success(saveFile(bytes, "flowfuel-eventos.csv"))
            }.getOrElse { e -> Timber.e(e, "export save failure"); AppResult.Failure(AppError.Unknown(e)) }

            ExportFormat.PDF -> {
                val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
                if (vehicleResult is AppResult.Failure) return vehicleResult
                val vehicle = (vehicleResult as AppResult.Success).value
                runCatching {
                    val bytes = pdfReportWriter.writeEventsReport(
                        vehicleLabel = vehicleLabel(vehicle),
                        periodLabel = periodLabel(startDate, endDate),
                        summary = buildEventsSummary(items),
                        tableHeader = EVENTS_TABLE_HEADER,
                        tableRows = items.map(::eventsTableRow),
                    )
                    AppResult.Success(saveFile(bytes, "flowfuel-eventos.pdf"))
                }.getOrElse { e -> Timber.e(e, "export save failure"); AppResult.Failure(AppError.Unknown(e)) }
            }
        }
    }

    private suspend fun fetchAllRefuels(
        vehicleId: Int,
        startDate: String?,
        endDate: String?,
    ): AppResult<List<RefuelItem>> {
        val start = startDate?.let { LocalDate.parse(it) }
        val end = endDate?.let { LocalDate.parse(it) }
        val items = mutableListOf<RefuelItem>()
        var page = 0
        while (true) {
            when (val r = historyRepository.getRefuelHistory(vehicleId, page, 100, start, end)) {
                is AppResult.Success -> {
                    items.addAll(r.value.items)
                    if (!r.value.hasMore) return AppResult.Success(items)
                    page++
                }
                is AppResult.Failure -> return r
            }
        }
    }

    private suspend fun fetchAllEvents(
        vehicleId: Int,
        category: EventCategory?,
        startDate: String?,
        endDate: String?,
    ): AppResult<List<VehicleEvent>> {
        val items = mutableListOf<VehicleEvent>()
        var page = 0
        while (true) {
            when (val r = eventRepository.getEventsByVehicle(vehicleId, page, category, startDate, endDate)) {
                is AppResult.Success -> {
                    items.addAll(r.value.items)
                    if (!r.value.hasMore) return AppResult.Success(items)
                    page++
                }
                is AppResult.Failure -> return r
            }
        }
    }

    private fun refuelsTableRow(item: RefuelItem): List<String> = listOf(
        item.date,
        item.refuelType ?: "FUEL",
        item.energyAmount.csvDecimal(),
        item.pricePerUnit.csvDecimal(),
        item.totalPrice.csvDecimal(),
        if (item.fullTank) "Sim" else "Não",
        item.odometer?.csvDecimal() ?: "",
        item.trip?.csvDecimal() ?: "",
        item.consumption?.csvDecimal() ?: "",
    )

    private fun eventsTableRow(item: VehicleEvent): List<String> = listOf(
        item.eventDate,
        item.category.label,
        item.title,
        item.description ?: "",
        item.amount?.csvDecimal() ?: "",
        item.odometerKm?.toString() ?: "",
        item.notes ?: "",
    )

    private fun saveFile(bytes: ByteArray, filename: String): Uri {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val file = File(dir, filename)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun Double.csvDecimal() = "%.2f".format(this).replace('.', ',')
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.export.data.ExportRepositoryImplTest"`
Expected: PASS (3 tests)

Also re-run Tasks 1–4 tests to confirm nothing regressed:

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.export.*"`
Expected: PASS (all export tests green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/export/domain/ExportRepository.kt app/src/main/java/com/flowfuel/app/feature/export/data/ExportRepositoryImpl.kt app/src/test/java/com/flowfuel/app/feature/export/data/ExportRepositoryImplTest.kt
git commit -m "feat(export): rotear exportação por formato (CSV/PDF) e aplicar BOM no CSV"
```

---

### Task 6: UI — seletor de formato (CSV/PDF) no ExportBottomSheet

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/export/presentation/ExportBottomSheet.kt:1-51,121-130` (imports e bloco de conteúdo)
- Modify: `app/src/main/res/values/strings.xml:114-115` (novas strings de formato)

**Interfaces:**
- Consumes: `ExportUiState.selectedFormat: ExportFormat` e `ExportViewModel.onFormatChange(format: ExportFormat)` (já existem em `ExportViewModel.kt`, sem mudanças nesta task).

- [ ] **Step 1: Add format chip strings**

Em `app/src/main/res/values/strings.xml`, logo abaixo de `export_format_label` (linha 115), adicione:

```xml
    <string name="export_format_label">Formato</string>
    <string name="export_format_csv">CSV</string>
    <string name="export_format_pdf">PDF</string>
```

- [ ] **Step 2: Add ExportFormat import to ExportBottomSheet.kt**

Em `app/src/main/java/com/flowfuel/app/feature/export/presentation/ExportBottomSheet.kt`, adicione o import junto aos demais do pacote `export`:

```kotlin
import com.flowfuel.app.feature.export.domain.ExportFormat
```

- [ ] **Step 3: Insert format chip row in the bottom sheet content**

No mesmo arquivo, dentro de `FFBottomSheet { Box { Column { ... } } }`, logo após o título e antes do comentário `// Date range` (imediatamente depois de `Spacer(Modifier.height(FFTheme.spacing.md))` que segue o `Text(text = stringResource(R.string.export_title), ...)`), insira:

```kotlin
                Text(
                    text = stringResource(R.string.export_format_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(FFTheme.spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                    FFChip(
                        label = stringResource(R.string.export_format_csv),
                        selected = state.selectedFormat == ExportFormat.CSV,
                        onClick = { viewModel.onFormatChange(ExportFormat.CSV) },
                    )
                    FFChip(
                        label = stringResource(R.string.export_format_pdf),
                        selected = state.selectedFormat == ExportFormat.PDF,
                        onClick = { viewModel.onFormatChange(ExportFormat.PDF) },
                    )
                }
                Spacer(Modifier.height(FFTheme.spacing.md))

                // Date range
```

(O `Spacer(Modifier.height(FFTheme.spacing.md))` originalmente entre o título e o comentário `// Date range` é substituído por este bloco, que já termina com o mesmo espaçamento.)

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/export/presentation/ExportBottomSheet.kt app/src/main/res/values/strings.xml
git commit -m "feat(export): seletor de formato CSV/PDF na tela de exportação"
```

---

### Task 7: Verificação final

**Files:** nenhum (apenas execução)

- [ ] **Step 1: Run the full export test suite**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.export.*"`
Expected: BUILD SUCCESSFUL, todos os testes das Tasks 1–5 passando.

- [ ] **Step 2: Run the full unit test suite to check for regressions**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL (nenhuma regressão em outros módulos).

- [ ] **Step 3: Assemble debug APK to confirm the app builds end-to-end**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual check (não automatizável)**

Instalar o APK debug num device/emulador, abrir Histórico ou Eventos → Exportar, escolher CSV e confirmar que "Preço", "Não", "Título" aparecem corretamente ao abrir o arquivo no Excel/Planilhas. Escolher PDF e confirmar que o relatório abre com cabeçalho do veículo, resumo e tabela.
