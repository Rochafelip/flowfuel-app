# Design: Toggle Odômetro / Percurso no Formulário de Abastecimento

**Data:** 2026-06-28  
**Status:** Aprovado

## Contexto

O formulário de abastecimento (`QuickRefuelBottomSheet`) exige hoje a leitura absoluta do odômetro. Muitos motoristas lembram com mais facilidade quantos km rodaram desde o último abastecimento (percurso) do que o valor exato do odômetro. Esta feature adiciona um toggle para que o usuário escolha qual informação quer informar.

## Objetivo

Permitir registrar um abastecimento informando **km percorridos desde o último abastecimento** (percurso) como alternativa ao odômetro absoluto. O backend continua recebendo o odômetro absoluto; o cálculo é feito no app.

## Escopo

Apenas o formulário de abastecimento rápido (`QuickRefuelBottomSheet` + `HomeViewModel` + `HomeUiState`). Nenhuma mudança na camada de dados, API ou outras telas.

---

## Design

### 1. Estado — `HomeUiState.kt`

Adicionar enum e três campos a `RefuelFormState`:

```kotlin
enum class OdometerInputMode { TRIP, ODOMETER }

data class RefuelFormState(
    // ... campos existentes ...
    val odometerInputMode: OdometerInputMode = OdometerInputMode.TRIP, // padrão
    val tripKm: String = "",           // dígitos + vírgula/ponto
    val tripKmError: Boolean = false,
)
```

Atualizar `canSubmit`:

```kotlin
fun canSubmit(isHybrid: Boolean): Boolean =
    when (odometerInputMode) {
        OdometerInputMode.TRIP ->
            tripKm.isNotBlank()
                && tripKm.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true
                && totalPriceCents > 0
                && liters.isNotBlank()
                && (!isHybrid || refuelType != null)
        OdometerInputMode.ODOMETER ->
            odometer.isNotBlank()
                && totalPriceCents > 0
                && liters.isNotBlank()
                && (!isHybrid || refuelType != null)
    }
```

### 2. ViewModel — `HomeViewModel.kt`

**Novos handlers:**

```kotlin
fun onOdometerInputModeChange(mode: OdometerInputMode) = _state.update {
    it.copy(refuelForm = it.refuelForm.copy(
        odometerInputMode = mode,
        odometer          = "",
        tripKm            = "",
        odometerError     = false,
        tripKmError       = false,
        serverErrors      = null,
    ))
}

fun onTripKmChange(v: String) = _state.update {
    it.copy(refuelForm = it.refuelForm.copy(
        tripKm       = v.filter { c -> c.isDigit() || c == ',' || c == '.' },
        tripKmError  = false,
        serverErrors = null,
    ))
}
```

**Atualizar `submitRefuel()`:**

```kotlin
val isTripMode = form.odometerInputMode == OdometerInputMode.TRIP

val tripInvalid     = isTripMode && (
    form.tripKm.isBlank() ||
    form.tripKm.replace(',', '.').toDoubleOrNull()?.let { it <= 0.0 } != false
)
val odometerInvalid = !isTripMode && form.odometer.isBlank()

// Calcular odômetro absoluto conforme o modo
val odometer = if (isTripMode)
    vehicle.currentKm.toDouble() + form.tripKm.replace(',', '.').toDouble()
else
    form.odometerDouble
```

Substituir `form.odometerDouble` por `odometer` na chamada a `CreateRefuelRequest`.

### 3. UI — `QuickRefuelBottomSheet.kt`

**Novos parâmetros:**

```kotlin
odometerInputMode: OdometerInputMode,
tripKm: String,
tripKmError: Boolean,
onOdometerInputModeChange: (OdometerInputMode) -> Unit,
onTripKmChange: (String) -> Unit,
```

**Substituir o campo de odômetro pelo bloco toggle + campo dinâmico:**

```
[Percurso ✓]  [Odômetro]     ← FilterChips (mesmo padrão do seletor de tipo híbrido)

┌──────────────────────────┐
│ Km percorridos           │  ← modo TRIP (padrão)
│ 310,6                    │
└──────────────────────────┘
helper: "Use vírgula ou ponto como separador decimal"
```

```
[Percurso]  [Odômetro ✓]

┌──────────────────────────┐
│ Odômetro (km)            │  ← modo ODOMETER (existente)
│ 67.270,0                 │
└──────────────────────────┘
```

- Modo TRIP: `FFNumberField` com `FFNumberKind.Decimal`, label "Km percorridos", helper de separador decimal
- Modo ODOMETER: `FFNumberField` com `FFNumberKind.Odometer`, label "Odômetro (km)" — comportamento atual
- Troca de modo limpa o campo e zera os erros
- Chips ficam acima do campo, sem divisor extra

**Wiring em `HomeScreen`:** passar os novos campos e handlers do `state` para o bottom sheet.

---

## Fluxo de dados

```
Usuário digita 310,6 km (modo TRIP)
    ↓
onTripKmChange("310,6") → RefuelFormState.tripKm = "310,6"
    ↓
submitRefuel()
    odometer = vehicle.currentKm (67.270) + 310,6 = 67.580,6
    ↓
CreateRefuelRequest(odometer = 67580.6, ...)
    ↓
API recebe odômetro absoluto — comportamento inalterado
```

## Validação

| Modo | Campo obrigatório | Erro exibido |
|------|------------------|--------------|
| TRIP | `tripKm` não vazio e > 0 | "Informe os km percorridos" |
| ODOMETER | `odometer` não vazio | "Informe a leitura do odômetro" |

Erros de servidor (ex: odômetro retroativo) continuam sendo exibidos normalmente — o odômetro calculado pode ser inferior ao último registrado se `currentKm` estiver desatualizado no backend.

## Arquivos alterados

| Arquivo | Mudança |
|---------|---------|
| `HomeUiState.kt` | Enum `OdometerInputMode` + campos `odometerInputMode`, `tripKm`, `tripKmError` em `RefuelFormState`; atualizar `canSubmit` |
| `HomeViewModel.kt` | Handlers `onOdometerInputModeChange`, `onTripKmChange`; atualizar `submitRefuel` |
| `QuickRefuelBottomSheet.kt` | Toggle chips + campo dinâmico |
| `HomeScreen.kt` | Passar novos params/handlers ao bottom sheet |

## Fora do escopo

- Edição de abastecimento (`EditRefuelScreen`) — mantém odômetro absoluto
- Histórico e detalhes — sem mudança
- Backend — sem mudança
