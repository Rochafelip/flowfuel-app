# Design: Endereço (rua e número) no card de Postos

## Contexto

O backend (`flowfuel` API, `GET /stations/nearby`) passou a retornar dois
campos novos em `StationResponseDTO`: `street` e `houseNumber`
(`String?` ambos, `null` quando a fonte de dado — OSM Overpass para postos
de combustível, Open Charge Map para recarga elétrica — não tem o dado
preenchido). Isso foi verificado com chamadas reais contra Overpass: parte
dos postos retornados vem com `street`/`houseNumber` preenchidos (ex.:
"Avenida Alfredo Lisboa" / "173"), parte vem com os dois `null` — é o dado
real da fonte, não um bug a corrigir no app.

Para Open Charge Map (tipo `ELECTRIC`), a API de origem não separa rua de
número — tudo cai em `AddressLine1`, mapeado só para `street`;
`houseNumber` é sempre `null` nesse tipo.

A spec anterior do card (`2026-07-02-postos-filtro-tipo-card-compacto-design.md`,
seção "Fora") registrou endereço como fora de escopo porque "nenhum desses
dados existe nas fontes atuais". Isso mudou — este documento cobre
especificamente a exibição desses dois campos novos no `StationCard` já
existente, sem reabrir o resto do redesign compacto feito naquela rodada.

## Objetivo

Exibir rua e número (quando disponíveis) no `StationCard` da tela
"Postos" (`StationsScreen`), como uma segunda linha de texto abaixo do
nome, sem quebrar o layout compacto já entregue e sem alterar a altura do
card nos casos em que o endereço vem `null`.

## Escopo

### Dentro

- Dois campos novos, opcionais, em `StationResponseDto`
  (`data/remote/StationApi.kt`): `street: String? = null`,
  `houseNumber: String? = null`.
- Dois campos novos, opcionais, no domínio `Station`
  (`domain/model/Station.kt`): `street: String?`, `houseNumber: String?`,
  mapeados 1:1 em `StationRepositoryImpl.toDomain()`.
- `StationCard.kt`: nova linha de endereço, renderizada só quando há algo
  para mostrar (`street != null`), entre a linha do nome/distância e a
  linha de rating/rota.
- Função de formatação `formatAddress(street: String?, houseNumber: String?): String?`
  (mesmo arquivo `StationCard.kt`, ao lado de `formatDistance`/
  `formatRating`), retornando `null` quando não há nada a exibir.
- Testes unitários de `formatAddress` e do novo comportamento condicional
  do card.

### Fora (não mexer nesta rodada)

- Qualquer UI de edição/correção de endereço pelo usuário (crowdsourcing).
- Geocodificação reversa client-side para preencher endereço quando a API
  retorna `null` — se a fonte não tem o dado, o app não inventa.
- Mudanças no filtro de tipo, badge, rating ou botão de rota — já cobertos
  pela spec anterior, sem alteração aqui.
- Exibir endereço em outro lugar do app (ex.: notificação, prefetch) — só
  o `StationCard` da tela Postos.
- Separar `street` de `houseNumber` para estações elétricas — a fonte
  (Open Charge Map) não fornece essa granularidade; `houseNumber` fica
  sempre `null` para `StationType.Electric`, o que é esperado.

## Arquitetura de dados

### `StationResponseDto` (`data/remote/StationApi.kt`)

```kotlin
@Serializable
data class StationResponseDto(
    val placeId: String,
    val name: String,
    val type: String,
    val distanceMeters: Int,
    val rating: Double? = null,
    val latitude: Double,
    val longitude: Double,
    val street: String? = null,
    val houseNumber: String? = null,
)
```

Campos opcionais com default `null`: contrato antigo (payloads em cache ou
mocks de teste que não têm esses campos) continua desserializando sem
quebrar.

### `Station` (`domain/model/Station.kt`)

```kotlin
data class Station(
    val placeId: String,
    val name: String,
    val type: StationType,
    val distanceMeters: Int,
    val rating: Double?,
    val latitude: Double,
    val longitude: Double,
    val street: String?,
    val houseNumber: String?,
)
```

