# Design: Fundação de Push Notification (FCM)

**Data:** 2026-07-14
**Status:** aprovado

## Contexto

Vai ser preciso avisar o usuário de eventos que acontecem fora do app aberto — o
primeiro caso de uso concreto é o convite de compartilhamento de veículo por
CPF (spec futura), mas a peça de infraestrutura em si (registrar dispositivo,
disparar push, exibir e navegar a partir dela) é genérica e vale a pena
construir isolada, pra servir de base a outros eventos no futuro (ex.: lembrete
de manutenção).

Hoje o app (`flowfuel-app`) não tem nenhuma integração com FCM nem serviço de
notificação push. Já existe, porém, um mecanismo de deep link
(`flowfuel://<rota>`) tratado em `FlowFuelNavHost`, hoje usado só para o magic
link de ativação de conta (`flowfuel://activate?token=...`). Esta spec estende
esse mesmo mecanismo para ser o alvo de navegação de qualquer notificação push,
em vez de construir um caminho de navegação novo.

O backend (`flowfuel`, repositório separado, Node/TypeScript) também precisa de
mudanças: endpoint para registrar/remover token de dispositivo e um serviço
interno de envio via Firebase Admin SDK.

Fora de escopo: iOS (app é Android-only), sistema de tipos/templates de
notificação por evento (ícones/ações diferentes por tipo) — reservamos um
campo `type` no payload para o futuro, mas hoje o tratamento é genérico.

## Decisões

### Cliente (`flowfuel-app`)

1. Novo módulo `core/notification`:
   - `FlowFuelFcmService : FirebaseMessagingService` — recebe mensagens
     (`onMessageReceived`) e captura renovação de token (`onNewToken`).
   - `NotificationChannelSetup` — cria o canal único `general` na
     inicialização do `Application` (exigido a partir do Android 8.0).
   - `PushPayload` — data class com `title`, `body`, `deepLink`, `type`
     (todos string; `type` não é usado ainda, só reservado).
   - `DeviceTokenRepository` — registra/renova token via novo endpoint
     (`POST /devices`), remove no logout (`DELETE /devices/:token`).
2. Permissão `POST_NOTIFICATIONS` (Android 13+) solicitada no fluxo de
   login/onboarding, de forma não bloqueante — negar a permissão não impede o
   uso do app, só desativa push naquele aparelho.
3. Ao tocar na notificação: `PendingIntent` abre `MainActivity` com uma
   `Intent` de ação `VIEW` cujo `data` é a URI do `deepLink` do payload — o
   mesmo formato que `MainActivity` já lê hoje (`intent.data`, em
   `onCreate`/`onNewIntent`) e repassa como `deepLinkUri` para
   `FlowFuelNavHost`. Nenhuma rota nova de navegação nem novo canal de
   `Intent` precisa ser criado, só passar a aceitar deep links arbitrários
   além do caso `activate`.
4. Com o app em primeiro plano, a notificação ainda é exibida no shade (sem
   navegação automática) — só age quando tocada, comportamento previsível.
5. Falha ao obter token FCM (ex.: Google Play Services ausente/desatualizado)
   é silenciosa: login e uso do app seguem normais, só sem push nesse
   aparelho.

### Backend (`flowfuel`)

6. Tabela `device_tokens` (`userId`, `token`, `platform`, `updatedAt`) — um
   usuário pode ter múltiplos tokens (múltiplos dispositivos).
7. `POST /devices` (autenticado): upsert do token do dispositivo atual do
   usuário logado. `DELETE /devices/:token` (autenticado): remove no logout.
8. Serviço interno `sendPushToUser(userId, payload)` (não exposto como rota
   pública) — busca todos os tokens do usuário, envia via Firebase Admin SDK.
   Tokens que o FCM reporta como inválidos (`UNREGISTERED`/`NOT_FOUND`) são
   removidos automaticamente da tabela.
9. Contrato do payload enviado ao FCM (`data` message, não `notification`,
   pra dar controle total de exibição ao cliente):
   ```json
   { "title": "...", "body": "...", "deepLink": "flowfuel://...", "type": "generic" }
   ```

## Arquivos afetados

```
flowfuel-app (Android):
  app/src/main/java/com/flowfuel/app/core/notification/FlowFuelFcmService.kt (novo)
  app/src/main/java/com/flowfuel/app/core/notification/NotificationChannelSetup.kt (novo)
  app/src/main/java/com/flowfuel/app/core/notification/PushPayload.kt (novo)
  app/src/main/java/com/flowfuel/app/core/notification/DeviceTokenRepository.kt (novo)
  app/src/main/java/com/flowfuel/app/core/network/ (novo endpoint na API de devices)
  app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt (aceitar deep link genérico, não só activate)
  app/src/main/AndroidManifest.xml (registro do serviço FCM, permissão POST_NOTIFICATIONS)
  app/build.gradle.kts (dependência firebase-messaging)

flowfuel (backend):
  device tokens: migração da tabela + endpoints POST /devices e DELETE /devices/:token
  serviço sendPushToUser(userId, payload) com Firebase Admin SDK
```

## Testes

- **Backend:** unitário de `sendPushToUser` (mock do Firebase Admin SDK,
  incluindo o caminho de limpeza de token inválido); integração dos endpoints
  `POST /devices` / `DELETE /devices/:token`.
- **Cliente:** unitário do parsing de `PushPayload` → construção da
  `deepLink URI` (JUnit5, sem emulador). Verificação manual ponta a ponta
  (enviar push de teste via Firebase Console/`sendPushToUser` num ambiente de
  dev, conferir exibição e navegação ao tocar) — UI test automatizado de push
  não compensa o esforço nesse escopo.

## Critérios de Aceitação

- Usuário loga → token FCM é registrado no backend (`POST /devices`).
- Token renovado pelo FCM a qualquer momento → backend atualizado automaticamente.
- Logout → token removido do backend, aparelho para de receber push daquela conta.
- Negar permissão de notificação não impede login nem uso do app.
- Push recebido com app em background: aparece no shade; tocar abre o app na
  rota indicada por `deepLink`.
- Push recebido com app em foreground: aparece no shade, sem navegação automática.
- Token inválido reportado pelo FCM é removido da tabela `device_tokens` no
  próximo envio.
