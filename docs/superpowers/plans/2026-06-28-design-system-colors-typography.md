# Design System — Colors & Typography Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corrigir 7 defeitos identificados no design system — 3 P0 (labelSmall inacessível, Inter não está conectada, OutlineDark invisível), 2 P1 (OutlineLight sem contraste, alphas hardcoded no HomeScreen), e 2 P2 (SurfaceContainer M3, cor semântica Info).

**Architecture:** Todas as mudanças ficam na camada de design system (`core/designsystem/theme/`), mais uma limpeza pontual em `HomeScreen.kt` para eliminar valores alpha hardcoded. Nenhuma lógica de negócio é tocada. Verificação via `./gradlew assembleDebug` (compilação) e `@Preview` no Android Studio (visual).

**Tech Stack:** Jetpack Compose, Material3 1.3.x (BOM 2024.12.01), Kotlin 2.0.21, `ui-text-google-fonts`

## Global Constraints

- 
- BOM: `2024.12.01` — não bumpar versões Compose/M3 individualmente fora do BOM
- minSdk 26 (Android 8.0), compileSdk 35
- Tokens do tema vivem em `app/src/main/java/com/flowfuel/app/core/designsystem/theme/`
- Não há testes de unidade para o design system — verificação é: `./gradlew assembleDebug` deve passar, e Previews existentes devem renderizar
- NÃO alterar ViewModels, repositórios, ou lógica de negócio

---

## File Map

| Arquivo | Ação | Responsabilidade |
|---------|------|-----------------|
| `core/designsystem/theme/Typography.kt` | Modificar | Corrigir escala (labelSmall, bodyLarge); conectar Inter |
| `core/designsystem/theme/Color.kt` | Modificar | Corrigir outline/surface; adicionar FFAlpha; ampliar FFExtraColors |
| `core/designsystem/theme/Theme.kt` | Modificar | Expor `FFAlpha` via `FFTheme`; mapear SurfaceContainer |
| `feature/home/presentation/HomeScreen.kt` | Modificar | Substituir 5 chamadas `.copy(alpha = x)` por tokens FFAlpha |
| `gradle/libs.versions.toml` | Modificar | Adicionar alias `ui-text-google-fonts` |
| `app/build.gradle.kts` | Modificar | Adicionar dependência `ui-text-google-fonts` |
| `app/src/main/res/values/font_certs.xml` | Criar | Certificados GMS para o provedor Google Fonts |

---

### Task 1: Fix Typography Scale — labelSmall + bodyLarge letterSpacing (P0)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/core/designsystem/theme/Typography.kt`

**Context:** `labelSmall` está em 11sp, abaixo do mínimo WCAG 2.1 AA (12sp). `bodyLarge` tem `letterSpacing = 0.5.sp`, que é o valor M3 correto para *labels*, não para body — comprime o texto de conteúdo. Essas são as mudanças mais simples, sem dependências.

- [ ] **Step 1: Corrigir `labelSmall` e `bodyLarge` em `Typography.kt`**

Substituir apenas esses dois estilos (manter tudo o mais exatamente como está):

```kotlin
bodyLarge = TextStyle(
    fontFamily = InterFamily, fontWeight = FontWeight.Normal,
    fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp  // era 0.5.sp
),

labelSmall = TextStyle(
    fontFamily = InterFamily, fontWeight = FontWeight.Medium,
    fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp   // era 11.sp
),
```

- [ ] **Step 2: Compilar**

```bash
./gradlew assembleDebug
```
Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/theme/Typography.kt
git commit -m "fix: labelSmall 11sp→12sp (WCAG AA), bodyLarge letterSpacing 0.5→0.15sp"
```

---

### Task 2: Conectar Inter via Google Fonts Compose (P0)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/flowfuel/app/core/designsystem/theme/Typography.kt`
- Create: `app/src/main/res/values/font_certs.xml`

**Context:** `InterFamily` está definida como `FontFamily.SansSerif`, que resolve para Roboto em todos os dispositivos Android. Esta task conecta a fonte Inter real usando Compose Google Fonts — download em runtime via GMS, sem binários para commitar, funciona em Android 6.0+ com GMS instalado.

