# Carrossel "Gasto do mês / Gasto total" (Home) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** O card `FinancialSummaryCard` na Home passa a ter 2 páginas deslizáveis: "Gasto do mês" (como hoje) e "Gasto total" (novo, usando `dashboard.totalSpent` que já é carregado).

**Architecture:** `HorizontalPager` (Compose Foundation, já usado em `OnboardingScreen`) dentro do `FFCard` existente, com um indicador de pontos local (`PagerDotsIndicator`, privado ao arquivo). Nenhuma mudança em `HomeViewModel`, camada de dados ou API — `dashboard.totalSpent` já está disponível em `HomeContent`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3 + `androidx.compose.foundation.pager`), arquitetura MVVM existente do módulo `feature/home`.

## Global Constraints

- Não adicionar dependência nova (ex: accompanist-pager) — usar `androidx.compose.foundation.pager.HorizontalPager`/`rememberPagerState`, mesma API já usada em `OnboardingScreen.kt`.
- Nenhuma mudança em `HomeViewModel.kt`, `HomeRepositoryImpl.kt`, `HomeApi.kt` ou qualquer camada de dados — `DashboardData.totalSpent` já é calculado e carregado hoje.
- O indicador de pontos é um composable **privado** dentro de `FinancialSummaryCard.kt` — não vira componente de design system.
- `FFTrendBadge` (badge de tendência) só aparece na página 0 ("Gasto do mês") — página 1 ("Gasto total") não tem comparação.
- Comportamento de loading/erro do card **não muda**: continuam fora do `HorizontalPager`, controlados pelo `when (financialSummary)` existente em `HomeScreen.kt`.
- Este projeto não tem infraestrutura de teste de UI Compose (`app/src/androidTest` não existe) — a verificação desta feature é build + instalação + inspeção visual no emulador (skill `run-android-emulator` do repo), não teste automatizado. Isso está refletido no Task 3.

---

### Task 1: `HorizontalPager` de 2 páginas em `FinancialSummaryCard.kt`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/components/FinancialSummaryCard.kt`

**Interfaces:**
- Consumes: `FFCard`/`FFCardVariant` (`core/designsystem/components/FFCard.kt`), `FFTrend`/`FFTrendBadge` (`core/designsystem/components/FFTrendBadge.kt`), `FFTheme` (`core/designsystem/theme/FFTheme.kt` — `spacing.sm`, `numericTypography.numericLarge`)
- Produces: `FinancialSummaryCard(currentMonthTotalLabel: String, percentDelta: Double?, totalSpentLabel: String, modifier: Modifier = Modifier)` — **assinatura muda**: novo parâmetro obrigatório `totalSpentLabel: String`, usado pela Task 2.

- [ ] **Step 1: Substituir o conteúdo de `FinancialSummaryCard.kt` pelo arquivo completo abaixo**

```kotlin
package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import com.flowfuel.app.core.designsystem.components.FFTrend
import com.flowfuel.app.core.designsystem.components.FFTrendBadge
import com.flowfuel.app.core.designsystem.theme.FFTheme
import kotlin.math.abs

@Composable
fun FinancialSummaryCard(
    currentMonthTotalLabel: String,
    percentDelta: Double?,
    totalSpentLabel: String,
    modifier: Modifier = Modifier,
) {
    val pageCount = 2
    val pagerState = rememberPagerState(pageCount = { pageCount })

    FFCard(modifier = modifier, variant = FFCardVariant.Flat) {
        Column {
            HorizontalPager(state = pagerState) { page ->
                Column {
                    Text(
                        text = if (page == 0) "Gasto do mês" else "Gasto total",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = FFTheme.spacing.sm),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                    ) {
                        Text(
                            text = if (page == 0) currentMonthTotalLabel else totalSpentLabel,
                            style = FFTheme.numericTypography.numericLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (page == 0 && percentDelta != null) {
                            // Gasto subindo é ruim (positiveIsGood = false): Up vira vermelho, Down vira verde.
                            val trend = when {
                                percentDelta > 0.5 -> FFTrend.Up
                                percentDelta < -0.5 -> FFTrend.Down
                                else -> FFTrend.Flat
                            }
                            FFTrendBadge(
                                trend = trend,
                                label = "%.0f%% vs. mês anterior".format(abs(percentDelta)),
                                positiveIsGood = false,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(FFTheme.spacing.sm))
            PagerDotsIndicator(pagerState = pagerState, pageCount = pageCount)
        }
    }
}

@Composable
private fun PagerDotsIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { index ->
            val active = pagerState.currentPage == index
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FinancialSummaryCardPreview() {
    FinancialSummaryCard(
        currentMonthTotalLabel = "R$ 350,00",
        percentDelta = 12.0,
        totalSpentLabel = "R$ 12.480,00",
    )
}
```

- [ ] **Step 2: Compilar para validar a sintaxe**

Run: `./gradlew.bat compileDebugKotlin --console=plain` (a partir de `C:\Users\rocha\AndroidStudioProjects\flowfuel-app`)
Expected: `BUILD SUCCESSFUL`. Isso vai falhar até a Task 2, porque `HomeScreen.kt` ainda chama `FinancialSummaryCard` sem o novo parâmetro obrigatório `totalSpentLabel` — esse erro de compilação é esperado neste ponto (é o análogo ao "teste falhando" deste tipo de mudança, já que o projeto não tem testes de UI Compose). Confirme que o erro reportado é especificamente `No value passed for parameter 'totalSpentLabel'` em `HomeScreen.kt`, e não outro erro de sintaxe em `FinancialSummaryCard.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/components/FinancialSummaryCard.kt
git commit -m "feat(home): FinancialSummaryCard vira carrossel de 2 páginas (mês/total)"
```

