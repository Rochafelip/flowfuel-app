# GuestVehicleScreen: separar "Abastecer" e "Registrar despesa"

**Data:** 2026-07-25
**Status:** Aprovado, aguardando plano de implementação

## Contexto

`GuestVehicleScreen` (`feature/vehicle/presentation/guest/GuestVehicleScreen.kt`,
ver [[project_vehicleshare_module]]) é a Home mínima do convidado (usuário
com veículo emprestado). Hoje tem um único botão "Registrar
abastecimento/despesa" que chama `GuestVehicleViewModel.onCreateEventClicked()`,
emitindo `GuestVehicleEffect.NavigateToCreateEvent(vehicleId)` sem nenhuma
categoria — a navegação (`FlowFuelNavHost.kt`, `onNavigateToGuestEventCreate`)
chama `Destinations.vehicleEventCreate(vehicleId, guestMode = true)`, também
sem `category`. Isso difere do padrão já usado por
`onNavigateToMaintenanceEventCreate`, que sempre passa a categoria.

Resultado: o convidado toca no botão esperando ir para o fluxo de
abastecimento e cai no formulário genérico de evento
(`CreateVehicleEventScreen`) com a categoria "Outro" pré-selecionada — a
tela certa, mas com o campo errado marcado, parecendo a tela errada. Bug
relatado pelo usuário e confirmado por leitura de código (sem reprodução no
emulador nesta rodada — sem compartilhamento ativo na conta de QA no
momento, ver [[project_qa_test_account]]).

Nota: o convidado **não** usa o mesmo fluxo de abastecimento do dono
(`QuickRefuelBottomSheet`/`CreateRefuelUseCase`, que grava um `Refuel` de
verdade) — o backend só permite ao convidado criar `VehicleEvent` nas
categorias `FUEL, WASH, TIRES, OTHER` (ver [[project_vehicleshare_module]],
testado contra produção em 2026-07-15). Este spec não muda esse contrato,
só corrige qual categoria vem pré-selecionada em cada entrada.

## Requisitos

1. `GuestVehicleScreen` troca o botão único "Registrar abastecimento/despesa"
   por dois botões:
   - **"Abastecer"** — estilo primário (`FFButtonVariant.Primary`), mesma
     posição que o botão atual ocupa (logo abaixo do bloco de odômetro).
     Abre `CreateVehicleEventScreen` com categoria `FUEL` pré-selecionada.
   - **"Registrar despesa"** — estilo secundário (`FFButtonVariant.Text`),
     logo abaixo do botão "Abastecer". Abre `CreateVehicleEventScreen` com
     categoria `OTHER` pré-selecionada (igual ao comportamento atual, sem
     categoria explícita).
2. Em ambos os casos o usuário continua podendo trocar a categoria dentro do
   formulário (`CreateVehicleEventScreen` já permite isso e já restringe às
   4 categorias liberadas para convidado) — a pré-seleção é só o ponto de
   partida.
3. Sem mudança de comportamento nos demais elementos da tela (odômetro,
   "Trocar de veículo", mensagens de erro/sucesso).

## Fora de escopo

- Criar uma tela de abastecimento dedicada (campos de litros/valor/tanque
  cheio como o `QuickRefuelBottomSheet` do dono) — o backend não expõe esse
  endpoint para convidados; ficaria salvando um `VehicleEvent` categoria
  `FUEL` de qualquer forma. Avaliado e descartado nesta rodada (decisão do
  usuário: separar os dois botões é suficiente).
- Qualquer mudança em `CreateVehicleEventScreen`/`CreateVehicleEventViewModel`
  além de já aceitarem `category` via `SavedStateHandle` (nenhuma mudança
  necessária ali).
- Mudança nas categorias permitidas para convidado (`FUEL, WASH, TIRES,
  OTHER`) — contrato do backend, não faz parte deste spec.

## Arquitetura

Mudança em cadeia por 4 arquivos, do evento de UI até a navegação —
mesmo padrão que `onNavigateToMaintenanceEventCreate` já estabelece para o
fluxo do dono (`vehicleId + category`):