- [ ] **Step 1: Adicionar alias em `libs.versions.toml`**

Na seção `[libraries]`, após a última entrada `androidx-compose-*`:

```toml
androidx-compose-ui-text-google-fonts = { module = "androidx.compose.ui:ui-text-google-fonts" }
```

Sem `version.ref` — o BOM gerencia a versão.

- [ ] **Step 2: Adicionar dependência em `app/build.gradle.kts`**

No bloco `dependencies`, após `implementation(libs.androidx.compose.material.icons.extended)`:

```kotlin
implementation(libs.androidx.compose.ui.text.google.fonts)
```

(Hífens no alias TOML viram pontos no accessor Gradle KTS.)

- [ ] **Step 3: Atualizar `Typography.kt` para usar Inter via Google Fonts**

Substituir a seção de declaração de fontes no topo do arquivo (linhas 1–10):

```kotlin
package com.flowfuel.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.flowfuel.app.R

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val inter = GoogleFont("Inter")

private val InterFamily = FontFamily(
    Font(googleFont = inter, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = inter, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = inter, fontProvider = fontProvider, weight = FontWeight.SemiBold),
)

private val MonoFamily = FontFamily.Monospace
```

O restante de `FFTypography` e `FFNumericTypography` fica exatamente como está.

- [ ] **Step 4: Criar `app/src/main/res/values/font_certs.xml`**

Este arquivo contém os fingerprints SHA-256 do APK de assinatura do provedor GMS. O conteúdo correto e atualizado está disponível em:
- **Android Studio:** Menu `File > New > Downloadable Font`, escolha "Inter" — o Studio gera o `font_certs.xml` automaticamente com os certs corretos.
- **Referência oficial:** https://developer.android.com/develop/ui/compose/text/fonts#downloadable-fonts

Estrutura esperada do arquivo gerado pelo Studio:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <array name="com_google_android_gms_fonts_certs">
        <item>@array/com_google_android_gms_fonts_certs_dev</item>
        <item>@array/com_google_android_gms_fonts_certs_prod</item>
    </array>
    <string-array name="com_google_android_gms_fonts_certs_dev">
        <item><!-- cert data gerado pelo Android Studio --></item>
    </string-array>
    <string-array name="com_google_android_gms_fonts_certs_prod">
        <item><!-- cert data gerado pelo Android Studio --></item>
    </string-array>
</resources>
```

**Nota:** Se não for possível usar o Studio wizard, o projeto pode compilar temporariamente com um array vazio (`<array name="com_google_android_gms_fonts_certs"></array>`). A fonte não carregará em runtime mas o app não crasha — cai para Roboto como fallback. Substituir pelos certs corretos antes do release.

- [ ] **Step 5: Compilar**

```bash
./gradlew assembleDebug
```
Esperado: `BUILD SUCCESSFUL`. Se aparecer `Unresolved reference: R`, verificar que `font_certs.xml` está em `app/src/main/res/values/` (não em `res/font/`).

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml \
    app/build.gradle.kts \
    app/src/main/java/com/flowfuel/app/core/designsystem/theme/Typography.kt \
    app/src/main/res/values/font_certs.xml
git commit -m "feat: wire Inter font via Google Fonts Compose (replaces FontFamily.SansSerif)"
```

---

### Task 3: Corrigir OutlineDark e OutlineVariantDark (P0)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt`

**Context:** `OutlineDark = #2C342F` sobre Surface `#161D19` tem ~1.5:1 de contraste — bordas de input e divisores são invisíveis no dark mode. `OutlineVariantDark = #222B26` é ainda pior (~1.3:1). Os novos valores precisam atingir ≥ 3.5:1 para `OutlineDark` (WCAG AA para componentes de UI) e ≥ 1.8:1 para `OutlineVariantDark` (divisores decorativos).

