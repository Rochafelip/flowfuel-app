# Meus Veículos: exibir veículos compartilhados junto dos próprios

**Data:** 2026-07-22
**Status:** Aprovado, aguardando plano de implementação

## Contexto

`VehiclesScreen` ("Meus Veículos", acessível via Perfil → item de menu, rota
`Destinations.VEHICLE_MANAGE`) hoje só lista veículos de propriedade do
usuário (`GET /vehicles` paginado). Veículos compartilhados com o usuário
(convite aceito, ver [[project_vehicleshare_module]]) só aparecem hoje em
`VehiclePickerScreen`, que já resolve o problema equivalente: uma lista
mista de itens `Owned` (próprios) e `Borrowed` (emprestados), com
`GetActiveSharedVehiclesUseCase` buscando os compartilhamentos ativos e um
card visualmente distinto (badge pill "Emprestado") para os emprestados.

Esta spec estende o mesmo conceito para `VehiclesScreen`, que é uma tela de
**gerenciamento** (editar, excluir, definir ativo, ver eventos) — ao
contrário do picker, que é só seleção. Isso importa porque as ações de
gerenciamento (editar, excluir, "definir como ativo" via
`SetActiveVehicleUseCase`) não se aplicam a veículos emprestados: o backend
retorna 403 para convidado em `PUT/DELETE /vehicles/{id}` (confirmado em
teste manual, ver [[project_vehicleshare_module]]).

## Requisitos

1. `VehiclesScreen` mostra tanto veículos próprios quanto veículos
   compartilhados ativos (aceitos) com o usuário, em duas seções com
   cabeçalho de texto: **"Meus veículos"** e **"Compartilhados comigo"**.
2. Cada veículo emprestado exibe um indicador visual claro de que é
   diferente de um veículo próprio: o mesmo badge pill "Emprestado" (fundo
   `FFTheme.semanticColors.info`) já usado em `VehiclePickerScreen`, mais o
   próprio cabeçalho de seção "Compartilhados comigo" que já separa os dois
   grupos. Este é um requisito explícito, não só um efeito colateral do
   agrupamento — a tag + a seção juntas garantem que o usuário nunca confunda
   um veículo emprestado com um próprio nesta tela.
3. Veículos emprestados **não** têm o menu de 3 pontos (sem editar, excluir,
   "definir como ativo" no sentido de dono, sem "Eventos" direto — essas
   ações não existem/não se aplicam para o papel de convidado nesta tela).
4. Tocar num veículo emprestado define-o como veículo ativo em modo
   convidado (`SetActiveGuestVehicleUseCase`) e navega para o fluxo de modo
   convidado (`GuestVehicleScreen`, via `MAIN_CONTAINER`), com o mesmo
   comportamento de limpeza de pilha de navegação (`popUpTo(0)`) que já
   ocorre ao selecionar um veículo emprestado no picker.
5. A seção "Compartilhados comigo" só aparece se houver ao menos um
   compartilhamento ativo; some por completo se a lista vier vazia (sem
   cabeçalho órfão).
6. O estado `Empty` da tela (hoje "Nenhum veículo cadastrado") só deve
   aparecer quando **ambas** as listas (próprios e emprestados) estiverem
   vazias.
7. Falha ao buscar compartilhamentos ativos degrada silenciosamente para
   lista vazia de emprestados — não derruba a tela inteira com um estado de
   erro global (mesma tolerância já usada em `VehiclePickerViewModel`).
8. Paginação (scroll infinito) continua se aplicando somente à seção de
   veículos próprios. A seção de compartilhados não é paginada (mesma
   premissa do picker: lista tipicamente pequena).

## Fora de escopo

- Qualquer indicador nos veículos **próprios** do usuário sinalizando que
  ele os compartilhou com terceiros (ex.: badge "Compartilhado" no card do
  dono). Descartado explicitamente nesta rodada — decisão do usuário.
- Mudanças em `VehiclePickerScreen`/`VehiclePickerViewModel` além da
  extração do componente de card compartilhado (ver Componentes).
- Acesso de convidado a `VehicleEventsScreen`/edição/exclusão — permanece
  bloqueado como já está.

## Arquitetura

### Estado / ViewModel

`VehiclesUiState.kt`:
- Novo sealed interface `VehiclesListItem`:
  - `Owned(val vehicle: Vehicle)`
  - `Borrowed(val share: VehicleShare)`
- `VehiclesScreenState.Success` passa a carregar `items: List<VehiclesListItem>`
  no lugar de `vehicles: List<Vehicle>`.
- Novo efeito `VehiclesEffect.NavigateToGuestVehicle(val share: VehicleShare)`.

`VehiclesViewModel.kt`:
- Injeta `GetActiveSharedVehiclesUseCase` e `SetActiveGuestVehicleUseCase`
  (mesmos já usados em `VehiclePickerViewModel`).
