
# Aviso de caixa de spam nas telas de token por e-mail Design

**Goal:** nas duas telas onde o usuário aguarda/insere um token enviado por e-mail (ativação de conta e redefinição de senha), exibir um aviso visível de que o e-mail pode ter caído na caixa de spam/lixo eletrônico, para reduzir confusão e tickets de suporte de usuários que "não recebem" o e-mail.

**Scope:** puramente visual/copy — nenhuma mudança de lógica de negócio, ViewModel ou fluxo de rede. O aviso é estático, sempre visível, sem novo estado.

## Componente novo: `FFInfoBanner`

**Arquivo:** `app/src/main/java/com/flowfuel/app/core/designsystem/components/FFInfoBanner.kt`

Banner informativo reutilizável do design system, seguindo o padrão de cores já usado no `EnergyTypeBadge` (`VehicleDetailsScreen.kt`): `tertiaryContainer`/`onTertiaryContainer`.

```kotlin
@Composable
fun FFInfoBanner(
    text: String,
    modifier: Modifier = Modifier,
)
```

- `Row` com ícone `Icons.Outlined.Info` (tint `onTertiaryContainer`) + `Text` (`bodyMedium`, cor `onTertiaryContainer`), alinhados `Alignment.Top` com `spacedBy(FFTheme.spacing.sm)`.
- Fundo `tertiaryContainer`, forma `FFTheme.extraShapes.card`, padding interno `FFTheme.spacing.md`.
- Sem parâmetro de variante/kind — hoje só existe o caso de uso "info". Se surgir um segundo caso (ex. aviso de erro) no futuro, o componente evolui para aceitar um enum tipo `FFCardVariant`/`FFSnackbarKind`; não construir essa abstração agora (YAGNI).
- Sem preview obrigatório, mas seguindo o padrão do resto do design system, incluir um `@Preview(showBackground = true)`.

## String nova

Em `app/src/main/res/values/strings.xml`, adicionar (reaproveitada nas duas telas):

```xml
<string name="spam_folder_notice">Não encontrou o e-mail? Verifique também a caixa de spam ou lixo eletrônico.</string>
```

## Integração: `CheckEmailScreen.kt`

Inserir o banner logo após o `Text` de `check_email_instruction` (linha ~137) e antes do `Spacer(Modifier.height(FFTheme.spacing.xl))` que precede o botão de reenviar:

```kotlin
Spacer(Modifier.height(FFTheme.spacing.sm))

FFInfoBanner(
    text = stringResource(R.string.spam_folder_notice),
    modifier = Modifier.fillMaxWidth(),
)

Spacer(Modifier.height(FFTheme.spacing.xl))
```

Import novo: `com.flowfuel.app.core.designsystem.components.FFInfoBanner`.

## Integração: `ResetPasswordScreen.kt`

A `Column` já usa `verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md)`, então o banner só precisa ser inserido como mais um item, logo após o `Text` do subtítulo (linha ~75) e antes do `FFTextField` do token:

```kotlin
Text(
    text = stringResource(R.string.reset_password_subtitle, email),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
FFInfoBanner(text = stringResource(R.string.spam_folder_notice))
FFTextField(
    value = state.token,
    ...
)
```

Import novo: `com.flowfuel.app.core.designsystem.components.FFInfoBanner`.

## Testes

Sem lógica nova (nenhum ViewModel/state tocado) — não há teste unitário a escrever. Verificação é visual: compilar e checar as duas telas no emulador (`run-android-emulator`), conferindo que o banner não quebra o layout em telas pequenas e que o texto não corta.

## Fora de escopo

- Não há aviso condicional (ex. só depois de X segundos ou após reenviar) — decisão explícita do usuário para manter simples.
- Não cobre outras telas de código/token que não envolvam e-mail (não existem outras no momento).
