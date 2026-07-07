# Aviso de caixa de spam nas telas de token por e-mail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** nas telas `CheckEmailScreen` (ativação de conta) e `ResetPasswordScreen` (redefinir senha), exibir um banner sempre visível avisando que o e-mail com o token pode ter caído na caixa de spam/lixo eletrônico.

**Architecture:** um componente novo do design system (`FFInfoBanner`) recebe apenas um `text: String` e se estiliza sozinho (ícone + fundo `tertiaryContainer`, mesmo padrão de cor do `EnergyTypeBadge` já existente). As duas telas apenas o instanciam com uma string nova (`spam_folder_notice`) — nenhuma mudança em ViewModel, estado ou navegação.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), design system interno (`core/designsystem/components`, `core/designsystem/theme`).

## Global Constraints

- Sem novo estado/lógica: o banner é estático e sempre visível, sem depender de cooldown, resend ou temporizador (decisão confirmada no brainstorm).
- Cores seguem o padrão já usado no `EnergyTypeBadge` (`VehicleDetailsScreen.kt`): `MaterialTheme.colorScheme.tertiaryContainer` / `onTertiaryContainer`.
- Forma: `FFTheme.extraShapes.card` (mesmo raio de canto usado em `FFCard`).
- Espaçamento: tokens de `FFTheme.spacing` (`sm` = 8.dp, `md` = 16.dp), nunca `dp` hardcoded.
- Strings em português; string nova reaproveitada nas duas telas (`spam_folder_notice`).
- `FFInfoBanner` não recebe parâmetro de variante/kind — só existe o caso de uso "info" hoje (YAGNI).
- Nenhum teste automatizado novo: mudança é puramente visual/copy, sem lógica testável em JVM; verificação é compilação + checagem manual no emulador.

---

### Task 1: `FFInfoBanner` — componente de banner informativo

**Files:**
- Create: `app/src/main/java/com/flowfuel/app/core/designsystem/components/FFInfoBanner.kt`

**Interfaces:**
- Produces: `@Composable fun FFInfoBanner(text: String, modifier: Modifier = Modifier)`. Consumido por `CheckEmailScreen.kt` e `ResetPasswordScreen.kt` (Task 2).

- [ ] **Step 1: Criar o componente**

```kotlin
package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.theme.FFTheme

/**
 * Banner informativo persistente (não é um snackbar transitório) para avisos
 * contextuais, ex.: "confira a caixa de spam". Fundo `tertiaryContainer`,
 * mesmo par de cores já usado no `EnergyTypeBadge` de `VehicleDetailsScreen`.
 */
@Composable
fun FFInfoBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FFTheme.extraShapes.card)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(FFTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FFInfoBannerPreview() {
    FFInfoBanner(text = "Não encontrou o e-mail? Verifique também a caixa de spam ou lixo eletrônico.")
}
```

- [ ] **Step 2: Verificar compilação**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowfuel/app/core/designsystem/components/FFInfoBanner.kt
git commit -m "feat(design-system): adiciona FFInfoBanner"
```

---

### Task 2: Integrar o aviso de spam em `CheckEmailScreen` e `ResetPasswordScreen`

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailScreen.kt`
- Modify: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/resetpassword/ResetPasswordScreen.kt`

**Interfaces:**
- Consumes: `FFInfoBanner(text: String, modifier: Modifier)` (Task 1).

No new automated test: wiring de UI puro (uma string nova + duas chamadas de composable), sem lógica de negócio. Verificado manualmente via a skill `run-android-emulator` no fim desta task.

- [ ] **Step 1: Adicionar a string em `strings.xml`**

Em `app/src/main/res/values/strings.xml`, adicionar (posição sugerida: perto das strings `check_email_*`, já que ambas as telas vão referenciar a mesma chave):

```xml
<string name="spam_folder_notice">Não encontrou o e-mail? Verifique também a caixa de spam ou lixo eletrônico.</string>
```

- [ ] **Step 2: Inserir o banner em `CheckEmailScreen.kt`**

Adicionar o import, logo após `import com.flowfuel.app.core.designsystem.components.FFButtonVariant`:

```kotlin
import com.flowfuel.app.core.designsystem.components.FFInfoBanner
```

No corpo do `Column` (dentro de `CheckEmailScreen`), o trecho atual:

```kotlin
            Text(
                text = stringResource(R.string.check_email_instruction),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(FFTheme.spacing.xl))

            val resendLabel = when {
```

vira:

```kotlin
            Text(
                text = stringResource(R.string.check_email_instruction),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(FFTheme.spacing.sm))

            FFInfoBanner(text = stringResource(R.string.spam_folder_notice))

            Spacer(Modifier.height(FFTheme.spacing.xl))

            val resendLabel = when {
```

- [ ] **Step 3: Inserir o banner em `ResetPasswordScreen.kt`**

Adicionar o import, logo após `import com.flowfuel.app.core.designsystem.components.FFButton`:

```kotlin
import com.flowfuel.app.core.designsystem.components.FFInfoBanner
```

O trecho atual dentro do `Column` (a `Column` já usa `verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md)`, então basta inserir mais um item — sem `Spacer` manual):

```kotlin
            Text(
                text = stringResource(R.string.reset_password_subtitle, email),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FFTextField(
                value = state.token,
```

vira:

```kotlin
            Text(
                text = stringResource(R.string.reset_password_subtitle, email),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FFInfoBanner(text = stringResource(R.string.spam_folder_notice))
            FFTextField(
                value = state.token,
```

- [ ] **Step 4: Verificar compilação**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Rodar a suíte de testes unitários (garantir zero regressão)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (nenhum teste toca essas telas, mas confirma que nada mais quebrou)

- [ ] **Step 6: Verificação manual no emulador**

Use a skill `run-android-emulator` deste projeto para instalar e abrir o app, então:

1. Cadastrar uma conta nova (ou usar o fluxo "Esqueci minha senha" a partir da tela de login) para chegar em `CheckEmailScreen` → confirmar que o banner "Não encontrou o e-mail? Verifique também a caixa de spam ou lixo eletrônico." aparece logo abaixo da instrução, com fundo destacado e ícone, antes do botão "Reenviar e-mail" → confirmar que o texto não corta e não estoura a tela em fontes maiores (testar com o texto do sistema em tamanho grande, se prático).
2. A partir da tela de login, tocar em "Esqueci minha senha" → confirmar que `ResetPasswordScreen` mostra o mesmo banner logo abaixo do subtítulo com o e-mail, antes do campo "Código de redefinição".
3. Capturar screenshot de cada tela (`adb exec-out screencap`) para conferência visual final.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailScreen.kt app/src/main/java/com/flowfuel/app/feature/auth/presentation/resetpassword/ResetPasswordScreen.kt
git commit -m "feat(auth): avisa sobre a caixa de spam nas telas de token por e-mail"
```

---
