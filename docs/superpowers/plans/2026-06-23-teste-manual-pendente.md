# Teste Manual Pendente (FlowFuel App) Implementation Plan

> **Para quem for executar:** Este não é um plano de TDD de código novo — é um
> plano de **teste manual exploratório** no emulador Android, contra a API
> real de produção (`https://flowfuel-api.fly.dev/api/v1/`), cobrindo a lista
> de "não testado" do relatório `.claude/TESTE_MANUAL_PRODUCAO.md`. Cada
> tarefa testa uma tela/fluxo; se um bug for encontrado, corrija o código,
> revalide manualmente, e só então comite (sem skill de subagente instalada
> neste ambiente — execução é inline, nesta mesma sessão).

**Goal:** Testar manualmente, no emulador, todas as telas/fluxos do FlowFuel
App ainda não exercitados (editar/excluir abastecimento, editar/excluir
evento, tela de atualizar odômetro, trocar senha logado, excluir conta,
filtros de data/categoria, múltiplos veículos, deep links, modo offline),
corrigindo e validando qualquer bug encontrado no caminho.

**Architecture:** App Kotlin/Compose (MVI: ViewModel + StateFlow + Channel de
efeitos), Retrofit/OkHttp contra a API Spring Boot real em produção. Testes
via `adb shell input tap/text` no emulador `Pixel_6` (headless), screenshots
redimensionados via PowerShell `System.Drawing` para inspeção visual.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Retrofit, ADB, PowerShell (só
para redimensionar PNG).

## Global Constraints

- Build de teste: sempre **release** assinado (`./gradlew assembleRelease` +
  `adb install -r app/build/outputs/apk/release/app-release.apk`) — é o que
  reflete o app real, e foi onde os 14 bugs anteriores foram encontrados.
- Conta de teste: `flowfuel.qa.test1@gmail.com` / senha atual `NovaSenha456`
  (redefinida durante o teste do fluxo de recuperação de senha).
- Toda screenshot deve ser redimensionada antes de ler (`Read` rejeita PNG
  acima de ~2000px de altura): usar a função `Resize-Screen` já estabelecida
  nesta sessão (largura 600, mantém proporção).
- Coordenadas de toque são sempre em resolução real (1080x2400) — ao ler uma
  posição na screenshot redimensionada (600px), multiplicar por 1.8 antes de
  usar em `adb shell input tap`.
- Qualquer bug encontrado: aplicar o fix mínimo, `./gradlew assembleRelease`,
  reinstalar, repetir o teste até confirmar visualmente a correção, **depois**
  commitar (mensagem em português, formato já usado nos commits anteriores
  desta sessão, terminando com `Co-Authored-By: Claude Sonnet 4.6
  <noreply@anthropic.com>`).
- Não usar `adb shell input keyevent 4` (back físico) em telas que sejam a
  raiz de um fluxo sem back stack — já existe um bug conhecido (corrigido só
  em `AddVehicleScreen`) de o app sair sem aviso. Preferir sempre tocar nos
  botões de navegação da própria UI.

---

## Task 1: Editar e excluir abastecimento

**Files:**
- Review: `app/src/main/java/com/flowfuel/app/feature/history/presentation/edit/EditRefuelScreen.kt`
- Review: `app/src/main/java/com/flowfuel/app/feature/history/presentation/edit/EditRefuelViewModel.kt`
- Review: `app/src/main/java/com/flowfuel/app/feature/history/presentation/details/RefuelDetailsScreen.kt`
- Possível fix: mesmos arquivos acima, ou `app/src/main/java/com/flowfuel/app/feature/history/data/HistoryRepositoryImpl.kt`

**Interfaces:**
- Consumes: abastecimento já existente (criado na Task de teste anterior desta sessão — "Toyota Corolla", 1 abastecimento de 40L/R$235,60 em 15.300,2 km).
- Produces: nenhuma interface nova — é teste exploratório.

