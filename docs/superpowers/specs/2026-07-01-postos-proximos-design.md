# Design: Postos Próximos (combustível + recarga elétrica)

## Contexto

Hoje o app não tem nenhuma feature de busca de postos de combustível ou
estações de recarga elétrica. O projeto já modela veículos com `EnergyType`
(`Combustion`, `Electric`, `Hybrid`) em `VehicleModels.kt`, mas nada na tela
de navegação principal ajuda o usuário a encontrar onde abastecer/recarregar.

## Objetivo

Tela "Postos" que busca postos de combustível e estações de recarga elétrica
próximos da localização atual do usuário, numa lista única ordenada por
distância, com nome, distância, nota (quando disponível) e um botão para
traçar rota até o local.

## Escopo

### Dentro do MVP

- Lista combinada (combustível + elétrico) via fontes de dados abertas e
  gratuitas (OpenStreetMap Overpass API + Open Charge Map API), com ícone
  diferenciando o tipo (⛽ / 🔌)
- Distância calculada a partir da localização atual do usuário
- Nota (rating), exibida somente quando a fonte de dados fornecer (raro em
  OSM/Open Charge Map — campo opcional, não bloqueia o MVP)
- Botão "Traçar rota" → dispara intent `google.navigation:q=lat,lng`, deixando
  o Android mostrar o seletor de apps de navegação instalados (Waze, Google
  Maps, etc.)
- Nova aba "Postos" na bottom nav, no lugar de "Veículos"
- Gestão de veículos (lista completa, editar, detalhes, histórico) migra para
  um item "Meus veículos" na aba Perfil

### Fora do escopo (iteração futura)

- Preço de combustível (nenhuma das fontes de dados escolhidas fornece esse
  dado)
- "Ver detalhes" do posto (tela extra ou expansão do card)
- Reportar preço manualmente / crowdsourcing de preços

## Navegação

- **Bottom nav (`FFBottomBar`)**: passa a ter Home, Histórico, **Postos**
  (nova, ícone `LocalGasStation`/`EvStation`), Eventos, Perfil — substituindo
  o item "Veículos" atual (`MainContainerScreen.kt`).
- **Perfil**: novo `ProfileActionRow` "Meus veículos" (ícone `DirectionsCar`),
  inserido antes de "Editar perfil", navegando para a `VehiclesScreen`
  existente sem alterações internas (lista, editar, detalhes, histórico por
  veículo continuam iguais).
- `VehiclesScreen` precisa virar também uma rota secundária no
  `FlowFuelNavHost` raiz (mesmo padrão já usado para `VEHICLE_EVENTS`,
  `EDIT_VEHICLE`), para poder ser empilhada a partir do Perfil com botão de
  voltar.
- `VehicleSwitcherBottomSheet` da Home (trocar veículo ativo, adicionar
  veículo) não muda.

## Arquitetura de dados

Novo módulo de feature `feature/station`, seguindo o padrão dos módulos
existentes (`domain/model`, `domain/usecase`, `data/remote`,
`presentation/list`, `di`).

Fluxo:

1. App obtém localização atual via `FusedLocationProviderClient` (nova
   dependência `play-services-location`, ainda não usada no projeto).
2. `StationRepository.getNearbyStations(lat, lng)` chama um **novo endpoint
   no backend próprio do FlowFuel**: `GET /stations/nearby?lat=&lng=&radius=`.
3. O **backend** (fora do escopo deste repo Android) busca os dois tipos de
   local em fontes de dados abertas e gratuitas, sem custo por chamada:
   - Postos de combustível: **OpenStreetMap via Overpass API**
     (`amenity=fuel` num raio/bounding box a partir de `lat/lng`).
   - Estações de recarga elétrica: **Open Charge Map API**.
   O backend mescla os dois resultados, calcula distância e devolve ao app
   já ordenado por distância — sem preço (nenhuma das duas fontes fornece).
4. O app mapeia a resposta para o domain model `Station`: `id/placeId`,
   `name`, `type` (Fuel/Electric), `distanceMeters`, `rating: Double?`, `lat`,
   `lng`. `rating` normalmente vem `null` (nem OSM nem Open Charge Map têm
   nota confiável equivalente à do Google) — a UI já trata isso como campo
   opcional.
5. `lat`/`lng` são usados para montar a URI do intent de rota, sem o app
   precisar acessar Overpass/Open Charge Map diretamente.

DI: `StationModule` com `@Binds StationRepository`, seguindo o padrão Hilt já
usado em `VehicleModule`/`VehicleEventModule`.

