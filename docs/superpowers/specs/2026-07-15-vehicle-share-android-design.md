# Compartilhamento de Veículo — Android

> Escopo cruza dois repositórios: `flowfuel-app` (Android, este repositório) e
> `flowfuel` (backend, Spring Boot/WSL). Esta spec cobre só o cliente Android
> — o contrato de API que ela consome está definido e implementado em
> `docs/superpowers/specs/2026-07-14-vehicle-share-backend-design.md` e
> `docs/superpowers/plans/2026-07-14-vehicle-share-backend.md` (repositório
> `flowfuel`).

**Data:** 2026-07-15
**Status:** aprovado

## Contexto

Quando um usuário empresta o carro pra outra pessoa por um dia (ou mais), o
backend agora permite que o dono convide (por email) outro usuário FlowFuel
já cadastrado a lançar eventos de uso diário (`FUEL`, `CAR_WASH`, `TIRES`,
`OTHER`) e atualizar o odômetro naquele veículo, por um prazo definido, sem
ganhar acesso a mais nada (sem ver histórico, sem editar dados do veículo).
Falta o lado Android: telas pro dono convidar/revogar, pro convidado
aceitar/recusar (via push, reaproveitando o FCM já implementado — ver
[[project_push_notification_frontend]]), e uma experiência dedicada e mínima
pro convidado enquanto o veículo emprestado está "ativo" na sessão dele.

**Restrições herdadas do backend** (não renegociáveis nesta spec):
- Convite só por email — backend não tem `username`.
- Categorias de evento permitidas pro convidado: `FUEL`, `CAR_WASH`,
  `TIRES`, `OTHER`. As demais retornam 403.
- Um veículo só tem um compartilhamento `PENDING`/`ACTIVE` por vez.
- Convidado não pode `GET /vehicles/{id}` (dados completos do veículo),
  nem `GET /vehicle-events` (histórico), nem `PUT /vehicles/{id}/active`
  (isso é escopado ao dono via `findByUserId` no backend) — só
  `POST /vehicle-events` (categorias permitidas) e
  `PUT /vehicles/{id}/odometer`.
- Convite exige aceite explícito (`PENDING` → `ACTIVE`/`REJECTED`).
- Aceite/recusa/revogação não disparam push — só a criação do convite.

Essas restrições do backend moldam boa parte das decisões de UI abaixo: como
o convidado não pode buscar o veículo completo nem o km atual, as telas dele
não podem depender desses dados.

Fora de escopo: múltiplos convidados simultâneos no mesmo veículo (backend já
impede); notificação em tempo real pro dono a cada evento lançado pelo
convidado; edição/exclusão de eventos pelo convidado.

## Decisões

### 1. Camada de dados

Novo pacote `core/vehicleshare` (domínio isolado, mesmo padrão de
`core/notification`):
- `VehicleShareApi` (Retrofit, DTOs `@Serializable`):
  - `POST vehicle-shares` (`VehicleShareRequestDto`: `vehicleId`,
    `inviteeEmail`, `durationDays`)
  - `POST vehicle-shares/{id}/accept`
  - `POST vehicle-shares/{id}/reject`
  - `DELETE vehicle-shares/{id}`
  - `GET vehicle-shares/vehicle/{vehicleId}` (retorna `VehicleShareResponseDto?`
    — 204 sem corpo quando não há share pendente/ativo)
  - `GET vehicle-shares/pending` → `List<VehicleShareResponseDto>`
  - `GET vehicle-shares/active-for-me` → `List<VehicleShareResponseDto>`
  - `VehicleShareResponseDto`: `id`, `vehicleId`, `vehicleBrand`,
    `vehicleModel`, `ownerId`, `ownerName`, `guestId`, `guestName`, `status`
    (string: `PENDING`/`ACTIVE`/`REJECTED`/`REVOKED`/`EXPIRED`), `createdAt`,
    `respondedAt`, `expiresAt` (todos os timestamps como `String?` ISO,
    mesmo padrão já usado nos outros DTOs do projeto).
- `VehicleShareRepository` (interface) + `VehicleShareRepositoryImpl`
  (`apiCall {}` → `AppResult<T>`), registrados via `Binds`/`Provides` em
  `VehicleShareModule` — mesmo padrão de `DeviceTokenModule`.
