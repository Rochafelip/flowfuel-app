# FFBorrowedVehicleCard: adicionar avatar de veículo

**Data:** 2026-07-23
**Status:** Aprovado, aguardando plano de implementação

## Contexto

`FFBorrowedVehicleCard` (`core/designsystem/components/FFBorrowedVehicleCard.kt`,
ver [[project_vehicleshare_module]] e [[project_vehicles_module]]) é o card
usado para representar um veículo compartilhado (emprestado) tanto em
`VehiclePickerScreen` quanto em `VehiclesScreen` ("Meus Veículos"). Hoje ele
é só texto: `Row(título + badge "Emprestado")` seguido de
`Text("até {data de expiração}")`, dentro de um `FFCard`.

Isso destoa visualmente do card de veículo próprio, `FFVehicleCard`
(mesmo pacote), que usa um `VehiclePhotoAvatar` (ícone ou foto do veículo,
48.dp) ao lado do bloco de texto. Testado manualmente no emulador em
2026-07-23 (conta `rocha.felipe98@gmail.com` como convidado, veículo
"Toyota Corolla" compartilhado por `yhe66@web-library.net`): o card
emprestado fica visualmente pobre por comparação, sem nenhuma identidade
visual de veículo — só texto puro.

## Requisitos

1. `FFBorrowedVehicleCard` passa a exibir um `VehiclePhotoAvatar` à esquerda
   do bloco de texto, no mesmo tamanho (48.dp) e com o mesmo espaçamento
   (`FFTheme.spacing.md` entre avatar e coluna de texto) usados por
   `FFVehicleCard`, para dar paridade visual entre os dois cards.
2. O layout do bloco de texto (título + badge "Emprestado" na primeira
   linha, "até {data}" na segunda) não muda — só ganha o avatar ao lado.
3. O avatar sempre usa o ícone genérico de carro: `VehiclePhotoAvatar(photoUrl
   = null, vehicleType = VehicleType.Car, size = 48.dp)`. `VehicleShare`
   (`core/vehicleshare/domain/model/VehicleShare.kt`) não carrega
   `vehicleType` nem `photoUrl` — o backend não retorna esses campos na
   resposta de compartilhamento. Mostrar sempre o ícone de carro, mesmo para
   motos ou elétricos emprestados, é uma limitação **aceita explicitamente**
   nesta rodada (decisão do usuário) — não é bug, é escopo.
4. Sem mudança de comportamento: `onClick`, badge, formatação de data e
   assinatura pública do composable (`share`, `modifier`, `onClick`)
   permanecem idênticos.

## Fora de escopo

- Buscar/exibir o tipo real do veículo ou foto real no card emprestado —
  exigiria o backend (`flowfuel` no WSL, repositório separado) incluir
  `vehicleType`/`photoUrl` na resposta de `VehicleShare`. Fica como
  possível follow-up futuro, não faz parte desta mudança.
- Exibir o nome do dono (`ownerName`) no card — descartado explicitamente
  nesta rodada; o campo já existe no modelo mas não é exibido.
- Qualquer mudança em `VehiclePickerScreen` ou `VehiclesScreen` além de
  herdarem automaticamente o novo visual por já consumirem
  `FFBorrowedVehicleCard`.

## Arquitetura

Mudança isolada em `core/designsystem/components/FFBorrowedVehicleCard.kt`:

- A `Column` interna do `FFCard` (hoje só o `Row(título+badge)` seguido do
  `Text(expiry)`) passa a ficar dentro de um `Row` externo:
  `Row(verticalAlignment = CenterVertically, horizontalArrangement =
  spacedBy(FFTheme.spacing.md)) { VehiclePhotoAvatar(...); Column { ... } }`
  — mesma estrutura de `FFVehicleCard` (linha 30-31 do arquivo), só que a
  `Column` interna tem uma única linha secundária (`Text(expiry)`) em vez de
  duas (`plate`, `odometerKm`).
- `VehiclePhotoAvatar` já existe e é importado por `FFVehicleCard.kt` no
  mesmo pacote `core.designsystem.components` — reaproveitado sem mudanças.
- Novos imports necessários em `FFBorrowedVehicleCard.kt`: `Column`,
  `com.flowfuel.app.feature.vehicle.domain.model.VehicleType` (para o
  parâmetro `vehicleType` do avatar).
- `VehiclePickerScreen` e `VehiclesScreen` não mudam — ambas já consomem
  `FFBorrowedVehicleCard` (ver [[project_vehicles_module]]), então herdam o
  novo visual automaticamente.

## Erros e casos de borda

- Nenhum novo — mudança é puramente visual/estrutural, sem novos dados,
  chamadas de rede ou estados.

## Testes

- Projeto não tem testes de UI Compose (`createComposeRule`) — padrão já
  estabelecido (ver [[project_architecture]]). Verificação: compilação
  forçada rodando a suíte de testes de `VehiclePickerViewModelTest` e
  `VehiclesViewModelTest` (força compilar `FFBorrowedVehicleCard.kt` e as
  duas telas que o consomem) + checagem visual manual no emulador
  (`VehiclesScreen`, seção "Compartilhados comigo").
