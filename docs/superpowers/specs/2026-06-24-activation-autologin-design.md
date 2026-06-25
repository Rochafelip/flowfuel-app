# Design: Auto-login no app Android via deep link de ativação

**Data:** 2026-06-24
**Status:** aprovado

## Contexto

O backend (`flowfuel`) já retorna `accessToken`/`refreshToken` em `POST /auth/activate`. O app Android (`flowfuel-app`) já tem o deep link `flowfuel://activate?token=...` funcionando (commit `25edeb9`, sessão anterior): abre direto na `CheckEmailScreen` com o token pré-preenchido. Falta só usar a resposta do `activate` pra logar automaticamente, em vez de só confirmar e mandar pro login.

O link real enviado por email hoje ainda é `https://flowfuel-api.fly.dev?token=...` (não o deep link) — o secret `ACCOUNT_ACTIVATION_LINK_BASE_URL` no Fly está desatualizado.

## Decisões

1. **Backend** (`flowfuel`): `SmtpAccountActivationNotifier` passa a incluir `&email=<urlencoded>` no link. Secret `ACCOUNT_ACTIVATION_LINK_BASE_URL` → `flowfuel://activate`.
2. **`AuthApi.activate()`**: retorna `AuthResponseDto` (mesmo shape de `login`), não mais `Unit`.
3. **`AuthRepositoryImpl.activate()`**: salva sessão via `SessionStore`, mesmo padrão de `login()` (extrai `userId` do `user.id` ou do JWT como fallback).
4. **`CheckEmailViewModel`**: novo efeito `ActivatedAndLoggedIn` (em vez de `ActivationConfirmed`) quando a ativação tem sucesso.
5. **`CheckEmailScreen`/`FlowFuelNavHost`**: novo callback `onNavigateHome`, navegando para `Destinations.VEHICLE_PICKER` com `popUpTo(LOGIN) { inclusive = true }` — mesmo destino do login bem-sucedido.

Fora de escopo: renovação automática de refresh token (já existe via `TokenRefreshAuthenticator`, não muda); resolver a limitação de o Gmail não linkificar `flowfuel://` (sem domínio próprio ainda — mesma decisão já registrada para o SMTP).

## Arquivos afetados

```
flowfuel (backend):
  src/main/java/com/devappmobile/flowfuel/user/SmtpAccountActivationNotifier.java

flowfuel-app (Android):
  app/src/main/java/com/flowfuel/app/feature/auth/data/remote/AuthApi.kt
  app/src/main/java/com/flowfuel/app/feature/auth/data/AuthRepositoryImpl.kt
  app/src/main/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailViewModel.kt
  app/src/main/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailScreen.kt
  app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt
  app/src/test/java/com/flowfuel/app/feature/auth/data/AuthRepositoryImplTest.kt (novos testes)
  app/src/test/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailViewModelTest.kt (novo)

Config (sem código): Fly secret ACCOUNT_ACTIVATION_LINK_BASE_URL
```

## Restrição de execução

Esta sessão (WSL Linux) acessa o repo via `/mnt/c/...` mas **não tem ADB nem Gradle com SDK Android configurado** (`local.properties` usa caminhos Windows). Edição de código funciona normalmente; build, testes unitários (`./gradlew test`) e verificação manual em dispositivo/emulador precisam ser executados pelo usuário no Android Studio (Windows).

## Critérios de Aceitação

- Tocar no link de ativação (`flowfuel://activate?token=...&email=...`) ativa a conta e loga automaticamente, sem tela de login.
- Token inválido/expirado/usado continua mostrando erro na `CheckEmailScreen` (comportamento de erro inalterado).
- Confirmação manual de token (campo de texto na tela) também loga automaticamente, já que usa o mesmo `activateWithToken()`.
- Login normal (email/senha) inalterado.
