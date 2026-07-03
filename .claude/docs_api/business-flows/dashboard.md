# Dashboard de Estatísticas do Veículo

> Fonte: `dashboard/DashboardController.java`, `dashboard/DashboardService.java`

## Objetivo de Negócio

Apresentar ao usuário um resumo consolidado de custos e consumo de um veículo específico, calculado em tempo real a partir do histórico de abastecimentos.

## Atores

- **Usuário final (dono do veículo)** — consulta as estatísticas.
- **Sistema (DashboardService)** — agrega e calcula as métricas sob demanda (sem cache).

## Fluxo: Consultar Estatísticas (`GET /dashboard/vehicle/{vehicleId}`)

**Pré-condições:** Veículo pertence ao usuário autenticado.

**Passos principais (veículo não-híbrido: COMBUSTION ou ELECTRIC):**
1. Sistema calcula: `totalRefuels` (contagem), `totalSpent` (soma de `totalAmount`), `totalEnergy` (soma de `energyAmount`), `averagePrice` (média de `pricePerUnit`), `lastRefuelDate` e `lastOdometer` (do abastecimento mais recente).
2. Sistema calcula `averageConsumption` (ver algoritmo abaixo).
3. Unidades variam por tipo de energia: COMBUSTION → litros/R$ por litro/km por litro; ELECTRIC → kWh/R$ por kWh/km por kWh.

**Passos principais (veículo híbrido):**
1. Sistema calcula as métricas totais (contagem, gasto total, última data/odômetro) considerando todos os abastecimentos, independente do tipo.
2. Sistema gera também uma quebra (`breakdown`) separada por tipo de abastecimento — uma seção para `FUEL` e outra para `ELECTRIC` — cada uma com seu próprio total de energia, gasto, preço médio e consumo médio.

**Algoritmo de Consumo Médio (`averageConsumption`):**
1. Considera **apenas abastecimentos com `fullTank = true`** (tanque completo) — abastecimentos parciais são ignorados no cálculo de consumo.
2. Ordena os abastecimentos de tanque completo por data (mais recente primeiro) e forma pares consecutivos.
3. Para cada par (atual, anterior): `kmDriven = odometer(atual) - odometer(anterior)`; `energyUsed = energyAmount(atual)`. O par só é considerado se `kmDriven > 0` e `energyUsed > 0`.
4. Consumo final = soma de todos os `kmDriven` ÷ soma de todos os `energyUsed`, arredondado para 2 casas decimais (HALF_UP).
5. **Requer no mínimo 2 abastecimentos de tanque completo** — caso contrário, ou se a soma de energia usada for zero, o consumo retorna `0.0`.
6. O cálculo **ignora o campo `kmSinceLastRefuel`** salvo no abastecimento e recalcula a distância diretamente a partir dos odômetros, garantindo consistência mesmo se aquele campo estiver desatualizado (ver [Abastecimentos — Pontos de Atenção](abastecimentos.md)).

**Exemplo (do próprio código-fonte):**
3 abastecimentos de tanque completo C(3000km, 40L) → B(2200km, 35L) → A(1500km, 30L): pares (C-B): 800km/40L e (B-A): 700km/35L → consumo = (800+700)/(40+35) = 20,00 km/L.

**Pós-condições:** Nenhuma — consulta somente leitura, sem efeitos colaterais; sem cache, recalculado a cada chamada.

## Diagrama

```mermaid
flowchart TD
    A[GET /dashboard/vehicle/{id}] --> B{Veículo é híbrido?}
    B -- Não --> C[Calcula totais e consumo médio sobre todos os abastecimentos]
    B -- Sim --> D[Calcula totais gerais]
    D --> E[Calcula breakdown separado: FUEL e ELECTRIC]
    C --> F[Filtra fullTank=true, ordena por data desc]
    E --> F
    F --> G{>= 2 abastecimentos full tank?}
    G -- Não --> H[averageConsumption = 0.0]
    G -- Sim --> I[Soma kmDriven entre pares / soma energyUsed]
```

## Pontos de Atenção

- O consumo médio depende inteiramente do usuário marcar corretamente `fullTank = true` em seus abastecimentos; abastecimentos parciais não entram no cálculo e não há aviso ao usuário sobre essa exigência na própria API. `[INFERIDO — confirmar se a UX do app comunica essa regra claramente]`
- Ausência de cache implica recálculo completo a cada requisição — pode ter impacto de performance em veículos com histórico muito extenso de abastecimentos (ver também `M5-optimize-dashboard-service` no roadmap do projeto).
