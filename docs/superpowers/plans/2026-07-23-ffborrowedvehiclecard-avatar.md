# FFBorrowedVehicleCard: adicionar avatar de veículo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar identidade visual ao card de veículo emprestado (`FFBorrowedVehicleCard`), adicionando um `VehiclePhotoAvatar` ao lado do texto, no mesmo padrão visual do card de veículo próprio (`FFVehicleCard`).

**Architecture:** Mudança isolada e puramente visual em um único arquivo. O `Row(título+badge)` seguido de `Text(expiry)` que hoje é o conteúdo direto do `FFCard` passa a ficar dentro de uma `Column`, ao lado de um `VehiclePhotoAvatar` — reaproveitando o composable já existente que `FFVehicleCard` usa, sem criar nada novo. `VehiclePickerScreen` e `VehiclesScreen` não mudam: ambas já consomem `FFBorrowedVehicleCard`, então herdam o novo visual automaticamente.

**Tech Stack:** Kotlin, Jetpack Compose. Sem testes de UI Compose neste projeto — a verificação é por compilação forçada (rodando suítes de teste de ViewModel que importam as telas que usam o componente) mais checagem visual manual no emulador.

## Global Constraints

- Comandos de build/teste no Windows usam `.\gradlew.bat` (não `./gradlew`).
- Este projeto não tem testes de UI Compose (`createComposeRule`) — a verificação de mudanças visuais é feita rodando a suíte de testes de ViewModel do módulo afetado, que força a compilação de todo o código principal (incluindo as telas) antes de rodar.
- O avatar sempre usa o ícone genérico de carro (`VehicleType.Car`, `photoUrl = null`) — `VehicleShare` não carrega tipo real do veículo nem foto. Isso é esperado, não é um bug a corrigir nesta tarefa.
- Spec desta feature: `docs/superpowers/specs/2026-07-23-ffborrowedvehiclecard-avatar-design.md`.

---

### Task 1: Adicionar `VehiclePhotoAvatar` ao `FFBorrowedVehicleCard`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/core/designsystem/components/FFBorrowedVehicleCard.kt`

**Interfaces:**
- Consumes: `VehiclePhotoAvatar(photoUrl: String?, vehicleType: VehicleType, modifier: Modifier = Modifier, size: Dp = 64.dp, onClick: (() -> Unit)? = null)` (existente, mesmo pacote `core.designsystem.components`, arquivo `VehiclePhotoAvatar.kt`), `VehicleType` (existente, `com.flowfuel.app.feature.vehicle.domain.model.VehicleType`).
- Produces: nenhuma mudança de assinatura pública — `FFBorrowedVehicleCard(share: VehicleShare, modifier: Modifier = Modifier, onClick: () -> Unit)` continua igual. `VehiclePickerScreen` e `VehiclesScreen` não precisam de nenhuma mudança.

Este task é puramente visual: não há lógica nova, então não há teste unitário a escrever. A verificação é por compilação forçada (Step 2 e 3) e checagem visual manual (Step 4).

- [ ] **Step 1: Substituir o conteúdo de `FFBorrowedVehicleCard.kt`**

Conteúdo completo do arquivo (substitui o arquivo inteiro):

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
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Card de veículo compartilhado com o usuário (não é dono). Usado em
 * `VehiclePickerScreen` e `VehiclesScreen` — mesmo visual nas duas telas
 * para deixar claro que é um veículo diferente de um próprio.
 *
 * O avatar sempre usa o ícone genérico de carro: `VehicleShare` não carrega
 * `vehicleType`/`photoUrl` do backend (limitação aceita, ver spec
 * `2026-07-23-ffborrowedvehiclecard-avatar-design.md`).
 */
@Composable
fun FFBorrowedVehicleCard(
    share: VehicleShare,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
        ) {
            VehiclePhotoAvatar(photoUrl = null, vehicleType = VehicleType.Car, size = 48.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                ) {
                    Text(
                        text = "${share.vehicleBrand} ${share.vehicleModel}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        color = FFTheme.semanticColors.info,
                        contentColor = FFTheme.semanticColors.onInfo,
                        shape = FFTheme.extraShapes.pill,
                    ) {
                        Text(
                            text = "Emprestado",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(
                                start = FFTheme.spacing.sm,
                                end = FFTheme.spacing.sm,
                                top = 2.dp,
                                bottom = 2.dp,
                            ),
                        )
                    }
                }
                Text(
                    text = "até ${share.expiresAt?.formatShareExpiry() ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val shareExpiryFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

private fun String.formatShareExpiry(): String =
    runCatching { LocalDate.parse(take(10)).format(shareExpiryFormatter) }.getOrDefault(this)
```

O que mudou em relação ao arquivo anterior:
- Novos imports: `androidx.compose.foundation.layout.Column`,
  `com.flowfuel.app.feature.vehicle.domain.model.VehicleType`.
- O `Row(título+badge)` e o `Text(expiry)`, que antes eram filhos diretos do
  `FFCard`, agora ficam dentro de uma `Column` nova.
- Essa `Column` fica ao lado de um `VehiclePhotoAvatar` novo, dentro de um
  `Row` externo novo (`spacedBy(FFTheme.spacing.md)` — mesmo espaçamento
  avatar↔texto usado por `FFVehicleCard`).
- O `Row` interno (título+badge) mantém `spacedBy(FFTheme.spacing.sm)` —
  sem mudança de espaçamento aí.
- Docstring do componente ganhou uma segunda frase explicando a limitação
  do ícone genérico.

- [ ] **Step 2: Rodar a suíte de testes do picker pra forçar a compilação**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.list.VehiclePickerViewModelTest"`
Expected: BUILD SUCCESSFUL (força a compilação de `VehiclePickerScreen.kt`, que consome `FFBorrowedVehicleCard` — se houver erro de sintaxe/import/tipo no componente, o build falha aqui)

- [ ] **Step 3: Rodar a suíte de testes de `VehiclesViewModel` pra forçar a compilação da segunda tela**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.flowfuel.app.feature.vehicle.presentation.manage.VehiclesViewModelTest"`
Expected: BUILD SUCCESSFUL (força a compilação de `VehiclesScreen.kt`, a outra consumidora de `FFBorrowedVehicleCard`)

- [ ] **Step 4: Checagem visual manual no emulador**

Pré-requisito: precisa de uma conta logada com pelo menos 1 compartilhamento
ativo aceito como convidado (ver [[project_qa_test_account]] e
[[project_vehicleshare_module]] pra contexto de contas de teste — no teste
mais recente, em 2026-07-23, `rocha.felipe98@gmail.com` tinha o Toyota
Corolla compartilhado por `yhe66@web-library.net` ativo).

Instalar o build de debug e abrir a tela (`.\gradlew.bat installDebug` +
abrir o app no emulador — ver skill `run-android-emulator` se precisar
subir o emulador do zero). Navegar até Perfil → Meus Veículos (ou até o
picker de veículos, se houver troca de veículo em andamento) e confirmar:

1. O card do veículo emprestado agora mostra um círculo com ícone de carro
   à esquerda do texto, do mesmo tamanho visual do avatar usado nos cards
   de veículo próprio.
2. O texto (marca+modelo, badge "Emprestado", data "até {data}") continua
   no mesmo lugar relativo, só deslocado pra direita pelo avatar — nada
   de texto cortado ou quebrado de forma estranha.
3. Tocar no card continua levando ao modo convidado normalmente (nenhuma
   mudança de comportamento, só visual).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/components/FFBorrowedVehicleCard.kt
git commit -m "feat(designsystem): adicionar avatar de veiculo ao FFBorrowedVehicleCard"
```