### `StationRepositoryImpl.toDomain()`

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

Mapeamento direto, sem transformação — igual aos demais campos.

## UI: `StationCard`

### `formatAddress` (novo, em `StationCard.kt`)

```kotlin
internal fun formatAddress(street: String?, houseNumber: String?): String? = when {
    street.isNullOrBlank() -> null
    houseNumber.isNullOrBlank() -> street
    else -> "$street, $houseNumber"
}
```

- `street` nulo/vazio → `null` (não mostra nada, independente de
  `houseNumber` — número sem rua não é um endereço útil).
- `street` presente + `houseNumber` nulo/vazio → só a rua (caso comum do
  OSM: rua tageada, número não).
- Ambos presentes → `"rua, número"`.

### Layout do card

Nova linha entre a `Row` do nome/distância e a `Row` de rating/rota:

```kotlin
Row(/* nome + distância, como hoje */)
val address = remember(station.street, station.houseNumber) {
    formatAddress(station.street, station.houseNumber)
}
if (address != null) {
    Spacer(Modifier.height(FFTheme.spacing.xxs))
    Text(
        text = address,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
Spacer(Modifier.height(FFTheme.spacing.xs))
Row(/* rating + botão de rota, como hoje */)
```

- Renderização condicional (`if (address != null)`): cards sem endereço na
  fonte mantêm a altura atual (2 linhas), sem espaço vazio reservado —
  evita lista com alturas inconsistentes por padding fantasma.
- `bodySmall` + `onSurfaceVariant`: mesmo padrão de hierarquia visual já
  usado para texto secundário no card (distância usa `labelMedium`/
  `onSurfaceVariant`); endereço usa um nível abaixo por ser informação
  auxiliar, não a usada para decisão rápida (nome/distância continuam
  sendo o foco).
- `maxLines = 1` + `Ellipsis`: ruas longas ("Avenida Norte Miguel Arraes de
  Alencar, 1002") não devem forçar o card a crescer verticalmente.
- Sem ícone dedicado (ex. `Icons.Filled.Place`) — mantém a densidade visual
  do redesign compacto anterior; texto puro já é suficiente como linha
  secundária.

## Testes

Seguindo o padrão existente em `StationCardTest.kt` (JUnit puro, sem
Compose test rule — as funções de formatação são testadas isoladas):

- `formatAddress`:
  - `street` e `houseNumber` presentes → `"Avenida Alfredo Lisboa, 173"`.
  - `street` presente, `houseNumber` nulo → `"Avenida Conde da Boa Vista"`.
  - `street` nulo, `houseNumber` presente → `null` (número sozinho não
    forma endereço).
  - `street` e `houseNumber` nulos → `null`.
  - `street` em branco (`""`) tratado como ausente → `null`.
- `StationRepositoryImplTest`: `toDomain()` propaga `street`/`houseNumber`
  do DTO para o domínio sem transformação, incluindo o caso `null` (não
  quebra quando a API antiga/mock não envia os campos).
- Teste de card (se `StationCardTest` ganhar suporte a Compose test rule
  nesta rodada, senão registrar como TODO de implementação): linha de
  endereço não aparece quando `station.street == null`; aparece com o
  texto formatado quando presente.

## Auto-revisão do spec

- **Sem placeholders:** assinatura de `formatAddress`, alteração de DTO/
  domínio/repositório e posição exata da nova linha no card estão todas
  explícitas.
- **Consistência interna:** o comportamento "número sem rua não conta como
  endereço" é único e aplicado de forma consistente entre a função de
  formatação e os casos de teste.
- **Escopo:** mudança pontual e coesa (3 arquivos de produção + mapeamento
  + 1 função de formatação), sem tocar em filtro, badge, rating ou rota —
  não precisa de decomposição adicional.
- **Ambiguidade:** resolvido explicitamente o caso `ELECTRIC` sempre sem
  `houseNumber` (limitação da fonte, não do app) e o caso de renderização
  condicional para não alterar a altura do card quando não há endereço.
