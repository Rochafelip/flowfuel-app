# Relatório de teste manual em produção — FlowFuel App

> Testado no emulador Android (Pixel_6, headless), build de release assinado,
> contra a API real em produção (`https://flowfuel-api.fly.dev/api/v1/`).
> Conta de teste: `flowfuel.qa.test1@gmail.com`.

## Bugs encontrados, corrigidos e validados de ponta a ponta

| # | Bug | Onde | Como foi validado |
|---|---|---|---|
| 1 | API em loop de crash (secret `ACCOUNT_ACTIVATION_LINK_BASE_URL` ausente) | Infra (Fly.io) | `flyctl secrets set` + `curl /actuator/health` voltou `UP` |
| 2 | Crash do Sentry ao abrir o app (auto-init nativo) | App inteiro | App abriu sem crashar após `io.sentry.auto-init=false` |
| 3 | Login com senha errada mostrava "Sessão expirada" em vez de "E-mail ou senha incorretos" | Login | Login com senha errada → mensagem correta |
| 4 | Odômetro do veículo salvo com 10x o valor digitado | Cadastrar Veículo, Editar Veículo | Cadastrei veículo com 15.000 km → confirmado 15.000 km na lista/detalhes (não 150.000) |
| 5 | Separador de milhar com vírgula em vez de ponto (locale) | Lista/Detalhes de veículo, Atualizar Odômetro | "15.000 km" exibido corretamente |
| 6 | Rótulo "Trip (km)" sem sentido (pedia odômetro absoluto) | Registrar abastecimento | Rótulo trocado para "Odômetro (km)", confirmado visualmente |
| 7 | Rótulo "Valor por litro" — campo na verdade pedia valor TOTAL pago | Registrar abastecimento | Preenchi 40L + R$235,60 → Preço/litro calculado certo (R$5,89) |
| 8 | Número cortando linha de forma feia no card de estatísticas | Home | Confirmado: agora trunca com "…" em vez de quebrar |
| 9 | Flecha de voltar enganosa em "Cadastrar veículo" (saía do app sem aviso) | Cadastrar Veículo | Testado os 2 casos: sem stack (flecha oculta) e via "Novo veículo" (flecha funciona) |
| 10 | Categorias de evento enviadas em português — backend só aceita inglês | Eventos de Veículo | Evento criado e visível na lista/detalhes após correção |
| 11 | Nomes de campo JSON errados (`category`/`odometerKm` vs `type`/`odometer`); campos `title`/`notes`/`receiptUrl` não existem no backend | Eventos de Veículo | Mesmo teste do #10 — evento com título "Troca de pneus" recuperado corretamente após combinar/separar com `description` |
| 12 | Campo "Valor" sem validação local — mostrava erro cru do backend em inglês ("must not be null") | Criar/Editar Evento | Reproduzido o erro cru, corrigido, confirmado rótulo `*` e validação em português |
| 13 | Fluxo "Esqueci minha senha" sem etapa de conclusão (corrigido em sessão anterior) | Login → Recuperar senha | Testado de ponta a ponta agora: e-mail → token real do log → redefinir → login com senha nova funcionou |
| 14 | Ativação de conta sem alternativa ao e-mail (corrigido em sessão anterior) | Cadastro → Confirme seu e-mail | Testado de ponta a ponta: cadastro → token real do log → ativação manual → login |

## Funcionalidades testadas, sem bugs encontrados

- Cadastro de conta (Criar conta)
- Login com credenciais corretas
- Logout
- Home (dashboard): consumo médio, odômetro, gasto total, último abastecimento
- Lista de Veículos
- Detalhes do veículo
- Perfil: exibição de dados e estatísticas (contadores de veículos/abastecimentos/eventos corretos)
- Histórico de abastecimentos (lista, agrupamento por mês)

## Observação registrada, não corrigida (decisão de produto pendente)

- **Inconsistência de consumo médio**: a Home diz "0.0 km/L — mínimo 2 abastecimentos para calcular", mas o Histórico mostra "7,5 km/L" para o mesmo (único) abastecimento. São métricas diferentes do próprio backend (consumo agregado vs. consumo por trecho desde o último abastecimento), mas a UI não deixa isso claro. Não é bug de código do app — é uma decisão de UX/produto sobre como apresentar.

## Não testado nesta rodada

**Telas/fluxos nunca abertos no emulador:**
- Editar perfil
- Trocar senha (usuário já logado)
- Excluir conta
- Upload/remoção de foto de perfil
- Editar abastecimento existente (`EditRefuelScreen`) — só revisado por código, não exercitado na UI
- Excluir abastecimento
- Editar evento existente (`EditVehicleEventScreen`) — código corrigido (mesmo fix de #10/#11/#12), mas nunca aberto na UI para confirmar
- Excluir evento
- Tela "Atualizar odômetro" (`UpdateOdometerScreen`) — código corrigido (#4), mas a tela em si nunca foi aberta/usada no emulador
- Filtros de data/categoria no Histórico e em Eventos (chips "30 dias", "3 meses", "Este ano", "Personalizado", filtro por categoria)
- Troca de veículo ativo / múltiplos veículos cadastrados (só testado com 1 veículo)
- Onboarding (carrossel inicial) — visto de relance, não testado a fundo
- Deep link `flowfuel://` (declarado no `AndroidManifest.xml`, nunca acionado)
- Comportamento offline / sem internet (só visto incidentalmente durante a instabilidade da API)
- Instalação em dispositivo físico real (só testado em emulador)
- Crash reporting do Sentry em produção real (DSN não configurado — `sentry.dsn` vazio em `local.properties`)

**Validações de código feitas, sem teste de UI:**
- Tratamento de `RATE_LIMIT_EXCEEDED` (rate limiting está desligado em produção, não há como provocar esse erro agora)
- Tratamento de `AUTH_REFRESH_REVOKED`/`AUTH_REFRESH_EXPIRED` ao reaparecer no login (motivo do logout forçado) — exigiria forçar revogação de refresh token em outro dispositivo/sessão para reproduzir