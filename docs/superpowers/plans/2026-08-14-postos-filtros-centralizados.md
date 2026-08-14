# Centralizar filtros na tela de Postos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Centralizar visualmente os filtros de tipo (Combustível/Elétrico) e raio (1/3/5/10 km) na tela de Postos, sem mudar comportamento.

**Architecture:** Ajuste puro de layout Compose — alinhamento no ponto de chamada do filtro de tipo, `horizontalArrangement` no `LazyRow` do filtro de raio.

**Tech Stack:** Kotlin, Jetpack Compose.

## Global Constraints

- Design aprovado em `docs/superpowers/specs/2026-08-14-postos-filtros-centralizados-design.md`.
- Sub-projeto A de 3 (B e C — endpoint de geocodificação no backend e UI de pesquisa de cidade — ficam para specs/plans separados).
- Nenhuma mudança de comportamento — só alinhamento visual.

---

### Task 1: Centralizar os dois filtros

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsScreen.kt:121-132`
- Modify: `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationDistanceFilterRow.kt:27-31`

**Interfaces:** Nenhuma — mudança de layout local, sem novos parâmetros ou tipos.

Nenhum componente da tela de Postos tem teste automatizado hoje (mesma
lacuna de outros componentes puramente visuais no app). Verificação por
screenshot antes/depois no emulador.

- [ ] **Step 1: Centralizar `StationTypeFilterRow`**

Em `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsScreen.kt`, dentro do bloco `if (state != StationsUiState.PermissionRequired) { ... }`:

```kotlin
            if (state != StationsUiState.PermissionRequired) {
                StationTypeFilterRow(
                    selectedType = selectedType,
                    onSelect = viewModel::onTypeSelected,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = FFTheme.spacing.sm),
                )
                StationDistanceFilterRow(
                    selectedRadiusMeters = radiusMeters,
                    onSelect = viewModel::onRadiusSelected,
                    modifier = Modifier.padding(vertical = FFTheme.spacing.sm),
                )
            }
```

(`Alignment` já está importado — `androidx.compose.ui.Alignment` — usado em outros pontos do mesmo arquivo.)

- [ ] **Step 2: Centralizar `StationDistanceFilterRow`**

Em `app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationDistanceFilterRow.kt`:

```kotlin
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
```

(`Arrangement.spacedBy(space, alignment)` mantém os 8dp de espaçamento
entre chips e ainda centraliza o grupo — `Arrangement.Center` sozinho
também centralizaria, mas perderia o espaçamento consistente definido
hoje. Precisa do import `androidx.compose.ui.Alignment`, que ainda não
existe neste arquivo.)

- [ ] **Step 3: Compilar**

Run: `./gradlew.bat compileDebugKotlin -q`

Expected: sem erros.

- [ ] **Step 4: Verificar visualmente no emulador**

Use a skill `run-android-emulator` deste projeto pra buildar e instalar o
app debug. Login com a conta de teste QA (`retiko1301@jobraux.com`), abrir
a aba "Postos". Confirmar:
- Os botões "Combustível"/"Elétrico" ficam centralizados horizontalmente
- Os chips de raio (1 km, 3 km, 5 km, 10 km) ficam centralizados como
  grupo, com o mesmo espaçamento de antes entre eles
- Nenhum chip fica cortado ou escondido
- Selecionar um filtro de cada tipo ainda funciona normalmente (sem
  regressão de comportamento)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationsScreen.kt \
        app/src/main/java/com/flowfuel/app/feature/station/presentation/list/StationDistanceFilterRow.kt
git commit -m "fix(stations): center type and distance filter rows"
```