- `load()`: busca página 0 de `getVehiclesPage(0)` e, em sequência,
  `getActiveSharedVehicles()` — mesmo padrão sequencial do
  `VehiclePickerViewModel.load()` (sem necessidade de paralelismo, listas
  pequenas). Resultado de compartilhados tratado com
  `(result as? AppResult.Success)?.value ?: emptyList()` (tolerante a
  falha, requisito 7).
  - `Empty` dispara só quando `paged.items.isEmpty() && borrowedItems.isEmpty()`.
  - `Success` guarda ambas as listas separadamente no estado (para permitir
    renderizar duas seções) — `VehiclesScreenState.Success` ganha
    `ownedItems: List<Vehicle>` e `borrowedItems: List<VehicleShare>` (mais
    simples e explícito que remontar via `filterIsInstance` toda hora na UI;
    diverge levemente do picker, que usa uma lista única `items` porque lá
    não há seções visuais).
- `loadNextPage()`: só mexe em `ownedItems` (dedup por id, igual hoje);
  `borrowedItems` permanece como veio do load inicial.
- Novo método `onBorrowedSelected(share: VehicleShare)`: chama
  `setActiveGuestVehicle(share.vehicleId)` e emite
  `VehiclesEffect.NavigateToGuestVehicle(share)`.

### UI

`VehiclesScreen.kt`:
- `LazyColumn` ganha uma seção "Meus veículos" (`item { Text(...) }` como
  cabeçalho, seguido de `itemsIndexed` sobre `ownedItems` — mesmo
  `VehicleManageItem` de hoje, sem mudança) e, se `borrowedItems.isNotEmpty()`,
  uma seção "Compartilhados comigo" (cabeçalho + `items` sobre
  `borrowedItems`, renderizando o card compartilhado extraído — ver
  Componentes). Cabeçalho de seção: `Text` com
  `MaterialTheme.typography.titleSmall`, mesmo padrão tipográfico já usado
  em `RecentActivityCard` para rótulos de agrupamento (não existe um
  componente `FFSectionHeader` dedicado no design system hoje — não vale
  criar um só para isso, YAGNI).
- Paginação (skeleton de loading/erro de página) continua ancorada ao fim
  da seção de próprios, como hoje.
- Novo efeito tratado: `VehiclesEffect.NavigateToGuestVehicle` → callback
  `onNavigateToGuestVehicle: (VehicleShare) -> Unit` (novo parâmetro da
  screen, análogo ao já existente em `VehiclePickerScreen`).

### Componente compartilhado

O card de veículo emprestado (`BorrowedVehicleCard`, hoje privado em
`VehiclePickerScreen.kt`: nome/modelo + badge "Emprestado" + "até
{data de expiração}") é extraído para
`core/designsystem/components/FFBorrowedVehicleCard.kt`. Ambas as telas
(`VehiclePickerScreen` e `VehiclesScreen`) passam a usá-lo — evita duplicar
a formatação de data (`formatShareExpiry`) e o badge. `VehiclePickerScreen`
é atualizado para consumir a versão extraída (sem mudança visual).

### Navegação (`FlowFuelNavHost.kt`)

Rota `Destinations.VEHICLE_MANAGE` ganha `onNavigateToGuestVehicle`, com o
mesmo corpo já usado na rota do picker (linha ~296): navega para
`Destinations.MAIN_CONTAINER` com `popUpTo(0) { inclusive = true }`. Isso
limpa toda a pilha de navegação, incluindo a tela de Perfil de onde o
usuário veio — comportamento consciente e consistente: entrar em modo
convidado sempre reseta a navegação para a raiz, não importa de onde foi
acionado.

## Erros e casos de borda

- Fetch de compartilhados falha → lista de emprestados vazia, sem erro
  visível (requisito 7). Próximo `load()` (pull-to-refresh/retry/edição)
  tenta de novo.
- Usuário com 0 veículos próprios mas 1+ compartilhados → seção "Meus
  veículos" fica vazia (sem `FFEmptyState`; simplesmente não renderiza
  itens ali) e "Compartilhados comigo" aparece normalmente. FAB "Novo
  veículo" continua visível (não é afetado por este estado).
- Usuário com 0 em ambos → `VehiclesScreenState.Empty` como hoje.
- Compartilhamento é revogado pelo dono enquanto a tela está aberta → sem
  atualização em tempo real (mesma limitação já aceita no picker); some no
  próximo `load()`.

## Testes

- Não existe `VehiclesViewModelTest` hoje. Criar seguindo TDD, cobrindo:
  lista mista carregada com sucesso (owned + borrowed separados no estado),
  `onBorrowedSelected` emitindo `NavigateToGuestVehicle` e chamando
  `setActiveGuestVehicle`, `Empty` só quando ambas vazias, tolerância a
  falha no fetch de compartilhados (tela mostra só os próprios, sem erro).
- `VehiclePickerViewModelTest` existente não deve quebrar com a extração do
  card (é só um componente de UI, sem lógica de ViewModel envolvida).
