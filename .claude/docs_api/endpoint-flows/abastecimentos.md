# Fluxo de Endpoints — Abastecimentos (Refuels)

> Fonte: `refuel/RefuelController.java`, `refuel/RefuelService.java`, `refuel/RefuelRepository.java`, `refuel/Refuel.java`, `vehicle/Vehicle.java`, `common/AuthorizationHelper.java`, `config/GlobalExceptionHandler.java`.

Controller: `@RequestMapping("/refuels")` (`RefuelController.java:15`). Todas as rotas exigem Bearer válido (não estão na lista de isenção do `JwtAuthenticationFilter` nem no `permitAll`). Sem rate limit. Nenhum método de `RefuelService`/`RefuelRepository` é `@Transactional` — cada chamada de repositório roda em transação própria, então gravações em múltiplos passos (ver `POST` abaixo) **não são atômicas**.

## `POST /refuels` — criar (regras de negócio mais densas do domínio)

```mermaid
sequenceDiagram
    actor Client
    participant API as RefuelController
    participant Svc as RefuelService
    participant VDB as vehicles (DB)
    participant RDB as refuels (DB)

    Client->>API: POST /refuels {vehicleId, odometer, energyAmount, pricePerUnit, refuelType?, fullTank?}
    API->>API: @Valid RefuelRequestDTO
    API->>Svc: createRefuel(user, dto)
    Svc->>VDB: findById(vehicleId) [404 se ausente]
    Svc->>Svc: ensureOwnsVehicle(user, vehicle) [403 se não dono]
    Svc->>RDB: findTopByVehicleIdOrderByOdometerDesc (lastOdometer, ou vehicle.currentKm se 1º abastecimento)
    alt odometer < lastOdometer
        Svc-->>API: BusinessRuleException 400 (odômetro retroativo)
    else
        Svc->>Svc: resolveRefuelType (default do veículo; híbrido sem tipo explícito → erro)
        Svc->>Svc: valida faixa de preço (ELECTRIC: 0.10–5.00 · demais: 0.50–15.00)
        Svc->>Svc: valida capacidade (energyAmount vs vehicle.effectiveCapacity)
        Svc->>RDB: save(refuel) — @PrePersist: totalAmount = energyAmount * pricePerUnit
        Svc->>VDB: save(vehicle.currentKm = odometer)
        Svc-->>API: RefuelResponseDTO
    end
    API-->>Client: 200
```

Fonte: `RefuelService.java:26-66`, fórmula confirmada em `Refuel.java:60-66` (`@PrePersist`/`@PreUpdate calculateTotalAmount`). **Efeito colateral confirmado:** todo `POST` bem-sucedido atualiza `vehicle.currentKm` incondicionalmente para o novo odômetro — único ponto do sistema que sincroniza o odômetro do veículo a partir de um abastecimento.

Regras de negócio, todas `BusinessRuleException` → 400 `BUSINESS_RULE_VIOLATED`:
- Odômetro informado menor que o último registrado (ou que `vehicle.currentKm`, se for o primeiro abastecimento).
- Veículo híbrido sem `refuelType` explícito no request (não há "tipo padrão" para híbridos — `vehicle.defaultRefuelType()` retorna `null`).
- `refuelType` resolvido incompatível com `energyType` do veículo.
- Preço por unidade fora da faixa esperada para o tipo (combustível vs elétrico).
- `energyAmount` maior que a capacidade efetiva do veículo (tanque ou bateria), quando essa capacidade está cadastrada.

## `GET /refuels/vehicle/{vehicleId}` — listar (filtro por data)

`RefuelService.getVehicleRefuels` (`:68-83`): `findOwned`-equivalente (busca veículo + `ensureOwnsVehicle`, 404/403). Filtro por intervalo de datas só é aplicado se **ambos** `startDate` e `endDate` forem enviados (`findByVehicleIdAndRefuelDateBetweenOrderByRefuelDateDesc`); enviar só um dos dois é ignorado silenciosamente e cai na listagem completa (`findByVehicleIdOrderByRefuelDateDesc`). `[descoberto na Fase 4 — comportamento não documentado em nenhum lugar do código]` Data malformada na query → `MethodArgumentTypeMismatchException` sem handler dedicado → 500.

## `GET /refuels/{id}` — detalhe

Ownership verificado **pelo próprio abastecimento**, não pelo veículo do path: `authorizationHelper.ensureOwnsRefuel(user, refuel)` (`AuthorizationHelper.java:19-23`) compara `refuel.getVehicle().getUser().getId()`.