- Domain model `VehicleShare` (mapeado do DTO) com `VehicleShareStatus`
  (enum Kotlin espelhando o backend) — usado pelas telas abaixo.
- Use cases (`core/vehicleshare/domain/usecase`): `InviteVehicleShareUseCase`,
  `AcceptVehicleShareUseCase`, `RejectVehicleShareUseCase`,
  `RevokeVehicleShareUseCase`, `GetVehicleShareForVehicleUseCase`,
  `GetPendingVehicleSharesUseCase`, `GetActiveSharedVehiclesUseCase` — um
  método fino por endpoint, seguindo o padrão de use case único já usado em
  `vehicle`/`vehicleevent`.

### 2. Estado local do "veículo ativo emprestado"

O backend não tem conceito de "ativo" pro convidado (`isActive` do veículo é
escopado ao dono via `findByUserId`) — então esse estado é puramente local.

`SessionStore` ganha uma chave nova, `ACTIVE_VEHICLE_IS_GUEST`
(`booleanPreferencesKey`), lida via `activeVehicleIsGuestFlow: Flow<Boolean>`.
`saveActiveVehicleId` ganha um parâmetro `isGuest: Boolean = false` que grava
os dois valores juntos (escolher um veículo próprio zera automaticamente a
flag). `clearActiveVehicleId()` remove as duas chaves.

### 3. `VehiclePickerScreen` — veículo emprestado aparece na lista

Decisão do brainstorm original: o veículo emprestado aparece junto na lista
normal de veículos, com tag "Emprestado", sem precisar de aba separada.

`VehiclePickerViewModel.load()` passa a combinar dois resultados:
`GetVehiclesPageUseCase` (como hoje) e `GetActiveSharedVehiclesUseCase`
(novo — `GET /vehicle-shares/active-for-me`). Como o `VehicleShareResponseDto`
só carrega `vehicleId`/`vehicleBrand`/`vehicleModel` (o convidado não pode
buscar o veículo completo), os itens emprestados são renderizados como um
card mais simples — sem foto, sem placa — só marca/modelo, badge
"Emprestado" e "até dd/mm" (de `expiresAt`). Isso é intencional: sinaliza
visualmente que é um tipo de item diferente, não força um dado que não
existe.

`VehiclePickerUiState.Success.vehicles: List<Vehicle>` vira
`items: List<VehiclePickerItem>`, um sealed interface:
```kotlin
sealed interface VehiclePickerItem {
    data class Owned(val vehicle: Vehicle) : VehiclePickerItem
    data class Borrowed(val share: VehicleShare) : VehiclePickerItem
}
```
`onVehicleSelected` bifurca: `Owned` segue o fluxo atual
(`SetActiveVehicleUseCase`, que chama `PUT /vehicles/{id}/active`). `Borrowed`
usa um novo `SetActiveGuestVehicleUseCase`, que só faz
`sessionStore.saveActiveVehicleId(vehicleId, isGuest = true)` — sem chamada
de rede (o convidado não é dono, não pode marcar `isActive` no backend).

### 4. Bottom nav em modo convidado

Enquanto `activeVehicleIsGuestFlow` é `true`, `MainContainerScreen` oculta as
abas Histórico e Eventos (ambas dependem de listar dados que o backend nega
ao convidado — 403 imediato) e mantém Postos (não depende do veículo) e
Perfil (conta do próprio convidado, inclui o caminho de volta pro picker). A
aba Home é substituída por uma tela nova, `GuestVehicleScreen`
(`vehicle/guest/{vehicleId}`), com:
- Cabeçalho com marca/modelo do veículo (do `VehicleShare` já carregado no
  picker, passado como argumento de navegação) e "Emprestado até dd/mm".
- Campo numérico inline + botão "Atualizar odômetro" — chama
  `PUT /vehicles/{id}/odometer` diretamente, sem depender de conhecer o km
  atual (o convidado não pode buscar isso; a validação "não pode ser menor
  que o atual" já existe no backend e a mensagem de erro é repassada pelo
  `userMessage()` padrão). Não reaproveita `UpdateOdometerScreen` (que exige
  `currentKm` como argumento de navegação vindo de `VehicleDetailsScreen`,
  inacessível ao convidado) — é uma tela nova e mais simples.
