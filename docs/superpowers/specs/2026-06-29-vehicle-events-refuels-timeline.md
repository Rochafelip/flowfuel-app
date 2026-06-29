# Spec: Abastecimentos na Timeline de Eventos do Veículo

**Data:** 2026-06-29  
**Status:** Aprovado

## Contexto

A tela de Eventos (`VehicleEventsScreen`) exibe apenas eventos manuais (`VehicleEvent`) criados pelo usuário, buscados em `VehicleEventRepository`. Os abastecimentos reais ficam em um módulo separado (`feature/history`, `HistoryRepository`) e não aparecem na timeline de eventos. O filtro "Combustível" existe mas não inclui abastecimentos registrados.

O objetivo é unificar as duas fontes em uma timeline ordenada por data, mostrando abastecimentos nas categorias "Todas" e "Combustível".

## Modelo de Domínio

### `VehicleTimelineItem` — novo sealed interface

Arquivo: `feature/vehicleevent/domain/model/VehicleTimelineItem.kt`

> `RefuelEntry` referencia `com.flowfuel.app.feature.history.domain.model.RefuelItem` — dependência cross-feature aceita, pois o projeto usa módulo único de app com pacotes de feature.

```kotlin
sealed interface VehicleTimelineItem {
    val sortDate: String  // ISO-8601, ordenação descendente

    data class EventEntry(val event: VehicleEvent) : VehicleTimelineItem {
        override val sortDate get() = event.eventDate
    }

    data class RefuelEntry(val refuel: RefuelItem) : VehicleTimelineItem {
        override val sortDate get() = refuel.date
    }
}
```

### Regras de visibilidade dos abastecimentos

| Filtro ativo | Eventos | Abastecimentos |
|---|---|---|
| Todas (null) | todos | sim |
| Combustível (`FUEL`) | categoria FUEL | sim |
| Qualquer outro | categoria X | não |

## Estratégia de Carregamento

- Eventos: paginados via `GetVehicleEventsPageUseCase` (comportamento atual preservado).
- Abastecimentos: carregados em **batch único** via `HistoryRepository.getRefuelHistory(vehicleId, page=0, size=200)` — sem paginação adicional, adequado ao volume típico de usuário.
- Filtro de data sobre abastecimentos: aplicado **client-side** após o carregamento (a API de refuels exige ambos `startDate`+`endDate`; o `EventDateFilter.toDateRange()` já resolve o par).
- Merge: `accumulatedEvents + accumulatedRefuels` → filtro de data → `sortedByDescending { it.sortDate }`.

## Camada de Apresentação

### `VehicleEventsUiState`

```kotlin
// Antes
Success(val events: List<VehicleEvent>)

// Depois
Success(val items: List<VehicleTimelineItem>)
```

### `VehicleEventsViewModel`

Novas responsabilidades:
- Depende de `HistoryRepository` (injetado via Hilt).
- `accumulatedRefuels: List<RefuelItem>` — lista em memória, recarregada a cada `load()`/`refresh()` quando aplicável.
- `shouldIncludeRefuels(): Boolean` — `true` se `selectedCategory == null || selectedCategory == EventCategory.FUEL`.
- `loadRefuels()` — busca batch de abastecimentos; em caso de erro `accumulatedRefuels` fica vazio e a timeline exibe apenas eventos (sem mensagem de erro adicional).
- Ao mudar de categoria via `onCategorySelected()`: limpa `accumulatedRefuels`; recarrega abastecimentos apenas se `shouldIncludeRefuels()` for `true` para a nova categoria.
- `buildTimeline()` — mescla `accumulatedEvents + accumulatedRefuels`, aplica filtro de data client-side nos refuels, ordena por `sortDate` descendente.
- `onRefuelClick(refuelId: Int)` — emite `VehicleEventsEffect.NavigateToRefuelDetails(refuelId)`.

### `VehicleEventsEffect` — novo subtipo

```kotlin
data class NavigateToRefuelDetails(val refuelId: Int) : VehicleEventsEffect
```

### `VehicleEventsScreen`

Novo parâmetro:
```kotlin
onNavigateToRefuelDetails: (refuelId: Int) -> Unit,
```

`LazyColumn` diferencia os dois tipos via key composta:
```kotlin
key = { item ->
    when (item) {
        is EventEntry  -> "event-${item.event.id}"
        is RefuelEntry -> "refuel-${item.refuel.id}"
    }
}
```

### `RefuelTimelineCard` — novo componente

Arquivo: `feature/vehicleevent/presentation/components/RefuelTimelineCard.kt`

Campos exibidos:
- Chip: "Abastecimento" se `refuelType == null || "FUEL"`; "Carga" se `refuelType == "ELECTRIC"` — ícone `LocalGasStation`, cor `primaryContainer`.
- Valor total em BRL (destaque).
- Litros/kWh + preço por unidade (`energyAmount` + `pricePerUnit`).
- Data formatada (`refuel.date`) + odômetro em km (se `refuel.odometer != null`) no lado direito.

Card clicável → `onRefuelClick(refuel.id)`.

## Navegação

Em `FlowFuelNavHost`, composable `VEHICLE_EVENTS`:

```kotlin
onNavigateToRefuelDetails = { refuelId ->
    navController.navigate(Destinations.refuelDetails(refuelId))
},
```

A rota `Destinations.REFUEL_DETAILS` já existe no nav graph.

## O que NÃO muda

- `VehicleEventRepository`, `GetVehicleEventsPageUseCase`, paginação de eventos.
- `HistoryRepository` e `RefuelItem` — sem alterações.
- `EventCategoryFilterRow` — sem alterações (o chip "Combustível" já existe).
- `VehicleEventCard` — sem alterações.
- Fluxo de criação/edição/exclusão de eventos.

## Fora do Escopo

- Criar/editar abastecimentos a partir da tela de Eventos.
- Paginação de abastecimentos na timeline (batch de 200 é suficiente).
- Endpoint de backend unificado.
