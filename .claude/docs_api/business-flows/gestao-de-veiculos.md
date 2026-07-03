# Gestão de Veículos

> Fonte: `vehicle/VehicleController.java`, `vehicle/VehicleService.java`, `vehicle/Vehicle.java`

## Objetivo de Negócio

Permitir que cada usuário cadastre e gerencie a sua frota de veículos (combustão, elétricos ou híbridos), mantendo sempre um único "veículo ativo" para uso como contexto padrão no app (ex.: tela inicial de abastecimento).

## Atores

- **Usuário final (dono)** — cria, edita, ativa e exclui seus veículos.
- **Sistema (VehicleService)** — aplica validações e garante regra de veículo ativo único.

## Fluxo: Cadastro de Veículo (`POST /vehicles`)

**Passos principais:**
1. Usuário informa `type` (texto livre), `energyType` (COMBUSTION/ELECTRIC/HYBRID), `currentKm` (>= 0) e `capacity` (>= 1, litros do tanque).
2. Campos opcionais: marca, modelo, subtipo de combustível, capacidade de bateria, ano de fabricação/modelo (entre 1886 e 2100), cor, placa.
3. Veículo é vinculado ao usuário autenticado.

**Pós-condições:** Novo veículo disponível na lista do usuário; não é automaticamente definido como ativo. `[INFERIDO — confirmar regra de ativação automática do primeiro veículo cadastrado]`

## Fluxo: Veículo Ativo (`GET/PUT /vehicles/active`, `/vehicles/{id}/active`)

**Regra de negócio central:** exatamente **um único veículo por usuário** pode estar marcado como ativo (`isActive = true`) a qualquer momento.

**Passos principais (definir ativo):**
1. Usuário escolhe um veículo (`PUT /vehicles/{id}/active`).
2. Sistema desativa todos os demais veículos do usuário e ativa apenas o escolhido (operação atômica do tipo "exclusive switch").

**Caminhos alternativos:**
- Consulta de veículo ativo (`GET /vehicles/active`) quando nenhum veículo está marcado como ativo → erro de recurso não encontrado.

## Fluxo: Atualizar Odômetro (`PUT /vehicles/{id}/odometer`)

**Regra de negócio:** o odômetro é **monotonicamente crescente** — não pode ser definido para um valor menor que o atual.

**Caminhos alternativos / exceções de negócio:**
- `currentKm` informado menor que o valor atual do veículo → erro de regra de negócio ("Odômetro não pode ser menor que o atual").

## Fluxo: Exclusão de Veículo (`DELETE /vehicles/{id}`)

**Passos principais:**
1. Sistema confirma que o usuário autenticado é o dono do veículo.
2. Veículo é excluído.
3. **Cascade em nível de banco:** todos os abastecimentos e eventos vinculados ao veículo são excluídos junto (`@OnDelete(CASCADE)`).

**Pós-condições:** Histórico de abastecimentos e eventos do veículo é permanentemente perdido — operação irreversível.

## Diagrama (Troca de veículo ativo)

```mermaid
flowchart TD
    A[Usuário possui N veículos] --> B[PUT /vehicles/{id}/active]
    B --> C[Sistema busca todos os veículos do usuário]
    C --> D[Para cada veículo: isActive = (veiculo.id == id escolhido)]
    D --> E[Apenas o veículo escolhido fica ativo]
```

## Pontos de Atenção

- Não está claro no código se o primeiro veículo cadastrado por um usuário é automaticamente marcado como ativo, ou se o usuário precisa fazer essa marcação manualmente. `[INFERIDO — confirmar com time]`
- A exclusão de veículo é irreversível e remove todo o histórico de abastecimentos/eventos sem aviso explícito de "ação destrutiva" documentado no nível de API.