---

### Task 2: Wiring em `HomeScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt:213-222`

**Interfaces:**
- Consumes: `FinancialSummaryCard(currentMonthTotalLabel: String, percentDelta: Double?, totalSpentLabel: String, modifier: Modifier = Modifier)` (Task 1), `DashboardData.totalSpent: Double` (já existe em `feature/home/domain/model/HomeModels.kt`), `formatBrl(amount: Double): String` (já existe em `feature/home/presentation/components/Formatting.kt`, `internal`, já usado neste mesmo arquivo)
- Produces: nada consumido por outras tasks.

- [ ] **Step 1: Editar o bloco `when (financialSummary)` em `HomeScreen.kt`**

Localizar (por volta da linha 213-222):

```kotlin
            item {
                when (financialSummary) {
                    is SectionState.Success -> FinancialSummaryCard(
                        currentMonthTotalLabel = formatBrl(financialSummary.value.currentMonthTotal),
                        percentDelta = financialSummary.value.percentDelta,
                    )
                    SectionState.Loading -> FFSkeletonBlock(height = 96.dp)
                    is SectionState.Error -> SectionErrorCard(onRetry = onRetryFinancialSummary)
                }
            }
```

Substituir por:

```kotlin
            item {
                when (financialSummary) {
                    is SectionState.Success -> FinancialSummaryCard(
                        currentMonthTotalLabel = formatBrl(financialSummary.value.currentMonthTotal),
                        percentDelta = financialSummary.value.percentDelta,
                        totalSpentLabel = formatBrl(dashboard.totalSpent),
                    )
                    SectionState.Loading -> FFSkeletonBlock(height = 96.dp)
                    is SectionState.Error -> SectionErrorCard(onRetry = onRetryFinancialSummary)
                }
            }
```

(`dashboard` já é o parâmetro `dashboard: DashboardData` de `HomeContent`, disponível neste escopo — ver linha 167 do mesmo arquivo.)

- [ ] **Step 2: Compilar para validar que o erro da Task 1 sumiu**

Run: `./gradlew.bat compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`, sem erros em `FinancialSummaryCard.kt` nem `HomeScreen.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat(home): passa gasto total do veículo para o carrossel do card financeiro"
```

---

### Task 3: Verificação visual no emulador

**Files:** nenhum (apenas build + inspeção manual).

**Interfaces:**
- Consumes: app instalável via `./gradlew.bat installDebug` (Tasks 1-2 já compilam com sucesso).
- Produces: nada — task de verificação terminal do plano.

- [ ] **Step 1: Seguir a skill do repo para subir o emulador**

Ler e seguir `.claude/skills/run-android-emulator/SKILL.md` (raiz do repo `flowfuel-app`) — cobre o workaround de GPU (`-gpu swiftshader_indirect`), boot do AVD `Pixel_6`, build/instalação (`./gradlew.bat installDebug -x lint --console=plain`) e launch (`adb shell monkey -p com.flowfuel.app.debug -c android.intent.category.LAUNCHER 1`).

Expected: app abre na Home (`dumpsys activity activities` mostra `com.flowfuel.app.debug/com.flowfuel.app.MainActivity` como `topResumedActivity`).

- [ ] **Step 2: Screenshot da Home na página 0 do carrossel ("Gasto do mês")**

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" exec-out screencap -p > <scratchpad>/carousel_page0.png
```

Ler o PNG. Expected: card mostra título "Gasto do mês", valor do mês, badge de tendência (se houver dado de mês anterior), e 2 pontinhos abaixo do card com o **primeiro** ativo (maior/cor primária).

- [ ] **Step 3: Deslizar o card para a esquerda (swipe) e tirar novo screenshot**

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" shell input swipe 700 450 200 450 300
sleep 1
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" exec-out screencap -p > <scratchpad>/carousel_page1.png
```

(Ajustar as coordenadas Y do swipe para o meio vertical do card "Gasto do mês" na tela — usar o screenshot da Step 2 como referência.)

Ler o PNG. Expected: título mudou para "Gasto total", valor mudou para o total acumulado do veículo (maior que o valor do mês, a menos que o veículo só tenha um abastecimento), **sem** badge de tendência, e o **segundo** pontinho agora ativo.

- [ ] **Step 4: Confirmar que nada mais na Home quebrou**

Rolar a tela (swipe vertical) e conferir visualmente que os cards abaixo (Indicadores, Dica do dia, Último abastecimento, Atividade recente, Próximos eventos) renderizam normalmente, sem sobreposição ou corte introduzido pela mudança de altura do card financeiro.

Nenhum commit nesta task — é só verificação do que já foi commitado nas Tasks 1-2. Se algo estiver errado, corrigir no arquivo relevante (Task 1 ou 2) e recomeçar a partir do Step 2 de compilação daquela task.

## Fora do escopo (não fazer neste plano)

- Histórico de meses intermediários (só "mês atual" e "total geral")
- Breakdown por categoria (combustível vs. manutenção)
- Testes automatizados de UI (o projeto não tem infraestrutura para isso — ver Global Constraints)