## `PUT /refuels/{id}` — atualizar (parcial)

`RefuelService.updateRefuel` (`:92-131`): `findById` (404) + `ensureOwnsRefuel` (403). Campo `vehicleId` do corpo é deserializado mas **nunca usado** — o veículo de referência é sempre `refuel.getVehicle()` já persistido (não é possível "mover" um abastecimento para outro veículo via este endpoint).

- `odometer`: se enviado, recalcula contra o abastecimento imediatamente anterior por odômetro (`findTopByVehicleIdAndOdometerLessThanOrderByOdometerDesc`, ou `vehicle.currentKm` se não houver anterior) → mesma regra de não-retroatividade. Atualiza também `kmSinceLastRefuel`. **Não atualiza `vehicle.currentKm`** (diferente do `POST`) — assimetria de efeito colateral.
- `energyAmount`/`pricePerUnit`: revalidados (capacidade/faixa de preço) contra o `refuelType` já salvo — o tipo não pode ser alterado via update (não há `setRefuelType`).
- `totalAmount` é recalculado via `@PreUpdate` sempre que o registro é salvo.

**Atenção:** o DTO `RefuelRequestDTO` marca `vehicleId`/`odometer`/`energyAmount`/`pricePerUnit` como `@NotNull`, mas o service trata todos como opcionais (checa null antes de aplicar) — um `PUT` parcial que omita qualquer um desses campos falha a validação Bean (`400 VALIDATION_FAILED`) antes mesmo de chegar à lógica de atualização parcial, que nunca é exercitada para esses campos. `[descoberto na Fase 4 — inconsistência]`

## `DELETE /refuels/{id}`

`findById` (404) + `ensureOwnsRefuel` (403) + `deleteById`. **Não recalcula** `vehicle.currentKm` nem o `kmSinceLastRefuel` dos abastecimentos subsequentes — excluir um abastecimento no meio da sequência deixa os campos cacheados de outros registros inconsistentes. `[descoberto na Fase 4 — gap de consistência de dados]` Retorna `void` → `200` com corpo vazio.

## Tabela de Erros → Status HTTP

| Exceção | Onde | Status | Code |
|---|---|---|---|
| Bearer ausente/inválido | `JwtAuthenticationFilter` | 401 | `AUTH_REQUIRED`/`AUTH_TOKEN_INVALID` |
| `MethodArgumentNotValidException` | `@Valid` no body | 400 | `VALIDATION_FAILED` |
| JSON malformado | `HttpMessageNotReadableException` | 400 | `REQUEST_MALFORMED` |
| Enum/data inválida em query param | Spring MVC (sem handler dedicado) | 500 | `INTERNAL_ERROR` |
| Veículo/abastecimento não encontrado | `RefuelService` | 404 | `RESOURCE_NOT_FOUND` |
| Não é dono do veículo/abastecimento | `AuthorizationHelper` | 403 | `FORBIDDEN_OPERATION` |
| Odômetro retroativo, tipo/preço/capacidade inválidos | `RefuelService` (várias) | 400 | `BUSINESS_RULE_VIOLATED` |
| Erro inesperado | catch-all | 500 | `INTERNAL_ERROR` |

## Pontos de Atenção

- Criação de abastecimento não é atômica: `save(refuel)` e `save(vehicle)` (atualização de odômetro) são duas transações independentes — falha na segunda deixa o abastecimento gravado sem o odômetro do veículo sincronizado. `[descoberto na Fase 4]`
- Filtro por data exige **ambos** `startDate`/`endDate`; um único parâmetro é ignorado sem aviso. `[descoberto na Fase 4]`
- `PUT` atualiza `kmSinceLastRefuel` mas não `vehicle.currentKm`; `POST` faz o contrário (atualiza `vehicle.currentKm`, calcula `kmSinceLastRefuel` na criação) — comportamento assimétrico entre os dois fluxos. `[descoberto na Fase 4]`
- `DELETE` não recalcula `kmSinceLastRefuel` dos registros vizinhos — dado cacheado pode ficar inconsistente após exclusões no meio da série temporal. `[descoberto na Fase 4]`
- `@NotNull` no DTO de `PUT` contradiz a lógica de atualização parcial no service. `[descoberto na Fase 4]`
- Enum/data inválidos em parâmetros de query (`MethodArgumentTypeMismatchException`) não têm handler dedicado e resultam em `500` em vez de `400`. `[descoberto na Fase 4 — gap]`
