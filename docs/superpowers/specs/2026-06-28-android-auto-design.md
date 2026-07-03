# Android Auto — Design Spec

**Data:** 2026-06-28  
**Status:** Aprovado  
**Abordagem escolhida:** A — Car App Library adicionada ao módulo `:app` existente

---

## Escopo

Duas funcionalidades dentro do Android Auto:

1. **Dashboard** — exibir consumo médio, gasto total e último abastecimento do veículo ativo
2. **Registrar abastecimento** — formulário simplificado de 3 campos (km percorridos, litros, valor total)

Fora do escopo: login/logout, troca de veículo, histórico, gerenciamento de veículos, eventos do veículo.

---

## Dependência

```kotlin
// app/build.gradle.kts
implementation("androidx.car.app:app:1.4.0")
```

Compatível com a Car App Library API level 5 (Android Auto atualizado via Play Store, sem requisito de versão de SO do celular).

---

## Estrutura de packages

```
app/src/main/java/com/flowfuel/app/feature/auto/
  AutoCarAppService.kt          ← CarAppService; entry point declarado no Manifest
  AutoSession.kt                ← Session; decide tela inicial (auth check)
  dashboard/
    AutoDashboardScreen.kt      ← PaneTemplate com stats + botão "Registrar abastecimento"
  refuel/
    AutoRefuelStep1Screen.kt    ← InputTemplate: km percorridos
    AutoRefuelStep2Screen.kt    ← InputTemplate: litros abastecidos
    AutoRefuelStep3Screen.kt    ← InputTemplate: valor total (R$)
    AutoRefuelConfirmScreen.kt  ← MessageTemplate: resumo + ações Confirmar / Corrigir
    AutoRefuelSuccessScreen.kt  ← MessageTemplate: feedback de sucesso + volta ao painel
```

---

## Camadas reutilizadas (sem modificação)

| Classe | Origem | Uso no Auto |
|---|---|---|
| `GetActiveVehicleUseCase` | `feature/home/domain` | Dados do veículo ativo no Dashboard |
| `GetDashboardUseCase` | `feature/home/domain` | Stats do painel |
| `CreateRefuelUseCase` | `feature/home/domain` | Submissão do abastecimento |
| `SessionStore` | `core/datastore` | Verificação de autenticação e `activeVehicleId` |

O `AutoCarAppService` é anotado com `@AndroidEntryPoint` e recebe os UseCases via injeção de campo do Hilt — mesmo mecanismo usado em `Activity` e `Fragment`.

---

## Fluxo de telas

### Inicialização (`AutoSession`)

```
Abrir no Auto
  ├─► SessionStore sem token → MessageTemplate: "Abra o app FlowFuel no celular para fazer login"
  └─► Token presente → AutoDashboardScreen
```

### Dashboard (`PaneTemplate`)

Conteúdo exibido:
- Título: `"<Marca> <Modelo>"` + placa entre parênteses
- Row 1: Consumo médio (ex: `8,4 km/L`) ou `"—"` se sem dados
- Row 2: Gasto total (ex: `R$ 1.240,00`)
- Row 3: Último abastecimento (ex: `"15/06 • 42,0 L"`) ou `"Nenhum ainda"`
- Ação primária: **"Registrar abastecimento"** → empurra `AutoRefuelStep1Screen`

Durante carregamento: `MessageTemplate` com texto `"Carregando…"`.  
Em erro: `MessageTemplate` com botão `"Tentar novamente"`.

### Fluxo de abastecimento (multi-step)

Estado acumulado é passado via construtores — sem ViewModel compartilhado.