- Botão "Registrar abastecimento/despesa" → navega pra
  `CreateVehicleEventScreen` com um novo argumento de rota opcional
  `guestMode=true`, que restringe o seletor de categoria às 4 permitidas
  (`FUEL`, `CAR_WASH`, `TIRES`, `OTHER`) — evita que o convidado monte um
  request que o backend vai rejeitar com 403.
- Botão secundário "Trocar de veículo" → `VehiclePickerScreen`.

**Tratamento de 403 durante a sessão do convidado:** se `PUT
.../odometer` ou `POST /vehicle-events` retornar 403 enquanto o veículo
emprestado está ativo (prazo expirou ou o dono revogou no meio da sessão),
a tela mostra o erro e, ao fechar o snackbar, chama
`sessionStore.clearActiveVehicleId()` e navega de volta pro
`VehiclePickerScreen` — o veículo já não vai mais aparecer lá na próxima
carga (`active-for-me` não vai mais devolvê-lo).

### 5. Dono: convidar e gerenciar o compartilhamento

Nova entrada em `VehicleDetailsScreen`: item de menu "Compartilhar veículo"
→ navega pra `ShareVehicleScreen` (`vehicle/share/{vehicleId}`, nova tela).
Ao abrir, chama `GetVehicleShareForVehicleUseCase` e renderiza 3 estados:
- **Nenhum share:** formulário com campo de email + seletor de duração (1 /
  3 / 7 / 14 / 30 dias) + botão "Enviar convite" → `InviteVehicleShareUseCase`.
  Erros do backend (email não cadastrado → 404, convite pra si mesmo → 400,
  já existe share pendente/ativo → 409) são mapeados pelo `AppError`
  existente e mostrados via snackbar.
- **`PENDING`:** "Convite enviado para `{guestEmail}`, aguardando resposta"
  + botão "Cancelar convite" → `RevokeVehicleShareUseCase`.
- **`ACTIVE`:** "Compartilhado com `{guestName}` até `{expiresAt}`" + botão
  "Encerrar compartilhamento" → `RevokeVehicleShareUseCase`.

### 6. Convidado: aceitar/recusar convite

Nova tela `ShareInviteScreen` (`vehicle-share/{shareId}`) — a rota bate
exatamente com o deep link que o backend envia no payload do push
(`flowfuel://vehicle-share/{shareId}`), então o mecanismo de deep link
genérico que `FlowFuelNavHost` já trata (qualquer `flowfuel://<rota>` quando
`start == StartDestination.Home`) funciona sem nenhuma mudança nesse
mecanismo — só é preciso registrar a rota nova em `Destinations.kt` e no
`NavHost`.

A tela mostra marca/modelo do veículo, nome do dono, duração, e dois botões
Aceitar/Recusar (`AcceptVehicleShareUseCase`/`RejectVehicleShareUseCase`).
Ao aceitar, fecha a tela — o veículo passa a aparecer na próxima carga do
picker via `active-for-me`.

**Caminho alternativo sem push:** como a permissão de notificação é opcional
(dialog de rationale, pode ser negada — ver
[[project_push_notification_frontend]]), depender só do push deixaria um
convidado que negou a permissão sem NENHUMA forma de ver o convite. A tela de
Perfil ganha uma linha condicional "Convites de veículo pendentes (N)"
(visível só quando `GetPendingVehicleSharesUseCase` retorna lista não-vazia,
checado ao abrir o Perfil) que leva a uma lista simples de convites
pendentes, cada um navegando pra `ShareInviteScreen`.

## Arquivos afetados