Racional dos valores escolhidos (calculado contra Surface `#161D19`, L=0.0113):
- `OutlineDark = #6B7870` → L≈0.171, ratio ≈ 3.6:1 ✓
- `OutlineVariantDark = #3A4642` → L≈0.053, ratio ≈ 1.9:1 ✓

- [ ] **Step 1: Atualizar as duas cores dark em `Color.kt`**

Em `FFColors`, alterar apenas esses dois valores (manter todos os outros):

```kotlin
// era: val OutlineDark = Color(0xFF2C342F)
val OutlineDark = Color(0xFF6B7870)

// era: val OutlineVariantDark = Color(0xFF222B26)
val OutlineVariantDark = Color(0xFF3A4642)
```

- [ ] **Step 2: Compilar**

```bash
./gradlew assembleDebug
```
Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt
git commit -m "fix: OutlineDark 1.5:1→3.6:1 e OutlineVariantDark 1.3:1→1.9:1 no dark mode"
```

---

### Task 4: Corrigir OutlineLight + Surface Layering no Dark Mode (P1)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt`

**Context:** Dois problemas independentes no mesmo arquivo: (a) `OutlineLight = #C7CDC9` sobre branco tem ~1.6:1, falha WCAG AA para componentes de UI (mínimo 3:1). (b) No dark mode, `BackgroundDark`, `SurfaceDark` e `SurfaceVariantDark` são tão próximos (~8 unidades de luminosidade) que cards não se distinguem do fundo.

Racional dos valores:
- `OutlineLight = #6D7570` → L≈0.168, ratio contra branco ≈ 4.7:1 ✓
- `OutlineVariantLight = #BFC9C3` → levemente mais escuro que atual, ainda decorativo
- `SurfaceDark = #1A2520` → spread de ~12 unidades acima do background (era ~8)
- `SurfaceVariantDark = #242E28` → spread de ~10 unidades acima do novo Surface

- [ ] **Step 1: Aplicar as 4 mudanças de cor em `Color.kt`**

```kotlin
// era: val OutlineLight = Color(0xFFC7CDC9)
val OutlineLight = Color(0xFF6D7570)

// era: val OutlineVariantLight = Color(0xFFDCE5E0)
val OutlineVariantLight = Color(0xFFBFC9C3)

// era: val SurfaceDark = Color(0xFF161D19)
val SurfaceDark = Color(0xFF1A2520)

// era: val SurfaceVariantDark = Color(0xFF1E2622)
val SurfaceVariantDark = Color(0xFF242E28)
```

`BackgroundDark` (`#0E1411`) e `OnSurface*` ficam inalterados.

- [ ] **Step 2: Compilar**

```bash
./gradlew assembleDebug
```
Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt
git commit -m "fix: OutlineLight 1.6:1→4.7:1, melhorar separação de camadas no dark mode"
```

---

### Task 5: Adicionar FFAlpha Tokens + Limpar HomeScreen (P1)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt`
- Modify: `app/src/main/java/com/flowfuel/app/core/designsystem/theme/Theme.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt`

**Context:** `HomeScreen.kt` tem 5 chamadas `.copy(alpha = x)` hardcoded com 3 valores distintos (0.75f, 0.7f, 0.15f) sem semântica clara. Os valores `medium` e `subtle` correspondem a níveis de ênfase do Material3 e devem ser tokens compartilhados.

- [ ] **Step 1: Adicionar objeto `FFAlpha` ao final de `Color.kt`**

Após `DarkSemanticColors`, adicionar:

```kotlin
object FFAlpha {
    const val medium = 0.74f   // texto secundário / ênfase média M3
    const val subtle = 0.12f   // divisores e superfícies tintadas
}
```

`medium` substitui tanto os usos de 0.70f quanto 0.75f (ambos são "texto secundário").  
`subtle` substitui o uso de 0.15f (divisor decorativo).

- [ ] **Step 2: Expor `FFAlpha` via `FFTheme` em `Theme.kt`**

`FFAlpha` é um object singleton — não precisa de CompositionLocal. Adicionar como propriedade direta em `FFTheme`:

```kotlin
object FFTheme {
    // ... propriedades existentes ...
    val alpha: FFAlpha get() = FFAlpha
}
```

- [ ] **Step 3: Substituir as 5 chamadas hardcoded em `HomeScreen.kt`**

Adicionar import no topo de `HomeScreen.kt`:
```kotlin
import com.flowfuel.app.core.designsystem.theme.FFAlpha
```

**Linha 335** (WelcomeHeroCard — texto de descrição):
```kotlin
// era: .copy(alpha = 0.75f)
color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = FFAlpha.medium),
```

**Linha 375** (ConsumptionHeroCard — fuelLabel):
```kotlin
// era: .copy(alpha = 0.7f)
color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = FFAlpha.medium),
```

**Linha 401** (ConsumptionHeroCard — unit Text):
```kotlin
// era: .copy(alpha = 0.7f)
color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = FFAlpha.medium),
```

**Linha 410** (ConsumptionHeroCard — HorizontalDivider):
```kotlin
// era: .copy(alpha = 0.15f)
color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = FFAlpha.subtle),
```

**Linha 423** (ConsumptionHeroCard — texto de legenda):
```kotlin
// era: .copy(alpha = 0.75f)
color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = FFAlpha.medium),
```

- [ ] **Step 4: Compilar**

```bash
./gradlew assembleDebug
```
Esperado: `BUILD SUCCESSFUL`. Se aparecer `Unresolved reference: FFAlpha`, verificar que o import foi adicionado.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt \
    app/src/main/java/com/flowfuel/app/core/designsystem/theme/Theme.kt \
    app/src/main/java/com/flowfuel/app/feature/home/presentation/HomeScreen.kt
git commit -m "feat: adicionar FFAlpha tokens, remover alpha hardcoded do HomeScreen"
```

---

### Task 6: Mapear SurfaceContainer para M3 1.3.x (P2)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt`

**Context:** Material3 1.3.x (BOM 2024.12.01 → M3 1.3.1) introduziu `surfaceContainer`, `surfaceContainerLow`, `surfaceContainerHigh`, e `surfaceContainerHighest`. Sem esse mapeamento, o M3 usa valores auto-gerados que podem não seguir a paleta verde do FlowFuel. Cards e bottom sheets são os principais afetados.

- [ ] **Step 1: Adicionar valores SurfaceContainer em `FFColors`**

Em `FFColors`, após `OutlineVariantLight`:

```kotlin
// SurfaceContainer — light
val SurfaceContainerLowestLight  = Color(0xFFFFFFFF)
val SurfaceContainerLowLight     = Color(0xFFF4F7F4)
val SurfaceContainerLight        = Color(0xFFEEF2EE)
val SurfaceContainerHighLight    = Color(0xFFE8EDE9)
val SurfaceContainerHighestLight = Color(0xFFE2E8E3)

// SurfaceContainer — dark
val SurfaceContainerLowestDark   = Color(0xFF0B1210)
val SurfaceContainerLowDark      = Color(0xFF17201B)
val SurfaceContainerDark         = Color(0xFF1C261F)
val SurfaceContainerHighDark     = Color(0xFF232E27)
val SurfaceContainerHighestDark  = Color(0xFF283428)
```

- [ ] **Step 2: Mapear em `FFLightColorScheme`**

Em `lightColorScheme(...)`, após `outlineVariant = FFColors.OutlineVariantLight,`:

```kotlin
surfaceContainerLowest = FFColors.SurfaceContainerLowestLight,
surfaceContainerLow = FFColors.SurfaceContainerLowLight,
surfaceContainer = FFColors.SurfaceContainerLight,
surfaceContainerHigh = FFColors.SurfaceContainerHighLight,
surfaceContainerHighest = FFColors.SurfaceContainerHighestLight,
```

- [ ] **Step 3: Mapear em `FFDarkColorScheme`**

Em `darkColorScheme(...)`, após `outlineVariant = FFColors.OutlineVariantDark,`:

```kotlin
surfaceContainerLowest = FFColors.SurfaceContainerLowestDark,
surfaceContainerLow = FFColors.SurfaceContainerLowDark,
surfaceContainer = FFColors.SurfaceContainerDark,
surfaceContainerHigh = FFColors.SurfaceContainerHighDark,
surfaceContainerHighest = FFColors.SurfaceContainerHighestDark,
```

- [ ] **Step 4: Compilar**

```bash
./gradlew assembleDebug
```
Esperado: `BUILD SUCCESSFUL`. Se alguma das propriedades não existir (versão M3 < 1.2.0), verificar a versão do BOM — com BOM 2024.12.01 e M3 1.3.1 todos os roles estão disponíveis.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt
git commit -m "feat: mapear SurfaceContainer family para M3 1.3.x"
```

---

### Task 7: Adicionar Cor Semântica Info (P2)

**Files:**
- Modify: `app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt`

**Context:** `FFSemanticColors` tem `success` e `warning` mas não `info`. Qualquer componente futuro de banner informativo (FFAlertBanner com nível Info) precisaria improvisar com `secondary` ou `tertiary`, criando inconsistência. Esta task fecha o sistema semântico.

- [ ] **Step 1: Adicionar valores Info em `FFExtraColors`**

Em `FFExtraColors`, após `OnWarningDark`:

```kotlin
val InfoLight   = Color(0xFF0055CC)
val OnInfoLight = Color(0xFFFFFFFF)

val InfoDark    = Color(0xFF7BAAF7)
val OnInfoDark  = Color(0xFF002D6E)
```

- [ ] **Step 2: Adicionar `info` e `onInfo` em `FFSemanticColors`**

```kotlin
data class FFSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val info: Color,
    val onInfo: Color,
)
```

- [ ] **Step 3: Atualizar `LightSemanticColors` e `DarkSemanticColors`**

```kotlin
val LightSemanticColors = FFSemanticColors(
    success = FFExtraColors.SuccessLight,
    onSuccess = FFExtraColors.OnSuccessLight,
    warning = FFExtraColors.WarningLight,
    onWarning = FFExtraColors.OnWarningLight,
    info = FFExtraColors.InfoLight,
    onInfo = FFExtraColors.OnInfoLight,
)

val DarkSemanticColors = FFSemanticColors(
    success = FFExtraColors.SuccessDark,
    onSuccess = FFExtraColors.OnSuccessDark,
    warning = FFExtraColors.WarningDark,
    onWarning = FFExtraColors.OnWarningDark,
    info = FFExtraColors.InfoDark,
    onInfo = FFExtraColors.OnInfoDark,
)
```

- [ ] **Step 4: Compilar — checar erros em callsites de `FFSemanticColors`**

```bash
./gradlew assembleDebug
```

Se falhar com "no value passed for parameter 'info'", buscar por `FFSemanticColors(` no projeto e adicionar os novos campos. Se compilar limpo, prosseguir.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt
git commit -m "feat: adicionar cor semântica Info a FFSemanticColors"
```

---

## Self-Review — Cobertura da Análise

| Problema da Análise | Prioridade | Task |
|---------------------|-----------|------|
| `InterFamily = FontFamily.SansSerif` não é Inter | P0 | Task 2 |
| `labelSmall` em 11sp — viola WCAG AA | P0 | Task 1 |
| `OutlineDark` invisível no dark mode | P0 | Task 3 |
| `OutlineLight` contraste 1.6:1 | P1 | Task 4 |
| Dark mode surfaces sem separação de camada | P1 | Task 4 |
| 5 alphas hardcoded em HomeScreen | P1 | Task 5 |
| `bodyLarge` letterSpacing 0.5sp (deveria ser 0.15sp) | P2 | Task 1 |
| SurfaceContainer tokens ausentes | P2 | Task 6 |
| Cor semântica Info faltando | P2 | Task 7 |
| Disabled state via alpha — coberto por `FFAlpha.low` (não exposto, mas disponível como `0.38f`) | P2 | Task 5 (parcial) |
