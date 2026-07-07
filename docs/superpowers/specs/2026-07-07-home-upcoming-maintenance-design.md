# Design: Lembretes de manutenção (Próximos eventos)

## Contexto

O `docs/FlowFuel_Home_Spec_Prompt.md` (e uma spec genérica derivada dele,
recebida fora do repositório) descreve uma tela Home com uma seção
"Próximos eventos": três cartões — troca de óleo, rodízio de pneus e
licenciamento — cada um com uma contagem regressiva ("Em 320 km", "Vence em
18 dias").

A `HomeScreen` atual (reformulada em
[2026-07-06-home-dashboard-design.md](2026-07-06-home-dashboard-design.md))
já implementa `VehicleHeader`, `FinancialSummaryCard`, `IndicatorsGrid`,
`InsightCard`, `LastRefuelCard` e `RecentActivityCard`. Essa seção de
"próximos eventos" foi explicitamente deixada fora do escopo daquele design
("'Próximos eventos' como agendamento/lembrete real — nesta versão o card
mostra atividade recente"). Este documento cobre exclusivamente essa lacuna.

As outras duas peças ausentes da spec recebida — `TopBar` com menu
hambúrguer/gaveta e FAB encaixado na bottom bar — **não fazem parte deste
design** e ficam para specs futuras e independentes (decisão explícita:
cada peça tem escopo e complexidade próprios).

## Objetivo

Mostrar três lembretes de manutenção (troca de óleo, rodízio de pneus,
licenciamento) na Home, calculados o máximo possível a partir de dados já
existentes no app, sem exigir nenhuma mudança no backend.

## Escopo

### Dentro do MVP

- Nova seção `UpcomingEventsSection` na `HomeScreen`, logo depois do bloco
  `LastRefuelCard`/`RecentActivityCard` (ou seja, o último item antes do
  espaçador do FAB): uma `Row` fixa com 3 `UpcomingEventCard` (óleo, pneus,
  licenciamento) — sem link "Ver todos" (não existe hoje nenhuma tela de
  destino para listar mais itens; sempre são exatamente 3 cartões).
  Renderizada **sempre**, inclusive quando `isFirstUse` (veículo sem
  nenhum abastecimento ainda) — diferente de `FinancialSummaryCard`/
  `IndicatorsGrid`/`LastRefuelCard`, os lembretes não dependem de
  abastecimentos: `vehicle.currentKm` já existe desde o cadastro do
  veículo (óleo/pneus) e o licenciamento é só uma data digitada pelo
  usuário — nenhum dos dois precisa de histórico de abastecimento.
- **Troca de óleo / Rodízio de pneus** (lembrete por km): calculado a partir
  do último `VehicleEvent` da categoria correspondente (`OIL_CHANGE` /
  `TIRES`) mais um intervalo fixo (10.000 km para ambos, constante no
  código — não configurável nesta versão). Se não existir nenhum evento
  daquela categoria ainda, usa uma **âncora local persistida** (ver
  "Armazenamento local" abaixo) em vez do odômetro atual bruto — do
  contrário "faltam 10.000 km" nunca mudaria, já que o odômetro atual muda
  a cada abastecimento.
- **Licenciamento** (lembrete por data): `licensingDueDate` definido
  manualmente pelo usuário, por veículo, armazenado **somente localmente no
  dispositivo** (ver "Armazenamento local"). Este repositório contém apenas
  o cliente Android — o backend é um serviço Spring Boot separado
  (documentado em `.claude/docs_api`, sem código-fonte aqui) — então
  qualquer campo novo persistido no servidor exigiria uma mudança fora
  deste repositório. Optamos por armazenamento local: sem dependência de
  backend, ao custo de não sincronizar entre dispositivos/reinstalações.
- Estado **atrasado** (não estava na spec original, mas é uma consequência
  óbvia de ter uma contagem regressiva): quando o valor fica negativo, o
  cartão troca para o estilo de erro e o texto vira "Atrasado X km" /
  "Venceu há X dias".
- Cartão de licenciamento sem data definida ainda: estado de "prompt"
  ("Defina a data de licenciamento"), tocar abre um diálogo de
  seletor de data dedicado (não a tela de edição de veículo — ver
  "Por que não a tela de edição de veículo" abaixo).
- Toque em um cartão de óleo/pneus (fora do estado de prompt — este estado
  não existe para eles, sempre há uma estimativa) navega para a tela de
  criação de evento já existente, com a categoria pré-selecionada.
- Skeleton de carregamento e erro isolado por seção, mesmo padrão das
  demais seções da Home (`SectionState`).

### Fora do escopo (specs futuras e independentes)

- `TopBar` com menu hambúrguer/gaveta de navegação — não existe hoje
  nenhuma gaveta nem destino para ela abrir; precisa de design próprio.
- FAB encaixado ("docked") na bottom bar — mudança de layout em
  `FFBottomBar`, componente compartilhado por todas as 5 abas, não só a
  Home; precisa de design próprio.
- Sino de notificações reais — já existe como ícone estático em
  `VehicleHeader.kt`, reservado para o futuro; sem mudança aqui.
- Intervalos configuráveis por veículo para óleo/pneus (usar o valor do
  manual do fabricante) — fica com o valor fixo de 10.000 km por agora.
- Regra de licenciamento por dígito da placa (tabela CONTRAN) — descartada
  por risco de estar errada por mudança de regra/estado; o usuário digita a
  data manualmente.
- Link "Ver todos" / tela de listagem de lembretes futuros — não existe
  ainda uma lista maior que os 3 itens fixos para justificar isso.
- Sincronização entre dispositivos do `licensingDueDate` (exigiria mudança
  de backend, fora deste repositório).

## Por que não a tela de edição de veículo

A opção natural seria adicionar `licensingDueDate` ao formulário de
`EditVehicleScreen`, que já edita campos como placa e ano. Decidimos não
fazer isso: esse formulário salva via `PUT /vehicles/{id}`
(`UpdateVehicleRequestDto`), ou seja, todo campo nele parece — e é — um
campo sincronizado com o backend. Misturar um campo puramente local nesse
mesmo formulário criaria uma sincronização falsa: o usuário veria o campo
ao lado de "placa"/"cor" e assumiria (razoavelmente) que ele também é
salvo no servidor e aparece em outro dispositivo, o que não é verdade.
Por isso o cartão de licenciamento abre seu próprio diálogo de data,
visualmente e arquiteturalmente separado da edição de veículo.

## Armazenamento local

Novo `VehicleMaintenancePrefsStore` em `core/datastore/`, mesmo padrão de
`SessionStore.kt` (Preferences DataStore + Hilt singleton), arquivo
`preferencesDataStore(name = "flowfuel_vehicle_maintenance")`. Chaves
construídas dinamicamente por veículo (Preferences DataStore aceita chaves
criadas em runtime; não há schema fixo):

```kotlin
class VehicleMaintenancePrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun licensingDueDateFlow(vehicleId: Int): Flow<String?> =
        context.dataStore.data.map { it[stringPreferencesKey("licensing_due_$vehicleId")] }

    suspend fun saveLicensingDueDate(vehicleId: Int, isoDate: String) { /* ... */ }

    /** Âncora de km usada só enquanto não existe nenhum evento real daquela categoria. */
    fun anchorKmFlow(vehicleId: Int, category: EventCategory): Flow<Int?> =
        context.dataStore.data.map { it[intPreferencesKey("anchor_${category.name}_$vehicleId")] }

    suspend fun saveAnchorKm(vehicleId: Int, category: EventCategory, km: Int) { /* ... */ }
}
```

- `licensingDueDate` e as âncoras (`anchor_OIL_CHANGE_<id>`,
  `anchor_TIRES_<id>`) seguem o formato ISO `yyyy-MM-dd` / `Int` em km, sem
  criptografia adicional (não é dado sensível).
- A âncora é gravada **uma única vez**, na primeira vez que o
  `GetUpcomingMaintenanceUseCase` roda para aquele veículo sem encontrar
  nenhum evento da categoria — com o valor do odômetro atual naquele
  momento. Depois disso ela nunca é sobrescrita automaticamente; só é
  ignorada (não removida) no momento em que um evento real daquela
  categoria passa a existir, já que o cálculo passa a preferir o evento
  real sobre a âncora.

## Arquitetura e estrutura de pacotes

Evolução do `feature/home` existente:

```
feature/home/
├── presentation/
│   ├── HomeScreen.kt                 (adiciona a seção sempre visível, após o bloco condicional de LastRefuelCard/RecentActivityCard)
│   ├── HomeViewModel.kt              (+ carrega upcomingMaintenance em paralelo)
│   ├── HomeUiState.kt                (+ upcomingMaintenance: SectionState<List<UpcomingMaintenanceItem>>)
│   ├── LicensingDueDateDialog.kt     (novo — diálogo de seletor de data, reaproveita o padrão de
│   │                                   CreateVehicleEventScreen.kt: DatePicker + DatePickerDialog)
│   └── components/
│       └── UpcomingEventsSection.kt  (novo — Row com 3 UpcomingEventCard)
├── domain/
│   ├── model/HomeModels.kt           (+ UpcomingMaintenanceItem, UpcomingMaintenanceType)
│   └── usecase/
│       └── GetUpcomingMaintenanceUseCase.kt   (novo)
└── di/HomeModule.kt                  (registra o novo use case; injeta VehicleMaintenancePrefsStore)

core/datastore/
└── VehicleMaintenancePrefsStore.kt   (novo)
```

**Modelo (`HomeModels.kt`):**

```kotlin
enum class UpcomingMaintenanceType { OIL_CHANGE, TIRE_ROTATION, LICENSING }

data class UpcomingMaintenanceItem(
    val type: UpcomingMaintenanceType,
    /** null quando é licenciamento sem data definida ainda (estado de prompt). */
    val remainingKm: Int? = null,
    val remainingDays: Int? = null,
    val isOverdue: Boolean = false,
    val needsSetup: Boolean = false, // true só para LICENSING sem data
)
```

**`GetUpcomingMaintenanceUseCase`:**

1. Busca o último evento `OIL_CHANGE` e o último `TIRES` via
   `GetVehicleEventsPageUseCase(vehicleId, category=..., page=0, size=1)` —
   mesmo use case já usado por `GetRecentActivityUseCase`, uma chamada por
   categoria.
2. Para cada categoria, se existir evento: `dueKm = evento.odometerKm +
   10_000`. Se não existir: lê `anchorKmFlow`; se também não existir ainda,
   grava `anchorKm = vehicle.currentKm` e usa `dueKm = anchorKm + 10_000`.
   `remainingKm = dueKm - vehicle.currentKm` (negativo ⇒ `isOverdue = true`).
3. Lê `licensingDueDateFlow(vehicleId)`. Se `null` ⇒
   `UpcomingMaintenanceItem(LICENSING, needsSetup = true)`. Caso contrário,
   `remainingDays = diasEntre(hoje, dueDate)` (negativo ⇒ `isOverdue =
   true`).
4. Retorna a lista de 3 itens, sempre na mesma ordem (óleo, pneus,
   licenciamento).

Sem chamada de rede nova: reaproveita `GetVehicleEventsPageUseCase`
(`feature/vehicleevent`, já uma dependência de `feature/home` desde o
design de 2026-07-06) e lê/escreve só o novo `VehicleMaintenancePrefsStore`
local.

## Estados e tratamento de erros

Mesmo padrão das demais seções da Home (`SectionState<T>`):

- **Loading**: carregado em paralelo com `financialSummary` e
  `recentActivity` (mesmo `coroutineScope`/`async` já usado em
  `HomeViewModel`); mostra `FFSkeletonBlock` só nessa seção enquanto
  carrega.
- **Success**: renderiza os 3 `UpcomingEventCard`, cada um em um dos 4
  sub-estados: normal, atrasado, prompt (só licenciamento).
- **Error**: falha isolada — só essa seção mostra erro inline + retry
  (`SectionErrorCard`, componente que já existe em `HomeScreen.kt`); o
  resto da tela continua funcional.
- Não há estado "Empty" próprio: a seção sempre renderiza os 3 cartões,
  mesmo com `isFirstUse` (ver "Escopo" acima) — diferente das seções que
  dependem de abastecimentos.

## UI

`UpcomingEventsSection` (`feature/home/presentation/components/`):

```kotlin
@Composable
fun UpcomingEventsSection(
    items: List<UpcomingMaintenanceItem>,
    onCardClick: (UpcomingMaintenanceType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
        // Título simples (Text), não FFSectionHeader: esse componente já aplica seu
        // próprio padding horizontal, o que dobraria o padding dentro de um item do
        // LazyColumn (que já recebe contentPadding) — nenhuma outra seção da Home usa
        // FFSectionHeader hoje, todas usam o título embutido do FFCard.
        Text("Próximos eventos", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
            items.forEach { item ->
                UpcomingEventCard(item, onClick = { onCardClick(item.type) }, modifier = Modifier.weight(1f))
            }
        }
    }
}
```

Sem link "Ver todos" (decisão já registrada em "Escopo").

`UpcomingEventCard`: ícone em bolha colorida + título curto + subtexto de
contagem — mesma estrutura visual de `IndicatorCard`/`FFStatTile`
(reaproveitar o componente base se o formato couber; senão, um `FFCard`
compacto próprio).

- Ícones (todos já usados no app, nenhum novo): `Icons.Outlined.Opacity`
  (óleo, hoje mapeado para `EventCategory.OIL_CHANGE` em
  `EventCategoryChip.kt`), `Icons.Outlined.TireRepair` (pneus, hoje mapeado
  para `EventCategory.TIRES`), `Icons.AutoMirrored.Outlined.Article`
  (licenciamento — reaproveita o ícone de `DOCUMENTS`).
- Cores via `FFTheme` (a spec original usava `Color()` literais e um roxo
  sem equivalente no design system atual — substituídos):
  - Óleo → `MaterialTheme.colorScheme.tertiary` / `FFTheme.semanticColors.warning` (laranja/âmbar)
  - Pneus → `FFTheme.semanticColors.success` / `brandGreen` (verde)
  - Licenciamento → `MaterialTheme.colorScheme.secondary` / `FFTheme.semanticColors.info` (azul)
  - Atrasado (qualquer tipo) → `MaterialTheme.colorScheme.error`, sobrepõe a cor do tipo
- Texto: "Em 320 km" / "Vence em 18 dias" (normal); "Atrasado 40 km" /
  "Venceu há 3 dias" (atrasado); "Defina a data de licenciamento" (prompt,
  sem número).

`LicensingDueDateDialog`: `DatePickerDialog` + `rememberDatePickerState`,
mesmo padrão de `CreateVehicleEventScreen.kt` (millis → `LocalDate` UTC →
`toString()` ISO). Ao confirmar, chama
`VehicleMaintenancePrefsStore.saveLicensingDueDate` e recarrega a seção.

## Navegação

Um novo parâmetro de query opcional na rota existente, para pré-selecionar
a categoria ao abrir a tela de criação de evento a partir de um cartão de
óleo/pneus:

- `Destinations.kt`: `VEHICLE_EVENT_CREATE` passa de
  `"vehicle/events/create/{vehicleId}"` para
  `"vehicle/events/create/{vehicleId}?category={category}"`.
- `FlowFuelNavHost.kt`: adiciona `navArgument("category") { type =
  NavType.StringType; nullable = true; defaultValue = null }` à entrada
  existente.
- `CreateVehicleEventViewModel` lê o argumento (se presente) e inicializa o
  formulário com aquela `EventCategory` e `eventDate = LocalDate.now()` em
  vez do estado inicial vazio atual — único ponto tocado nessa tela.
- Chamada a partir da Home: `onCardClick` navega para
  `"vehicle/events/create/$vehicleId?category=OIL_CHANGE"` (ou `TIRES`).
  Licenciamento não navega para lá — abre `LicensingDueDateDialog`
  localmente.

## Testes

`GetUpcomingMaintenanceUseCaseTest` (JUnit + MockK, mesmo padrão de
`GetFinancialSummaryUseCase`/`GetRecentActivityUseCase`):

- Óleo/pneus com evento existente: `remainingKm` calculado a partir do
  odômetro do evento + 10.000, não da âncora.
- Óleo/pneus sem nenhum evento: grava a âncora uma única vez e usa o
  odômetro atual como base; uma segunda chamada não regrava a âncora nem
  muda a base (mesmo `remainingKm` decrescendo apenas quando o odômetro
  do veículo avança, nunca "resetando").
- Transição âncora → evento real: depois que um evento passa a existir,
  o cálculo ignora a âncora persistida e usa o evento.
- `remainingKm`/`remainingDays` negativo ⇒ `isOverdue = true`.
- `licensingDueDate` ausente ⇒ `needsSetup = true`, sem crash.
- Falha em `GetVehicleEventsPageUseCase` isolada não impede o restante da
  Home de carregar (só a seção de lembretes mostra erro).

`HomeViewModelTest.kt`: atualizado incrementalmente para incluir a nova
seção nos casos de Loading → Success e de erro isolado, mesmo padrão já
usado para `financialSummary`/`recentActivity`.

Previews Compose para `UpcomingEventsSection`/`UpcomingEventCard` cobrindo
os 4 sub-estados (normal, atrasado, prompt, loading) em light/dark, mesma
convenção dos demais componentes da Home.

Sem testes de UI/Compose instrumentados e sem testes de backend (nenhuma
mudança de backend neste design), consistente com o restante do projeto.