**Dependência externa:** este design assume um novo endpoint no backend
(`GET /stations/nearby`), que não faz parte deste repositório e precisa ser
implementado separadamente.

**Por que não Google Places:** decisão tomada em 2026-07-01 para evitar custo
recorrente (Places cobra por chamada de Nearby Search após uma cota gratuita
mensal por SKU, e o fluxo faz 2 chamadas por busca) e a necessidade de
cadastrar billing/cartão de crédito no Google Cloud só para um MVP. Overpass
e Open Charge Map são gratuitas e não exigem conta de billing. Trade-off
aceito: cobertura/qualidade de dados menor que a base comercial do Google
(sem nota confiável, possíveis buracos de cobertura em cidades menores) — ok
para o escopo do MVP, que já não usa preço nem "ver detalhes".

## Segurança e confiabilidade

- Nenhuma chamada a serviço externo é feita diretamente pelo app Android —
  tudo passa pelo endpoint próprio `GET /stations/nearby`, mesmo as fontes
  sendo públicas e sem key obrigatória, para manter o app desacoplado da
  fonte de dados (troca futura de provider não exige alterar o app).
- Open Charge Map tem key opcional (aumenta rate limit); se usada, essa key
  fica no backend, nunca no app.
- Overpass: os servidores públicos (`overpass-api.de` e espelhos) têm
  política de uso justo e podem throttlar sob alto volume — para produção,
  considerar rodar uma instância própria do Overpass (imagem Docker oficial)
  em vez de depender só do servidor público. Isso é uma decisão de
  infraestrutura do backend, não afeta o contrato com o app.

## Tela e componentes de UI

`StationsScreen` (rota `MainDestinations.STATIONS`, substitui `VEHICLES` na
bottom nav) com `StationsViewModel` seguindo o padrão de estado já
estabelecido no projeto:

```kotlin
sealed interface StationsUiState {
    object Loading : StationsUiState
    data class Success(val stations: List<Station>) : StationsUiState
    object Empty : StationsUiState
    data class Error(val message: String) : StationsUiState
    object PermissionRequired : StationsUiState
}
```

Layout:

- `FFTopBar` com título "Postos próximos"
- `LazyColumn` de cards, reaproveitando o estilo visual de
  `FFVehicleCard`/cards de eventos:
  - Ícone por tipo: `LocalGasStation` (combustível) / `EvStation` (elétrico)
  - Nome do posto
  - Distância formatada (`420 m` / `1,2 km`)
  - Nota com estrela, somente quando `rating != null`
  - Botão único "Traçar rota" (`FFButton` outlined)
- Sem paginação infinita — Places Nearby Search já limita a ~20 resultados
  por chamada, suficiente para o MVP
- Pull-to-refresh para atualizar lista/localização

## Permissão de localização e tratamento de erros

Fluxo de permissão (`ACCESS_FINE_LOCATION`, nova entrada no manifest):

1. Ao abrir a aba Postos, `StationsViewModel` verifica se a permissão já foi
   concedida.
2. Se não: `StationsUiState.PermissionRequired` → `FFEmptyState` explicando
   por que a localização é necessária, com botão "Permitir acesso à
   localização" que dispara o request do sistema.
3. Se negado permanentemente: mesmo estado, mas o botão leva às configurações
   do app (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).
4. Se concedido: busca localização atual → chama `getNearbyStations`.

Erros tratados:

- Sem permissão → `PermissionRequired`
- Sem GPS/localização indisponível → `Error("Não foi possível obter sua
  localização")` com botão "Tentar novamente"
- Falha de rede/backend → `Error`, reaproveitando `AppError` (`Api`,
  `Network`, `Unauthorized`, `Unknown`) e `userMessage()` já padronizados no
  projeto
- Nenhum posto encontrado no raio de busca → `Empty` ("Nenhum posto
  encontrado por perto")
- Nenhum app de navegação instalado ao clicar "Traçar rota" → `try/catch` em
  `ActivityNotFoundException`, snackbar "Nenhum app de navegação instalado"

## Testes

Testes unitários de `StationsViewModel` (padrão `VehicleEventsViewModelTest`),
com fakes/mocks de `StationRepository` e um `FakeLocationProvider`:

- Loading → Success com lista ordenada por distância
- Empty quando repository retorna lista vazia
- Error quando repository falha (mapeamento de `AppError`)
- PermissionRequired quando localização não concedida
- Clique em "Traçar rota" dispara o efeito de navegação externa com a URI
  correta (`lat,lng`)
- Pull-to-refresh reexecuta a busca

Sem testes de UI/Compose (não é padrão hoje no projeto) e sem testes de
backend (fora do escopo Android).
