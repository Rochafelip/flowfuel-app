# Foto do veículo nos cards de lista — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir o ícone de carro fixo em `FFVehicleCard` (usado em Meus Veículos, no seletor de veículo e no bottom sheet de troca rápida da Home) pela foto real do veículo, usando o componente `VehiclePhotoAvatar` que já existe e já é usado em `VehicleDetailsScreen`.

**Architecture:** `FFVehicleCard` ganha dois parâmetros opcionais (`photoUrl`, `vehicleType`) e delega a renderização do avatar para `VehiclePhotoAvatar`, que já implementa o padrão "ícone de fallback atrás, foto crossfade na frente". Os três call sites reais passam `vehicle.photoUrl`/`vehicle.type` a partir do domínio `Vehicle`, que já expõe esses campos desde a implementação anterior (`2026-07-03-vehicle-photo-display-edit`).

**Tech Stack:** Kotlin, Jetpack Compose, Coil (via `VehiclePhotoAvatar`, sem mudança nele).

## Global Constraints

- Não alterar `VehicleDetailsScreen`/`EditVehicleScreen` — já implementados e corretos (spec anterior).
- Não alterar paginação, cache, endpoints ou o domínio `Vehicle` — `photoUrl`/`type` já existem.
- `UiKitDemoScreen.kt` não muda — a chamada de showcase usa os defaults novos (`photoUrl = null`, `vehicleType = VehicleType.Car`), preservando o visual atual no catálogo.
- Sem novo teste automatizado de Compose — nem `FFVehicleCard` nem as telas que o consomem têm testes de UI hoje; verificação é manual (mesmo padrão do plano anterior).

---

### Task 1: `FFVehicleCard` — parâmetros de foto e delegação para `VehiclePhotoAvatar`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/core/designsystem/components/FFVehicleCard.kt`

**Interfaces:**
- Consumes: `VehiclePhotoAvatar(photoUrl: String?, vehicleType: VehicleType, modifier: Modifier = Modifier, size: Dp = 64.dp, onClick: (() -> Unit)? = null)` (já existe em `VehiclePhotoAvatar.kt:35-41`); `com.flowfuel.app.feature.vehicle.domain.model.VehicleType` (já existe em `VehicleModels.kt:3-6`).
- Produces: `FFVehicleCard(nickname: String, plate: String, odometerKm: String, isActive: Boolean, modifier: Modifier = Modifier, photoUrl: String? = null, vehicleType: VehicleType = VehicleType.Car, onClick: (() -> Unit)? = null)` — consumido pelas Tasks 2, 3 e 4.

Sem teste dedicado — `FFVehicleCard` não tem teste de Compose hoje (mesmo padrão do resto do design system).

- [ ] **Step 1: Substituir o corpo do arquivo**

Conteúdo completo de `FFVehicleCard.kt`:

```kotlin
package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType

@Composable
fun FFVehicleCard(
    nickname: String,
    plate: String,
    odometerKm: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    photoUrl: String? = null,
    vehicleType: VehicleType = VehicleType.Car,
    onClick: (() -> Unit)? = null,
) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.md)) {
            VehiclePhotoAvatar(photoUrl = photoUrl, vehicleType = vehicleType, size = 48.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                    Text(nickname, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (isActive) {
                        Surface(
                            color = FFTheme.semanticColors.success,
                            contentColor = FFTheme.semanticColors.onSuccess,
                            shape = FFTheme.extraShapes.pill,
                        ) {
                            Text(
                                "Ativo",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = FFTheme.spacing.sm, end = FFTheme.spacing.sm, top = 2.dp, bottom = 2.dp)
                            )
                        }
                    }
                }
                Text(plate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$odometerKm km", style = FFTheme.numericTypography.numericSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
```

Isso remove os imports órfãos (`Box`, `CircleShape`, `Icons`, `Icons.Default.DirectionsCar`, `Icon`, `clip`, `size`) e adiciona `VehicleType`. `VehiclePhotoAvatar` está no mesmo pacote (`core.designsystem.components`), não precisa de import.

- [ ] **Step 2: Compilar para verificar que não há erros**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (os call sites ainda não foram atualizados, mas os dois parâmetros novos têm default, então nada quebra ainda).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/components/FFVehicleCard.kt
git commit -m "feat(designsystem): FFVehicleCard exibe a foto do veiculo via VehiclePhotoAvatar"
```

---

### Task 2: `VehiclesScreen` (Meus Veículos) — passar `photoUrl`/`vehicleType`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/manage/VehiclesScreen.kt:249-256`

**Interfaces:**
- Consumes: `FFVehicleCard` com os parâmetros novos da Task 1; `Vehicle.photoUrl: String?` e `Vehicle.type: VehicleType` (já existem, `VehicleModels.kt:21-39`).
- Produces: nada consumido por outras tasks (call site folha).

Sem teste dedicado — `VehicleManageItem` é um composable privado sem teste de Compose hoje.

- [ ] **Step 1: Atualizar a chamada de `FFVehicleCard` em `VehicleManageItem`**

Em `VehiclesScreen.kt`, a chamada atual (linhas 249-256):

```kotlin
        FFVehicleCard(
            nickname   = "${vehicle.brand} ${vehicle.model}",
            plate      = vehicle.licensePlate ?: "—",
            odometerKm = String.format(Locale("pt", "BR"), "%,d", vehicle.odometerKm),
            isActive   = isActive,
            modifier   = Modifier.fillMaxWidth(),
            onClick    = onNavigateToVehicleDetails,
        )
```

Passa a ser:

```kotlin
        FFVehicleCard(
            nickname    = "${vehicle.brand} ${vehicle.model}",
            plate       = vehicle.licensePlate ?: "—",
            odometerKm  = String.format(Locale("pt", "BR"), "%,d", vehicle.odometerKm),
            isActive    = isActive,
            modifier    = Modifier.fillMaxWidth(),
            photoUrl    = vehicle.photoUrl,
            vehicleType = vehicle.type,
            onClick     = onNavigateToVehicleDetails,
        )
```

- [ ] **Step 2: Compilar para verificar que não há erros**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/manage/VehiclesScreen.kt
git commit -m "feat(vehicle): exibe a foto do veiculo em VehiclesScreen"
```

---

### Task 3: `VehiclePickerScreen` (seletor de veículo) — passar `photoUrl`/`vehicleType`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/list/VehiclePickerScreen.kt:162-169`

**Interfaces:**
- Consumes: mesma interface de `FFVehicleCard` da Task 1.
- Produces: nada consumido por outras tasks (call site folha).

Sem teste dedicado — mesmo padrão da Task 2.

- [ ] **Step 1: Atualizar a chamada de `FFVehicleCard`**

Em `VehiclePickerScreen.kt`, a chamada atual (linhas 162-169):

```kotlin
                                FFVehicleCard(
                                    nickname = "${vehicle.brand} ${vehicle.model}",
                                    plate = vehicle.licensePlate ?: "—",
                                    odometerKm = String.format(Locale("pt", "BR"), "%,d", vehicle.odometerKm),
                                    isActive = isCurrentlyActive,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { viewModel.onVehicleSelected(vehicle) },
                                )
```

Passa a ser:

```kotlin
                                FFVehicleCard(
                                    nickname = "${vehicle.brand} ${vehicle.model}",
                                    plate = vehicle.licensePlate ?: "—",
                                    odometerKm = String.format(Locale("pt", "BR"), "%,d", vehicle.odometerKm),
                                    isActive = isCurrentlyActive,
                                    modifier = Modifier.fillMaxWidth(),
                                    photoUrl = vehicle.photoUrl,
                                    vehicleType = vehicle.type,
                                    onClick = { viewModel.onVehicleSelected(vehicle) },
                                )
```

- [ ] **Step 2: Compilar para verificar que não há erros**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/list/VehiclePickerScreen.kt
git commit -m "feat(vehicle): exibe a foto do veiculo em VehiclePickerScreen"
```

---

### Task 4: `VehicleSwitcherBottomSheet` (troca rápida na Home) — passar `photoUrl`/`vehicleType`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/VehicleSwitcherBottomSheet.kt:74-80`

**Interfaces:**
- Consumes: mesma interface de `FFVehicleCard` da Task 1.
- Produces: nada consumido por outras tasks (call site folha, última deste plano).

Sem teste dedicado — mesmo padrão das Tasks 2 e 3.

- [ ] **Step 1: Atualizar a chamada de `FFVehicleCard`**

Em `VehicleSwitcherBottomSheet.kt`, a chamada atual (linhas 74-80):

```kotlin
                            FFVehicleCard(
                                nickname = vehicleNickname(vehicle),
                                plate = vehicle.licensePlate ?: "—",
                                odometerKm = formatOdometer(vehicle.odometerKm),
                                isActive = vehicle.id == state.activeId,
                                onClick = { onVehicleSelect(vehicle.id) },
                            )
```

Passa a ser:

```kotlin
                            FFVehicleCard(
                                nickname = vehicleNickname(vehicle),
                                plate = vehicle.licensePlate ?: "—",
                                odometerKm = formatOdometer(vehicle.odometerKm),
                                isActive = vehicle.id == state.activeId,
                                photoUrl = vehicle.photoUrl,
                                vehicleType = vehicle.type,
                                onClick = { onVehicleSelect(vehicle.id) },
                            )
```

- [ ] **Step 2: Compilar para verificar que não há erros**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Rodar a suíte completa de testes unitários para checar regressão**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, sem testes quebrados (nenhum ViewModel mudou nas Tasks 1-4, só composables de UI).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/VehicleSwitcherBottomSheet.kt
git commit -m "feat(home): exibe a foto do veiculo no VehicleSwitcherBottomSheet"
```

---

### Task 5: Verificação manual end-to-end contra o backend real

**Files:** nenhum (task de verificação, sem alteração de código).

**Interfaces:**
- Consumes: build instalado no emulador; veículo de teste com foto (criado após o wizard de foto obrigatória, ou com foto adicionada via `EditVehicleScreen`, spec `2026-07-03-vehicle-photo-display-edit`).
- Produces: nada (task terminal).

- [ ] **Step 1: Rodar o app no emulador**

Usar a skill `run-android-emulator` deste projeto para build + instalar + abrir o app contra `flowfuel-api.fly.dev`.

- [ ] **Step 2: Confirmar a foto em "Meus Veículos"**

Navegar: Perfil → Meus veículos. Confirmar que o veículo com foto mostra a foto real no card (não o ícone de carro), e que um veículo sem foto (se houver algum antigo) continua mostrando o ícone de fallback normalmente.

- [ ] **Step 3: Confirmar a foto no seletor de veículo (tela cheia)**

Abrir o seletor de veículo (fluxo de troca de veículo ativo, `VehiclePickerScreen`) e confirmar o mesmo comportamento do Step 2.

- [ ] **Step 4: Confirmar a foto no bottom sheet da Home**

Na Home, abrir o bottom sheet de troca rápida de veículo e confirmar o mesmo comportamento do Step 2.

- [ ] **Step 5: Registrar o resultado**

Se algum dos três lugares não mostrar a foto, anotar exatamente qual tela, qual veículo (id/nome) e o comportamento observado antes de investigar further — não assumir causa sem reproduzir.
