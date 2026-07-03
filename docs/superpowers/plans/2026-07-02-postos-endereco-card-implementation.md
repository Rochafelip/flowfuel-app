# Endereço (rua e número) no card de Postos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Exibir rua e número (quando disponíveis) no `StationCard` da tela "Postos", como uma segunda linha de texto abaixo do nome, sem alterar a altura do card quando o endereço vem `null`.

**Architecture:** Dois campos novos e opcionais (`street`, `houseNumber`) atravessam as três camadas existentes sem transformação — `StationResponseDto` (rede) → `Station` (domínio) → `StationCard` (UI). Uma função pura `formatAddress()` decide o texto final (ou `null` se não há nada útil a mostrar); o card renderiza a linha condicionalmente, preservando o layout compacto atual quando `null`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), kotlinx.serialization, JUnit — nenhuma dependência nova.

## Global Constraints

- Campos opcionais com default `null` em todas as camadas (`StationResponseDto`, `Station`) — contratos antigos (payloads/mocks sem os campos) continuam desserializando e compilando sem quebrar. Isso inclui os dois helpers `station(...)` já existentes em `StationsViewModelTest.kt` e `NearbyStationsPrefetcherTest.kt`, que constroem `Station(...)` sem os novos campos — eles não devem precisar de nenhuma alteração.
- `houseNumber` fica sempre `null` para `StationType.Electric` — isso é uma limitação da fonte de dado (Open Charge Map), não algo que o app precisa validar ou tratar defensivamente no código.
- Fora de escopo (não implementar nesta rodada): UI de edição/correção de endereço pelo usuário, geocodificação reversa client-side, qualquer mudança em filtro/badge/rating/botão de rota, exibir endereço fora do `StationCard` da tela Postos.
- A nova linha de texto usa `MaterialTheme.typography.bodySmall` + `MaterialTheme.colorScheme.onSurfaceVariant`, `maxLines = 1` + `TextOverflow.Ellipsis` — mesmo padrão visual já usado para texto secundário no card.
- Correção ao spec original: o spec referencia `FFTheme.spacing.xxs`, que **não existe** em `FFSpacing` (`app/src/main/java/com/flowfuel/app/core/designsystem/theme/Spacing.kt` só define `xs = 4.dp` como menor token). Usar `FFTheme.spacing.xs` para o espaçamento antes da linha de endereço — é o mesmo token já usado para o espaçamento entre as outras linhas do card, então o resultado visual permanece consistente.
- Não usar `remember()` ao redor da chamada de `formatAddress()` — é uma função pura e barata (comparação de strings), e nenhuma outra formatação no card (`formatDistance`, `formatRating`) usa `remember` hoje. Adicionar aqui só quebraria a consistência do arquivo sem ganho.

---

