# Design: Tela de ativação por email — só magic link (sem código à vista)

**Data:** 2026-07-27
**Status:** aprovado

## Contexto

O backend (`flowfuel`) já foi implantado com o novo email de ativação: em vez de mostrar o código de ativação em texto puro, o email traz um botão clicável "Ativar conta" (link `flowfuel://activate?token=...&email=...`, ver spec do backend). O app Android (`flowfuel-app`) já trata esse deep link (`FlowFuelNavHost`, commit anterior) e já tem uma `CheckEmailScreen` com aviso de spam (`FFInfoBanner` + string `spam_folder_notice`) — mas essa tela **sempre mostra**, junto com o resto, um campo de colar código + botão "Ativar com código" (leftover do fluxo antigo, em que o código aparecia em texto no email).

Essa combinação criou uma tela poluída: ela mistura o fluxo "toque no link" com o fluxo "copie e cole o código", quando hoje só o primeiro deveria ser o caminho principal.

**Risco conhecido, aceito conscientemente:** o `linkBaseUrl` configurado no backend hoje é `flowfuel://activate` (esquema customizado, não um domínio `https` com Android App Links). Gmail — e a maioria dos clientes de email — não linkifica esquemas customizados, então o botão "Ativar conta" no email pode não ficar clicável para boa parte dos usuários. Corrigir isso na raiz (domínio próprio + App Links) está fora de escopo deste trabalho; a mitigação aqui é manter um fallback manual, só que discreto em vez de sempre visível.

## Decisões

1. **Reestruturar `CheckEmailScreen`** — conteúdo principal (sempre visível), nessa ordem:
   ícone de email → título (`check_email_title`, inalterado) → subtítulo com o email (`check_email_subtitle`, inalterado) → aviso de spam em destaque, logo abaixo do subtítulo, antes de qualquer botão → botão "Reenviar e-mail" → botão texto "Já confirmei → Entrar".

   O texto do aviso de spam (string `spam_folder_notice`) passa a fundir instrução + spam, substituindo a necessidade da string `check_email_instruction` (que é removida):
   - Antes: `check_email_instruction` = "Clique no link no e-mail para ativar sua conta e volte aqui para entrar." + `spam_folder_notice` = "Não encontrou o e-mail? Verifique também a caixa de spam ou lixo eletrônico." (duas strings, a segunda mais abaixo na tela)
   - Depois: uma única string `spam_folder_notice` = "Abra o e-mail e toque no botão de ativação. Não encontrou? Verifique a caixa de spam ou lixo eletrônico." — mostrada uma vez, logo abaixo do subtítulo.

2. **Fallback manual vira secundário, dentro de um `FFBottomSheet`** — o bloco hoje sempre visível (label + `FFTextField` de código + botão "Ativar com código", linhas 172-199 do arquivo atual) some da tela principal. Em seu lugar, um botão texto discreto no fim ("Problemas para ativar?", nova string `check_email_manual_entry_link`) abre um `FFBottomSheet` (componente já existente no design system) contendo exatamente esse bloco (label `check_email_manual_token_label`, campo `check_email_manual_token_field`, botão `check_email_manual_token_cta` — todas strings reaproveitadas sem mudança). Estado de abertura do sheet é local à Composable (`remember { mutableStateOf(false) }`), sem entrar no `CheckEmailViewModel`.

3. **Auto-ativação ao abrir o magic link** — hoje, abrir `flowfuel://activate?token=...` (via `FlowFuelNavHost` → `initialToken`) só chama `viewModel.onActivationTokenChange(initialToken)`, preenchendo o campo; o usuário ainda precisa apertar um botão manualmente. Como esse campo passa a ficar escondido no bottom sheet (decisão 2), isso quebraria silenciosamente o fluxo — o token ficaria preenchido em um lugar que ninguém vê.

   Mudança: o `LaunchedEffect(initialToken)` em `CheckEmailScreen` passa a chamar `viewModel.onActivationTokenChange(initialToken)` **seguido de** `viewModel.activateWithToken()` quando `initialToken` não está em branco, completando de fato o fluxo "abrir e-mail → 1 toque → ativado e logado" (efeito `ActivatedAndLoggedIn` já existente, que navega para home). Nenhuma mudança de assinatura ou lógica no `CheckEmailViewModel` — a orquestração das duas chamadas fica na Composable.

4. **Erro de ativação passa a aparecer também como snackbar** — hoje `state.activationError` só é exibido como `errorText` inline no campo de código (que ficará escondido por padrão). Como a ativação automática do item 3 pode falhar (token inválido/expirado/já usado) sem que o usuário tenha aberto o bottom sheet, `CheckEmailScreen` passa a observar `state.activationError` com um `LaunchedEffect` (mesmo padrão já usado para `resendErrorMessage`) e mostrar um `FFSnackbarVisuals(..., FFSnackbarKind.Error)`. O `errorText` inline no campo do bottom sheet continua existindo também (redundante só quando o sheet está aberto, o que é inofensivo).

## Fora de escopo

- Mudar o backend, o secret `ACCOUNT_ACTIVATION_LINK_BASE_URL` ou o esquema do deep link (continua `flowfuel://`).
- Resolver a limitação do Gmail não linkificar esquemas customizados (domínio próprio + Android App Links) — mitigado pelo fallback do bottom sheet, não corrigido na raiz.
- Qualquer mudança em `AuthApi`, `AuthRepositoryImpl`, `ActivateAccountUseCase`, `ResendActivationUseCase` ou no shape de `CheckEmailUiState`/`CheckEmailEffect` — o `CheckEmailViewModel` não muda.
- Mudanças de navegação (`FlowFuelNavHost`, `Destinations`) — o deep link já roteia corretamente para `CheckEmailScreen` com `initialToken`.

## Arquivos afetados

```
flowfuel-app (Android):
  app/src/main/java/com/flowfuel/app/feature/auth/presentation/checkemail/CheckEmailScreen.kt
  app/src/main/res/values/strings.xml
```

Nenhum arquivo de teste existente (`CheckEmailViewModelTest.kt`) precisa mudar — o `ViewModel` não muda de comportamento, só passa a ser chamado em outra sequência pela `Screen`. Não há testes de UI/Compose para essa tela hoje (nenhum arquivo `CheckEmailScreenTest`), então não há suíte de screen tests a atualizar.

## Critérios de Aceitação

- Ao abrir a tela sem token (fluxo normal pós-registro), o usuário vê: ícone, título, subtítulo com o email, aviso de spam (com a instrução fundida), botão reenviar, botão "já confirmei", e um link discreto "Problemas para ativar?" — **sem** campo de código visível.
- Tocar em "Problemas para ativar?" abre um bottom sheet com o campo de código e o botão "Ativar com código", idêntico ao comportamento atual desse par campo+botão.
- Abrir o app via `flowfuel://activate?token=...&email=...` ativa a conta e loga automaticamente **sem** exigir nenhum toque adicional do usuário (nem no bottom sheet, nem em botão de ativar) — token válido resulta direto em `ActivatedAndLoggedIn` → navegação para home.
- Token inválido/expirado/usado, vindo do deep link, mostra o erro como snackbar (tela permanece na `CheckEmailScreen`, sem o bottom sheet abrir sozinho).
- Confirmação manual (usuário abre o bottom sheet e cola o código à mão) continua funcionando exatamente como hoje, incluindo erro inline no campo.
- Reenvio de email (`Reenviar e-mail`) e "Já confirmei → Entrar" continuam com o comportamento atual, inalterado.