```
app/src/main/java/com/flowfuel/app/core/vehicleshare/ (novo pacote)
  data/remote/VehicleShareApi.kt
  data/VehicleShareRepositoryImpl.kt
  domain/VehicleShareRepository.kt
  domain/model/VehicleShare.kt
  domain/usecase/InviteVehicleShareUseCase.kt
  domain/usecase/AcceptVehicleShareUseCase.kt
  domain/usecase/RejectVehicleShareUseCase.kt
  domain/usecase/RevokeVehicleShareUseCase.kt
  domain/usecase/GetVehicleShareForVehicleUseCase.kt
  domain/usecase/GetPendingVehicleSharesUseCase.kt
  domain/usecase/GetActiveSharedVehiclesUseCase.kt
  di/VehicleShareModule.kt

app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/share/ (novo)
  ShareVehicleScreen.kt + ShareVehicleViewModel.kt (dono)
  ShareInviteScreen.kt + ShareInviteViewModel.kt (convidado)

app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/guest/ (novo)
  GuestVehicleScreen.kt + GuestVehicleViewModel.kt

app/src/main/java/com/flowfuel/app/core/datastore/SessionStore.kt
  (ACTIVE_VEHICLE_IS_GUEST, activeVehicleIsGuestFlow, saveActiveVehicleId(isGuest))

app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/list/VehiclePickerViewModel.kt
  (VehiclePickerItem, merge com GetActiveSharedVehiclesUseCase, SetActiveGuestVehicleUseCase)
app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/list/VehiclePickerScreen.kt
  (renderização do card "Borrowed")
app/src/main/java/com/flowfuel/app/feature/vehicle/domain/usecase/SetActiveGuestVehicleUseCase.kt (novo)

app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/details/VehicleDetailsScreen.kt
  (item de menu "Compartilhar veículo")

app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/create/CreateVehicleEventScreen.kt
  (argumento guestMode, filtro de categoria)

app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileScreen.kt
  (linha condicional de convites pendentes)
app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileViewModel.kt
  (GetPendingVehicleSharesUseCase)

app/src/main/java/com/flowfuel/app/navigation/Destinations.kt
  (VEHICLE_SHARE, VEHICLE_SHARE_INVITE, VEHICLE_GUEST_HOME)
app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt
  (registro das 3 rotas novas)
app/src/main/java/com/flowfuel/app/navigation/MainContainerScreen.kt
  (abas condicionais por activeVehicleIsGuestFlow, aba Home → GuestVehicleScreen)
```

## Testes

- **Unitário (ViewModel/data):** `VehicleShareRepositoryImpl` (mapeamento
  DTO→domínio, `apiCall` nos 7 endpoints); `VehiclePickerViewModel` (merge de
  `Owned`/`Borrowed`, seleção bifurcando `SetActiveVehicleUseCase` vs
  `SetActiveGuestVehicleUseCase`); `ShareVehicleViewModel` (3 estados:
  nenhum/`PENDING`/`ACTIVE`, mapeamento de erros 404/400/409);
  `ShareInviteViewModel` (aceitar/recusar); `GuestVehicleViewModel`
  (atualizar odômetro, criar evento com categoria restrita, tratamento de
  403 → limpa sessão de convidado e sinaliza navegação de volta ao picker).
- **Manual (ponta a ponta, exige backend com `flowfuel.push.enabled=true`
  em staging):** convidar por email → push chega no convidado → aceitar →
  veículo aparece no picker do convidado com tag "Emprestado" → trocar pra
  ele → bottom nav restrita (sem Histórico/Eventos) → atualizar odômetro →
  registrar abastecimento → categoria não permitida não aparece no seletor
  → dono revoga → próxima ação do convidado no veículo dá erro e volta pro
  picker → convite recusado não deixa o veículo pendurado (dono pode
  convidar de novo).

## Critérios de Aceitação

- Dono vê "Compartilhar veículo" em `VehicleDetailsScreen`, envia convite
  por email, e a tela reflete o estado (`PENDING`/`ACTIVE`) sem precisar sair
  e voltar.
- Convidado recebe push, toca, cai direto na tela de aceitar/recusar
  (`flowfuel://vehicle-share/{id}` → `ShareInviteScreen`) sem passar por
  nenhuma outra tela.
- Convidado que negou permissão de notificação ainda consegue achar o
  convite pendente pelo Perfil.
- Após aceitar, o veículo aparece em `VehiclePickerScreen` com tag
  "Emprestado" e data de expiração, junto com os veículos próprios.
- Selecionar o veículo emprestado não chama `PUT /vehicles/{id}/active`
  (só grava local) e leva pra `GuestVehicleScreen`, com bottom nav sem
  Histórico/Eventos.
- Convidado consegue atualizar odômetro e lançar evento de categoria
  permitida sem nunca precisar que o app busque o veículo completo.
- Seletor de categoria em modo convidado só mostra `FUEL`/`CAR_WASH`/
  `TIRES`/`OTHER`.
- Revogação pelo dono (ou expiração do prazo) faz a próxima ação do
  convidado falhar com uma mensagem clara e devolvê-lo ao picker, sem crash
  nem estado travado.
- Trocar para um veículo próprio a partir do modo convidado restaura a
  bottom nav completa normalmente.