### Task 1: Propagar `street`/`houseNumber` do DTO até o domínio

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/data/remote/StationApi.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/domain/model/Station.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/data/StationRepositoryImpl.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/data/StationRepositoryImplTest.kt`

**Interfaces:**
- Produces: `StationResponseDto.street: String? = null`, `StationResponseDto.houseNumber: String? = null` (package `com.flowfuel.app.feature.station.data.remote`); `Station.street: String? = null`, `Station.houseNumber: String? = null` (package `com.flowfuel.app.feature.station.domain.model`).

- [ ] **Step 1: Write the failing tests**

Modify `app/src/test/java/com/flowfuel/app/feature/station/data/StationRepositoryImplTest.kt` — add `import org.junit.Assert.assertNull` to the existing import block, then add these two tests at the end of the class, before the closing `}`:

```kotlin
    @Test
    fun `propagates street and houseNumber from dto to domain unchanged`() = runTest {
        coEvery { api.getNearbyStations(any(), any(), any()) } returns listOf(
            StationResponseDto(
                placeId = "a", name = "Shell Boa Viagem", type = "FUEL",
                distanceMeters = 420, rating = 4.6, latitude = -8.05, longitude = -34.91,
                street = "Avenida Alfredo Lisboa", houseNumber = "173",
            ),
        )

        val result = repository.getNearbyStations(location, radiusMeters = 5000)

        assertTrue(result is AppResult.Success)
        val station = (result as AppResult.Success).value.single()
        assertEquals("Avenida Alfredo Lisboa", station.street)
        assertEquals("173", station.houseNumber)
    }

    @Test
    fun `defaults street and houseNumber to null when the dto omits them`() = runTest {
        coEvery { api.getNearbyStations(any(), any(), any()) } returns listOf(
            StationResponseDto(
                placeId = "b", name = "Ipiranga", type = "FUEL",
                distanceMeters = 650, rating = null, latitude = -8.05, longitude = -34.90,
            ),
        )

        val result = repository.getNearbyStations(location, radiusMeters = 5000)

        assertTrue(result is AppResult.Success)
        val station = (result as AppResult.Success).value.single()
        assertNull(station.street)
        assertNull(station.houseNumber)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.data.StationRepositoryImplTest"`
Expected: FAIL to compile — `StationResponseDto(...)` doesn't accept `street`/`houseNumber` yet, and `Station.street`/`Station.houseNumber` don't exist.

- [ ] **Step 3: Implement**

Modify `app/src/main/java/com/flowfuel/app/feature/station/data/remote/StationApi.kt` — add the two fields to `StationResponseDto`:

```kotlin
@Serializable
data class StationResponseDto(
    val placeId: String,
    val name: String,
    /** "FUEL" ou "ELECTRIC" — ver StationRepositoryImpl.toDomain(). */
    val type: String,
    val distanceMeters: Int,
    val rating: Double? = null,
    val latitude: Double,
    val longitude: Double,
    val street: String? = null,
    val houseNumber: String? = null,
)
```

Modify `app/src/main/java/com/flowfuel/app/feature/station/domain/model/Station.kt` — add the two fields to `Station`:

```kotlin
package com.flowfuel.app.feature.station.domain.model

enum class StationType { Fuel, Electric }

data class Station(
    val placeId: String,
    val name: String,
    val type: StationType,
    val distanceMeters: Int,
    val rating: Double?,
    val latitude: Double,
    val longitude: Double,
    val street: String? = null,
    val houseNumber: String? = null,
)
```

Modify `app/src/main/java/com/flowfuel/app/feature/station/data/StationRepositoryImpl.kt` — propagate the two fields in `toDomain()`:

```kotlin
    private fun StationResponseDto.toDomain(): Station = Station(
        placeId = placeId,
        name = name,
        type = if (type.equals("ELECTRIC", ignoreCase = true)) StationType.Electric else StationType.Fuel,
        distanceMeters = distanceMeters,
        rating = rating,
        latitude = latitude,
        longitude = longitude,
        street = street,
        houseNumber = houseNumber,
    )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.data.StationRepositoryImplTest"`
Expected: PASS (all existing tests + 2 new ones)

- [ ] **Step 5: Run the station domain/presentation test suites to confirm the new default-null fields don't break existing call sites**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.*"`
Expected: PASS — `StationsViewModelTest` and `NearbyStationsPrefetcherTest` keep compiling unchanged because their `station(...)` helpers rely on the new fields' `null` defaults.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/data/remote/StationApi.kt app/src/main/java/com/flowfuel/app/feature/station/domain/model/Station.kt app/src/main/java/com/flowfuel/app/feature/station/data/StationRepositoryImpl.kt app/src/test/java/com/flowfuel/app/feature/station/data/StationRepositoryImplTest.kt
git commit -m "feat(postos): propagate street/houseNumber from API to domain"
```

---

### Task 2: `formatAddress()` — função pura de formatação

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt`
- Test: `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationCardTest.kt`

**Interfaces:**
- Consumes: nenhuma (função pura de strings).
- Produces: `internal fun formatAddress(street: String?, houseNumber: String?): String?` (package `com.flowfuel.app.feature.station.presentation.list`).

- [ ] **Step 1: Write the failing tests**

Modify `app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationCardTest.kt` — add `import org.junit.Assert.assertNull` to the existing import block, then add these tests at the end of the class, before the closing `}`:

```kotlin
    @Test
    fun `formatAddress combines street and house number with a comma`() {
        assertEquals("Avenida Alfredo Lisboa, 173", formatAddress("Avenida Alfredo Lisboa", "173"))
    }

    @Test
    fun `formatAddress returns only the street when house number is null`() {
        assertEquals("Avenida Conde da Boa Vista", formatAddress("Avenida Conde da Boa Vista", null))
    }

    @Test
    fun `formatAddress returns null when street is null even if house number is present`() {
        assertNull(formatAddress(null, "173"))
    }

    @Test
    fun `formatAddress returns null when both street and house number are null`() {
        assertNull(formatAddress(null, null))
    }

    @Test
    fun `formatAddress treats a blank street as absent`() {
        assertNull(formatAddress("", "173"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationCardTest"`
Expected: FAIL to compile — `Unresolved reference: formatAddress`.

- [ ] **Step 3: Implement**

Modify `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt` — add this function next to `formatDistance`/`formatRating`, at the end of the file:

```kotlin
internal fun formatAddress(street: String?, houseNumber: String?): String? = when {
    street.isNullOrBlank() -> null
    houseNumber.isNullOrBlank() -> street
    else -> "$street, $houseNumber"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.presentation.list.StationCardTest"`
Expected: PASS (all existing tests + 5 new ones)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt app/src/test/java/com/flowfuel/app/feature/station/presentation/list/StationCardTest.kt
git commit -m "feat(postos): add formatAddress for the station card's address line"
```

---

### Task 3: Renderizar a linha de endereço no `StationCard`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt`

**Interfaces:**
- Consumes: `Station.street`, `Station.houseNumber` (Task 1); `formatAddress(street: String?, houseNumber: String?): String?` (Task 2).
- Produces: nenhuma interface nova — mudança visual interna ao Composable `StationCard`.

**Note:** `StationCardTest.kt` é JUnit puro (sem Compose test rule) e nenhum outro teste no módulo `station` usa Compose testing hoje — consistente com esse padrão existente, esta task não introduz infraestrutura de teste de Compose nova. A verificação é compilação + checagem visual manual no emulador, igual ao processo já usado para o restante do redesign do `StationCard` (release v0.8.0).

- [ ] **Step 1: Inserir a linha de endereço condicional entre a Row de nome/distância e a Row de rating/rota**

Modify `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt` — no corpo de `StationCard`, substituir:

```kotlin
        Spacer(Modifier.height(FFTheme.spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (station.rating != null) {
```

por:

```kotlin
        val address = formatAddress(station.street, station.houseNumber)
        if (address != null) {
            Spacer(Modifier.height(FFTheme.spacing.xs))
            Text(
                text = address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(FFTheme.spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (station.rating != null) {
```

`TextOverflow` e `MaterialTheme` já estão importados neste arquivo (usados por `formatDistance`/o texto do nome) — nenhum import novo é necessário.

- [ ] **Step 2: Compilar**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Rodar toda a suíte de testes do módulo station para confirmar que não há regressão**

Run: `./gradlew testDebugUnitTest --tests "com.flowfuel.app.feature.station.*"`
Expected: `BUILD SUCCESSFUL` — nenhuma regressão nas suítes existentes (`StationRepositoryImplTest`, `StationCardTest`, `StationsViewModelTest`, `NearbyStationsPrefetcherTest`, `StationDistanceFilterRowTest`).

- [ ] **Step 4: Verificação visual manual no emulador**

Usar a skill `run-android-emulator` (ou equivalente) para instalar o app, abrir a tela "Postos" e confirmar visualmente:
- Postos com `street` preenchido (com ou sem `houseNumber`) mostram a linha de endereço abaixo do nome, truncada com reticências se muito longa.
- Postos sem `street` (comum em resultados reais do Overpass) mantêm a altura de card atual, sem espaço em branco extra.
- Estações elétricas (Open Charge Map) nunca mostram número, só rua (quando disponível) — comportamento esperado da fonte, não um bug.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationCard.kt
git commit -m "feat(postos): show street/house number line in the station card"
```

---

## Self-Review

### Spec coverage

| Requisito do spec | Task |
|---|---|
| `street`/`houseNumber` opcionais no `StationResponseDto` | Task 1 |
| `street`/`houseNumber` opcionais no domínio `Station` | Task 1 |
| Propagação 1:1 em `StationRepositoryImpl.toDomain()` | Task 1 |
| `formatAddress()` com as 5 regras de formatação | Task 2 |
| Linha de endereço no card, renderização condicional, sem alterar altura quando `null` | Task 3 |
| `bodySmall` + `onSurfaceVariant`, `maxLines = 1` + `Ellipsis` | Task 3 |
| Testes de `formatAddress` (5 casos do spec) | Task 2 |
| Teste de `toDomain()` propagando incluindo caso `null` | Task 1 |
| `houseNumber` sempre `null` para `Electric` | Coberto naturalmente — `formatAddress` já trata `houseNumber == null` corretamente; nenhum código extra necessário (documentado em Global Constraints) |
| Fora de escopo (edição, geocoding reverso, outros lugares do app) | Nenhuma task toca nisso — confirmado |

### Placeholder scan
Nenhum "TBD"/"implementar depois" — todo código é completo em cada step. A ausência de teste de Compose na Task 3 é uma decisão explícita (documentada, com verificação alternativa concreta: compilação + checagem manual), não um placeholder.

### Type consistency
`formatAddress(street: String?, houseNumber: String?): String?` é usado de forma idêntica em Task 2 (definição) e Task 3 (chamada). `Station.street`/`Station.houseNumber` (Task 1) são os únicos consumidos em Task 3. Sem divergência de nomes/tipos entre tasks.