```
Step 1 — AutoRefuelStep1Screen
  InputTemplate, tipo numérico inteiro
  Label: "Km percorridos desde o último abastecimento"
  Hint: ex: "150"
  Validação: valor > 0
  ✓ → AutoRefuelStep2Screen(tripKm)

Step 2 — AutoRefuelStep2Screen(tripKm)
  InputTemplate, tipo decimal
  Label: "Litros abastecidos"
  Hint: ex: "45,5"
  Validação: valor > 0
  ✓ → AutoRefuelStep3Screen(tripKm, liters)

Step 3 — AutoRefuelStep3Screen(tripKm, liters)
  InputTemplate, tipo decimal
  Label: "Valor total pago (R$)"
  Hint: ex: "289,90"
  Validação: valor > 0
  ✓ → AutoRefuelConfirmScreen(tripKm, liters, totalPrice)

Confirm — AutoRefuelConfirmScreen(tripKm, liters, totalPrice)
  MessageTemplate com 3 linhas de resumo:
    "Percurso: 150 km"
    "Litros: 45,5 L"
    "Valor: R$ 289,90"
  Ação primária: "Confirmar" → chama CreateRefuelUseCase
  Ação secundária: "Corrigir" → screenManager.pop() até Step 1

  Durante envio: MessageTemplate "Registrando…"
  Em erro: MessageTemplate com erro + "Tentar novamente" / "Cancelar"
  ✓ → AutoRefuelSuccessScreen

Success — AutoRefuelSuccessScreen
  MessageTemplate: "Abastecimento registrado!"
  Ação: "Voltar ao painel" → screenManager.popToRoot()
```

### Cálculo do odômetro

```kotlin
val odometer = vehicle.currentKm.toDouble() + tripKm
```

Mesma lógica do modo percurso no celular. `refuelType` é inferido automaticamente com base no `energyType` do veículo:
- `"ELECTRIC"` → `"ELECTRIC"`
- Qualquer outro (combustão ou híbrido) → `"FUEL"`

Para veículos híbridos, o default `"FUEL"` é conservador e correto para o contexto do Auto (usuário parou num posto de gasolina).

---

## Autenticação e erros

**Sem autenticação:**  
`AutoSession` verifica o token de sessão no `SessionStore` (leitura do flow de token existente). Se ausente ou vazio, exibe `MessageTemplate` estático — sem botão de login (não permitido pelo Google no Auto).

**Token expirado durante uso (401):**  
Qualquer UseCase que retorne `AppError.Unauthorized` exibe `MessageTemplate`: *"Sessão expirada. Abra o FlowFuel no celular para entrar novamente."*

**Erros de rede:**  
`MessageTemplate` específico por tela, com botão "Tentar novamente" que chama `invalidate()` e reexecuta a operação.

**Validação de inputs:**  
Feita inline antes de avançar para a próxima tela — não existe conceito de "campo com erro" nos templates do Car App; a validação bloqueia o avanço e exibe `CarToast` com a mensagem de erro.

---

## Manifest

### `AndroidManifest.xml` — adições

```xml
<!-- Dentro de <application> -->
<service
    android:name=".feature.auto.AutoCarAppService"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.car.app.CarAppService" />
        <category android:name="androidx.car.app.category.IOT" />
    </intent-filter>
</service>

<meta-data
    android:name="com.google.android.gms.car.application"
    android:resource="@xml/automotive_app_desc" />
```

### `res/xml/automotive_app_desc.xml` — arquivo novo

```xml
<?xml version="1.0" encoding="utf-8"?>
<automotiveApp>
    <uses name="template" />
</automotiveApp>
```

**Categoria `IOT`:** categoria catch-all do Google para apps que não se enquadram em Navigation, Parking ou Messaging. Requer revisão manual do Google Play antes da publicação — sem impacto no desenvolvimento local.

---

## Testes

- Testar com `TestCarContext` da Car App Library (não requer emulador nem carro físico)
- Casos prioritários:
  - `AutoSession` redireciona para mensagem de login quando sem token
  - `AutoDashboardScreen` exibe dados corretamente e estado de loading/erro
  - `AutoRefuelConfirmScreen` calcula `odometer = currentKm + tripKm` corretamente
  - Validação de campos bloqueia avanço para próxima tela com input inválido
  - Erro 401 durante envio exibe mensagem de sessão expirada
- Os UseCases existentes já têm cobertura unitária; não duplicar esses testes no módulo Auto

---

## O que este design não cobre (fora do escopo)

- Suporte a Automotive OS (Android Automotive — sistema embarcado em carros sem celular)
- Troca de veículo ativo pelo painel do carro
- Histórico de abastecimentos no Auto
- Notificações push via Auto
- Modo offline / cache local para uso sem conexão