1. **`GuestVehicleViewModel.kt`**
   - `GuestVehicleEffect.NavigateToCreateEvent` ganha um campo:
     `data class NavigateToCreateEvent(val vehicleId: Int, val category: EventCategory)`.
   - `onCreateEventClicked()` é substituído por dois métodos:
     - `onRefuelClicked()` → envia `NavigateToCreateEvent(vehicleId, EventCategory.FUEL)`.
     - `onExpenseClicked()` → envia `NavigateToCreateEvent(vehicleId, EventCategory.OTHER)`.
   - Import novo: `com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory`.

2. **`GuestVehicleScreen.kt`**
   - Parâmetro do composable muda de
     `onNavigateToCreateEvent: (vehicleId: Int) -> Unit` para
     `onNavigateToCreateEvent: (vehicleId: Int, category: EventCategory) -> Unit`.
   - Handler do efeito:
     `is GuestVehicleEffect.NavigateToCreateEvent -> onNavigateToCreateEvent(effect.vehicleId, effect.category)`.
   - O único `FFButton("Registrar abastecimento/despesa", ...)` vira dois:
     ```
     FFButton(text = "Abastecer", onClick = viewModel::onRefuelClicked,
         variant = FFButtonVariant.Primary, modifier = Modifier.fillMaxWidth())
     Spacer(...)
     FFButton(text = "Registrar despesa", onClick = viewModel::onExpenseClicked,
         variant = FFButtonVariant.Text, modifier = Modifier.fillMaxWidth())
     ```

3. **`MainContainerScreen.kt`**
   - Parâmetro `onNavigateToGuestEventCreate: (vehicleId: Int) -> Unit`
     muda para `(vehicleId: Int, category: EventCategory) -> Unit` — mesma
     assinatura de `onNavigateToMaintenanceEventCreate`, já presente no
     arquivo.
   - Chamada a `GuestVehicleScreen` passa a repassar a categoria:
     `onNavigateToCreateEvent = { vehicleId, category -> onNavigateToGuestEventCreate(vehicleId, category) }`.

4. **`FlowFuelNavHost.kt`**
   - `onNavigateToGuestEventCreate = { vehicleId -> ... }` vira
     `onNavigateToGuestEventCreate = { vehicleId, category -> navController.navigate(Destinations.vehicleEventCreate(vehicleId, category.name, guestMode = true)) }`.
   - `Destinations.vehicleEventCreate(vehicleId, category, guestMode)` já
     aceita os dois parâmetros juntos (usado hoje só sem `guestMode` pelo
     fluxo do dono) — sem mudança na função nem na rota
     `VEHICLE_EVENT_CREATE`.

`CreateVehicleEventViewModel` não muda: já lê `category` do
`SavedStateHandle` e já valida contra `availableCategories` (que já
restringe a `FUEL, WASH, TIRES, OTHER` quando `guestMode=true`).

## Erros e casos de borda

- Nenhum novo estado de erro — a navegação e a criação do evento seguem o
  mesmo caminho de hoje, só muda qual categoria chega pré-selecionada.
- Se o usuário trocar a categoria manualmente no formulário depois de abrir
  por qualquer um dos dois botões, o comportamento é idêntico ao atual
  (sem diferença entre "abastecer" e "despesa" a partir daí).

## Testes

- `GuestVehicleViewModelTest`: dois testes novos —
  `onRefuelClicked_emiteNavigateToCreateEventComCategoriaFuel` e
  `onExpenseClicked_emiteNavigateToCreateEventComCategoriaOther`, seguindo o
  padrão `viewModel.effects.test { ... }` já usado no arquivo.
- Sem testes de UI Compose no projeto (padrão estabelecido, ver
  [[project_architecture]]) — verificação da cadeia de navegação
  (`GuestVehicleScreen → MainContainerScreen → FlowFuelNavHost`) é manual,
  por leitura de código e, se possível, teste no emulador com uma conta que
  tenha compartilhamento ativo (a conta de QA atual não tem — ver
  [[project_qa_test_account]]).