- [ ] **Step 1: Abrir o abastecimento existente**

```bash
export ANDROID_HOME=/c/Users/rocha/AppData/Local/Android/Sdk
export PATH="$ANDROID_HOME/platform-tools:$PATH"
adb shell am force-stop com.flowfuel.app
adb shell am start -n com.flowfuel.app/com.flowfuel.app.MainActivity
```
Logar com `flowfuel.qa.test1@gmail.com` / `NovaSenha456` se cair na tela de
Login. Ir para a aba **Histórico**, tocar no card do abastecimento de
22/06/2026.

Esperado: tela "Detalhes do abastecimento" com odômetro 15.300,2 km, 40,00 L,
R$235,60, R$5,89/L.

- [ ] **Step 2: Editar o abastecimento**

Tocar no botão de editar (ícone de lápis). Na tela de edição, alterar
"Litros abastecidos" para `45` e "Valor por litro"/total para um valor novo.
Salvar.

Esperado: volta para Detalhes com os novos valores refletidos corretamente
(sem bug de 10x no odômetro, sem troca de litros↔valor — comparar com o bug
#7 do relatório, que era exatamente sobre rótulo de preço trocado).

- [ ] **Step 3: Verificar reflexo na Home e no veículo**

Ir para a aba **Home**. Conferir se "Odômetro atual" e "Gasto total"
refletem a edição (não o valor antigo).

- [ ] **Step 4: Excluir o abastecimento**

Voltar para Detalhes do abastecimento (Histórico → tocar no card). Tocar no
menu/ícone de excluir, confirmar no diálogo.

Esperado: volta para o Histórico, card removido da lista. Conferir Home:
"Nenhum abastecimento registrado" ou estado de primeiro uso deve reaparecer
(já que era o único abastecimento).

- [ ] **Step 5: Se algum bug for encontrado, corrigir e revalidar**

Aplicar o fix mínimo no arquivo relevante, rebuildar:
```bash
./gradlew assembleRelease --console=plain
adb install -r app/build/outputs/apk/release/app-release.apk
```
Repetir os Steps 1–4 até confirmar visualmente a correção.

- [ ] **Step 6: Commit (somente se algo foi corrigido)**

```bash
git add -A -- app/src/main/java/com/flowfuel/app/feature/history
git commit -m "$(cat <<'EOF'
<descrever o bug encontrado e a correção aqui>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Editar e excluir evento de veículo

**Files:**
- Review: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/edit/EditVehicleEventScreen.kt`
- Review: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/edit/EditVehicleEventViewModel.kt`
- Review: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/details/VehicleEventDetailsScreen.kt`
- Possível fix: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/data/VehicleEventRepositoryImpl.kt` (mesma classe de bug já corrigida na criação — nomes de campo `type`/`odometer`/combinação de `description` — confirmar que o `EditVehicleEventViewModel` herda a correção corretamente, já que o código foi ajustado mas **nunca testado na UI**).

**Interfaces:**
- Consumes: evento "Troca de pneus" (Manutenção, R$350,00, 15.400 km) criado na sessão anterior.
- Produces: nenhuma.

- [ ] **Step 1: Abrir o evento existente**

Ir para a aba **Eventos**, tocar no card "Troca de pneus".

Esperado: "Detalhes do Evento" mostrando Manutenção / Troca de pneus / data /
15.400 km / R$350,00 (mesma tela já vista ao criar o evento).

- [ ] **Step 2: Editar o evento**

Tocar no botão de editar (ícone de lápis, canto inferior direito). Alterar
"Quilometragem" para `16000` e a categoria para "Troca de Óleo". Salvar.

Esperado: volta para Detalhes com categoria "Troca de Óleo" e "16.000 km"
exibidos (sem o bug de 10x — comparar com a correção da Task de odômetro
desta sessão). Como o backend não tem campo de título separado, confirmar
que "Troca de pneus" como título não duplicou/corrompeu ao salvar de novo
(round-trip de `combineDescription`/`splitDescription` em
`VehicleEventRepositoryImpl.kt`).

- [ ] **Step 3: Conferir lista de Eventos**

Ir para Eventos. Card deve mostrar "Troca de Óleo" e "16.000 km" atualizados.

- [ ] **Step 4: Excluir o evento**

Nos Detalhes do evento, tocar no menu (⋮) → Excluir, confirmar.

Esperado: volta para a lista de Eventos, agora vazia ("Nenhum evento
registrado").

- [ ] **Step 5: Se algum bug for encontrado, corrigir e revalidar**

Mesmo procedimento de build/install da Task 1, repetindo Steps 1–4.

- [ ] **Step 6: Commit (somente se algo foi corrigido)**

```bash
git add -A -- app/src/main/java/com/flowfuel/app/feature/vehicleevent
git commit -m "$(cat <<'EOF'
<descrever o bug encontrado e a correção aqui>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Tela "Atualizar odômetro"

**Files:**
- Review: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/odometer/UpdateOdometerScreen.kt`
- Review: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/odometer/UpdateOdometerViewModel.kt`
- Já corrigido nesta sessão (`FFNumberKind.WholeNumber`), mas a tela em si
  **nunca foi aberta no emulador** — só Cadastrar/Editar Veículo foram
  validados visualmente.

**Interfaces:**
- Consumes: veículo "Toyota Corolla" (atualmente ~15.300 km, dependendo do
  resultado da Task 1).
- Produces: nenhuma.

- [ ] **Step 1: Abrir a tela**

Aba **Veículos** → tocar no card do Corolla → Detalhes do Veículo → botão
"Atualizar odômetro".

Esperado: campo "Novo odômetro" vazio, mensagem mostrando o odômetro atual
como referência (rótulo exato a confirmar na tela).

- [ ] **Step 2: Testar valor menor que o atual (regressão)**

Digitar um valor MENOR que o odômetro atual (ex.: se atual é 15.300, digitar
`15000`). Tentar salvar.

Esperado: erro inline "regressão" (`vehicle_odometer_regression_error`),
sem 10x — confirmar visualmente que o número exibido no campo bate com o
que foi digitado (ex.: `15.000`, não `1.500,0`).

- [ ] **Step 3: Testar valor válido**

Limpar e digitar um valor maior (ex.: `16500`). Salvar.

Esperado: snackbar "Odômetro atualizado para 16.500 km" (com ponto, não
vírgula — já corrigido nesta sessão), volta para Detalhes do Veículo
mostrando "16.500 km".

- [ ] **Step 4: Se algum bug for encontrado, corrigir e revalidar**

Mesmo procedimento de build/install. Repetir Steps 1–3.

- [ ] **Step 5: Commit (somente se algo foi corrigido)**

```bash
git add -A -- app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/odometer
git commit -m "$(cat <<'EOF'
<descrever o bug encontrado e a correção aqui>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Trocar senha com usuário logado

**Files:**
- Review: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/changepassword/ChangePasswordScreen.kt`
- Review: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/changepassword/ChangePasswordViewModel.kt`

**Interfaces:**
- Consumes: sessão logada como `flowfuel.qa.test1@gmail.com` / senha atual `NovaSenha456`.
- Produces: nenhuma (mas a senha efetivamente muda — anotar a senha nova usada para os próximos testes desta sessão).

- [ ] **Step 1: Abrir a tela**

Aba **Perfil** → "Trocar senha".

- [ ] **Step 2: Testar senha atual incorreta**

Preencher "Senha atual" com um valor errado (ex.: `SenhaErrada000`), nova
senha válida nos dois campos. Tentar salvar.

Esperado: mensagem "Senha atual incorreta" (mapeada de `AUTH_BAD_CREDENTIALS`
em `ErrorMessages.kt` — já existente, confirmar que ainda funciona).

- [ ] **Step 3: Testar senha atual correta**

Corrigir "Senha atual" para `NovaSenha456`, nova senha `TerceiraSenha789` nos
dois campos. Salvar.

Esperado: sucesso, volta para o Perfil (ou Login, dependendo do fluxo —
conferir se a sessão é mantida ou se força novo login).

- [ ] **Step 4: Confirmar a troca**

Se a sessão caiu, logar de novo com `flowfuel.qa.test1@gmail.com` /
`TerceiraSenha789`. Se a sessão se manteve, fazer logout e logar de novo com
a senha nova para confirmar.

Esperado: login funciona com `TerceiraSenha789`; a senha antiga
(`NovaSenha456`) não deve mais funcionar.

- [ ] **Step 5: Se algum bug for encontrado, corrigir e revalidar**

- [ ] **Step 6: Commit (somente se algo foi corrigido)**

```bash
git add -A -- app/src/main/java/com/flowfuel/app/feature/auth/presentation/changepassword
git commit -m "$(cat <<'EOF'
<descrever o bug encontrado e a correção aqui>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Excluir conta

**Files:**
- Review: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/DeleteAccountDialog.kt`
- Review: `app/src/main/java/com/flowfuel/app/feature/auth/presentation/profile/ProfileViewModel.kt`
- Review: `app/src/main/java/com/flowfuel/app/feature/auth/domain/usecase/DeleteAccountUseCase.kt`

**Interfaces:**
- Consumes: sessão logada.
- Produces: conta `flowfuel.qa.test1@gmail.com` será **permanentemente
  excluída** na produção real. ⚠️ Decisão do usuário antes de executar esta
  task — ver nota abaixo.

> ⚠️ **Esta é uma ação destrutiva e irreversível na API real de produção.**
> Antes de executar este Task, confirme com o usuário se ele quer mesmo
> excluir a conta de teste, ou se prefere criar uma SEGUNDA conta de teste
> só para este fluxo (ex.: `flowfuel.qa.test2@gmail.com`, repetindo o fluxo
> de registro + ativação manual via token do log do Fly já documentado em
> `.claude/TESTE_MANUAL_PRODUCAO.md`), preservando `flowfuel.qa.test1` para
> os Tasks 6–10 que ainda dependem dela.

- [ ] **Step 1: Decidir e, se necessário, criar conta secundária**

Se optar por não excluir a conta principal, repetir o fluxo de registro
(Criar conta → ativar via token do log do Fly.io → login) para
`flowfuel.qa.test2@gmail.com`, documentado na sessão anterior.

- [ ] **Step 2: Abrir o diálogo de exclusão**

Aba **Perfil** → rolar até "Zona de Perigo" → "Excluir conta".

Esperado: diálogo de confirmação (`DeleteAccountDialog`), provavelmente
exigindo digitar algo (senha ou texto de confirmação) — observar o que pede.

- [ ] **Step 3: Confirmar exclusão**

Preencher o que o diálogo pedir e confirmar.

Esperado: navega para a tela de Login (sessão limpa), conta removida.

- [ ] **Step 4: Confirmar que a conta não existe mais**

Tentar logar de novo com o e-mail excluído e a última senha conhecida.

Esperado: erro de credenciais inválidas (não "conta não ativada" nem
sucesso).

- [ ] **Step 5: Se algum bug for encontrado, corrigir e revalidar**

- [ ] **Step 6: Commit (somente se algo foi corrigido)**

```bash
git add -A -- app/src/main/java/com/flowfuel/app/feature/auth
git commit -m "$(cat <<'EOF'
<descrever o bug encontrado e a correção aqui>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Filtros de data/categoria (Histórico e Eventos)

**Files:**
- Review: `app/src/main/java/com/flowfuel/app/feature/history/presentation/HistoryScreen.kt`, `HistoryViewModel.kt`
- Review: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/presentation/list/VehicleEventsScreen.kt`, `VehicleEventsViewModel.kt`
- Possível fix: `app/src/main/java/com/flowfuel/app/feature/vehicleevent/data/VehicleEventRepositoryImpl.kt` (o parâmetro de filtro por categoria já foi corrigido de `category` para `type` nesta sessão, mas o filtro **nunca foi exercitado na UI** — só corrigido "no escuro").

**Pré-requisito:** ter pelo menos 2 abastecimentos e 2 eventos de categorias
diferentes para os filtros fazerem sentido. Se necessário, criar um segundo
abastecimento (odômetro maior que o atual) e um segundo evento (categoria
diferente de "Troca de Óleo"/o que sobrou da Task 2) antes de iniciar.

- [ ] **Step 1: Testar filtro de período no Histórico**

Aba Histórico → tocar em "30 dias", depois "3 meses", depois "Este ano",
depois "Personalizado" (escolher um intervalo de datas no calendário).

Esperado: lista atualiza para cada filtro sem crash; "Personalizado" abre um
seletor de intervalo e filtra corretamente pelos abastecimentos cuja data
cai dentro do intervalo escolhido.

- [ ] **Step 2: Testar filtro de categoria em Eventos**

Aba Eventos → tocar no chip de uma categoria específica (ex.: "Manutenção").

Esperado: lista mostra **somente** eventos dessa categoria. Tocar em "Todas"
deve voltar a mostrar todos. **Este é o ponto crítico**: antes da correção
desta sessão, o filtro enviava `?category=` (ignorado pelo backend) — agora
deve enviar `?type=`. Se a lista não filtrar (mostrar tudo independente do
chip), é regressão ou a correção não pegou.

- [ ] **Step 3: Testar filtro de período em Eventos**

Repetir o Step 1 na aba Eventos.

- [ ] **Step 4: Se algum bug for encontrado, corrigir e revalidar**

- [ ] **Step 5: Commit (somente se algo foi corrigido)**

```bash
git add -A -- app/src/main/java/com/flowfuel/app/feature/history app/src/main/java/com/flowfuel/app/feature/vehicleevent
git commit -m "$(cat <<'EOF'
<descrever o bug encontrado e a correção aqui>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Múltiplos veículos cadastrados

**Files:**
- Review: `app/src/main/java/com/flowfuel/app/feature/vehicle/presentation/list/VehiclePickerScreen.kt`, `VehiclePickerViewModel.kt`
- Review: `app/src/main/java/com/flowfuel/app/feature/home/presentation/VehicleSwitcherBottomSheet.kt`
- Review: `app/src/main/java/com/flowfuel/app/feature/vehicle/data/VehicleRepositoryImpl.kt` (endpoint `PUT /vehicles/{id}/active`)

**Interfaces:**
- Consumes: veículo existente "Toyota Corolla".
- Produces: um segundo veículo cadastrado (ex.: moto ou carro elétrico, para
  também exercitar os campos condicionais de "Tipo de energia" ainda não
  testados com Elétrico/Híbrido).

- [ ] **Step 1: Cadastrar um segundo veículo**

Aba Veículos → "Novo veículo". Preencher com Tipo "Moto", Energia
"Combustão", marca/modelo/placa/odômetro distintos do Corolla. Salvar.

Esperado: volta para a lista de Veículos mostrando os 2 veículos.

- [ ] **Step 2: Trocar o veículo ativo pela Home**

Aba Home → tocar no nome do veículo no topo (abre `VehicleSwitcherBottomSheet`)
→ selecionar o segundo veículo.

Esperado: Home recarrega mostrando os dados do veículo recém-selecionado
(zerado, "pronto para rodar"); o Corolla deixa de aparecer como ativo na
lista de Veículos (badge "Ativo" muda de card).

- [ ] **Step 3: Confirmar isolamento de dados por veículo**

Aba Histórico e Eventos: confirmar que mostram dados do veículo
**atualmente ativo** (segundo veículo, provavelmente vazio), não misturando
com os registros do Corolla.

- [ ] **Step 4: Voltar para o Corolla e confirmar que os dados persistem**

Trocar de volta para o Corolla via `VehicleSwitcherBottomSheet`. Confirmar
que o histórico/eventos do Corolla reaparecem intactos.

- [ ] **Step 5: Testar veículo Elétrico/Híbrido (campos condicionais)**

Cadastrar um terceiro veículo com Energia "Elétrico" (deve esconder/trocar
campo "Capacidade do tanque" por "Capacidade da bateria"). Repetir com
"Híbrido" se o tempo permitir (deve exigir `refuelType` no abastecimento
rápido, conforme `QuickRefuelBottomSheet.kt`).

- [ ] **Step 6: Se algum bug for encontrado, corrigir e revalidar**

- [ ] **Step 7: Commit (somente se algo foi corrigido)**

```bash
git add -A -- app/src/main/java/com/flowfuel/app/feature/vehicle app/src/main/java/com/flowfuel/app/feature/home
git commit -m "$(cat <<'EOF'
<descrever o bug encontrado e a correção aqui>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Deep links (`flowfuel://`)

**Files:**
- Review: `app/src/main/AndroidManifest.xml` (intent-filter `flowfuel://`)
- Review: `app/src/main/java/com/flowfuel/app/navigation/FlowFuelNavHost.kt` ou `MainActivity.kt` (verificar se existe algum handler de deep link — se não existir nenhum código consumindo o `Intent.data`, é o próprio bug a relatar: manifest declara suporte, mas nada trata).

**Interfaces:**
- Consumes: nenhuma.
- Produces: nenhuma.

- [ ] **Step 1: Procurar o handler de deep link no código**

```bash
grep -rn "Intent.data\|getIntent()\|intent.data\|flowfuel://" app/src/main/java/com/flowfuel/app/
```

Esperado: encontrar onde `MainActivity`/`FlowFuelNavHost` lê o `Intent` de
entrada e decide para onde navegar. Se **nada** for encontrado, este é o
achado em si — documentar como "deep link declarado no manifest mas sem
nenhum tratamento no código" em vez de tentar simular um teste de UI que não
vai exercitar nada.

- [ ] **Step 2: Disparar o deep link via adb (se houver handler)**

```bash
adb shell am start -a android.intent.action.VIEW -d "flowfuel://algumcaminho" com.flowfuel.app
```
Ajustar `algumcaminho` conforme o que o Step 1 revelar ser esperado (ex.:
`flowfuel://vehicle/details/1` ou similar).

Esperado: app abre direto na tela correspondente (não na Splash/Home padrão).

- [ ] **Step 3: Se não houver handler, decidir com o usuário**

Reportar o achado (manifest declara suporte a deep link sem nenhum consumo
no código) e perguntar se vale a pena implementar tratamento básico agora ou
deixar para depois — **não implementar sem essa confirmação**, já que é uma
feature nova, não um bug de comportamento existente.

- [ ] **Step 4: Commit (somente se algo foi corrigido/implementado, com aprovação)**

```bash
git add -A -- app/src/main/java/com/flowfuel/app/navigation app/src/main/java/com/flowfuel/app/MainActivity.kt
git commit -m "$(cat <<'EOF'
<descrever o que foi implementado/corrigido aqui>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Modo offline / sem internet

**Files:**
- Review: `app/src/main/java/com/flowfuel/app/core/network/ApiCall.kt`
- Review: `app/src/main/java/com/flowfuel/app/core/ui/ErrorMessages.kt`
- Review qualquer tela com `FFErrorState`/`AppError.Network`

**Interfaces:**
- Consumes: nenhuma.
- Produces: nenhuma.

- [ ] **Step 1: Desligar a rede do emulador**

```bash
export ANDROID_HOME=/c/Users/rocha/AppData/Local/Android/Sdk
export PATH="$ANDROID_HOME/platform-tools:$PATH"
adb shell svc wifi disable
adb shell svc data disable
```
(Em emulador headless sem rádio, pode ser necessário usar
`adb shell settings put global airplane_mode_on 1` seguido de
`am broadcast -a android.intent.action.AIRPLANE_MODE`, ou simplesmente
`adb shell ndc resolver flush-net` se os comandos `svc` não tiverem efeito
em runtime headless — testar qual funciona neste emulador específico.)

- [ ] **Step 2: Tentar ações que dependem da rede**

Com o app já aberto e logado, tentar: pull-to-refresh na Home; abrir
Histórico; tentar registrar um abastecimento.

Esperado: cada uma mostra `FFErrorState`/snackbar com a mensagem
"Sem conexão. Verifique sua internet." (`AppError.Network` →
`error_network`), sem crash, sem tela em branco infinita.

- [ ] **Step 3: Testar login offline**

Fazer logout (se a rede permitir; senão pular esta sub-etapa) ou usar uma
segunda instância fresca do app sem sessão. Tentar logar.

Esperado: mesma mensagem de "Sem conexão", não uma mensagem de credenciais
inválidas (seria um bug: confundir falha de rede com credenciais erradas).

- [ ] **Step 4: Religar a rede e confirmar recuperação**

```bash
adb shell svc wifi enable
adb shell svc data enable
```
Tocar em "Tentar novamente" nas telas que mostraram erro.

Esperado: tudo volta a carregar normalmente, sem precisar reiniciar o app.

- [ ] **Step 5: Se algum bug for encontrado, corrigir e revalidar**

- [ ] **Step 6: Commit (somente se algo foi corrigido)**

```bash
git add -A -- app/src/main/java/com/flowfuel/app/core
git commit -m "$(cat <<'EOF'
<descrever o bug encontrado e a correção aqui>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review (preenchido ao escrever este plano)

**1. Cobertura da lista pendente:** editar abastecimento (Task 1) ✓, excluir
abastecimento (Task 1) ✓, editar evento (Task 2) ✓, excluir evento (Task 2)
✓, tela Atualizar odômetro (Task 3) ✓, trocar senha logado (Task 4) ✓,
excluir conta (Task 5) ✓, filtros de data/categoria (Task 6) ✓, múltiplos
veículos (Task 7) ✓, deep links (Task 8) ✓, modo offline (Task 9) ✓. Todos
os itens da lista do usuário cobertos.

**2. Placeholders:** nenhum "TBD"/"implementar depois" — cada step tem
comando exato ou ação de UI específica com coordenadas/textos a confirmar
visualmente na hora (inevitável em teste manual exploratório, já que a
posição exata de cada elemento só é conhecida ao ver a tela real).

**3. Consistência:** nomes de arquivos e classes referenciados foram
confirmados existentes nesta sessão (lidos/editados diretamente) — exceto
`DeleteAccountDialog.kt`, `VehicleSwitcherBottomSheet.kt` e
`EventCategoryFilterRow.kt`, citados pelo nome visto em greps anteriores
desta sessão mas não reabertos para confirmar o conteúdo atual; confirmar
ao iniciar a Task correspondente.

## Execution Handoff

Plano salvo em `docs/superpowers/plans/2026-06-23-teste-manual-pendente.md`.
Como não há skill de subagente instalada neste ambiente, a única opção real
é:

**Execução inline** — eu executo as Tasks 1–9 nesta mesma sessão, uma por
vez, parando para reportar achados e pedir aprovação antes de qualquer ação
destrutiva (Task 5, exclusão de conta) ou de implementação de feature nova
não solicitada (Task 8, se não houver handler de deep link).

Quer que eu comece pela Task 1, ou prefere reordenar/pular alguma?
