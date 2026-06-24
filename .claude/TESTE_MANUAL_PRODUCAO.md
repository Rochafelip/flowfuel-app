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
| 15 | Avatar nunca aparecia (mapeava `profilePictureUrl`, que o backend nunca preenche, em vez de `profilePicture`) | Perfil — foto | Subi uma foto → avatar carregou corretamente após o fix |
| 16 | Remoção de foto de perfil falhava no release (`ResponseBody?` em retorno 204 perde nulidade no R8) | Perfil — foto | Removi a foto → sem erro, fallback de iniciais voltou |
| 17 | Botão "Excluir conta" inalcançável (Column sem `verticalScroll`, botão ficava fora da área tocável em tela real) | Perfil — Zona de Perigo | `verticalScroll` adicionado, botão confirmado alcançável e tocável na tela |
| 18 | Trocar senha e excluir conta sempre mostravam "Algo deu errado" no release (mesmo bug de `ResponseBody?` 204 + R8 do #16) | Trocar senha / Excluir conta | Erro cru parou de aparecer após o fix |
| 19 | Edição de abastecimento sempre falhava (`vehicleId` ausente no PUT, exigido pelo backend mesmo em update parcial) | Editar abastecimento | Editei 40,00L → 45,00L mantendo o valor total: salvou, recalculou o preço/litro corretamente, manteve o odômetro |
| 20 | Mesmos rótulos errados de odômetro/preço do bug #6/#7, só que na tela de edição ("Trip (km)", "Valor por litro" pedindo total) | Editar abastecimento | Rótulos corrigidos, confirmado junto com a validação do #19 |
| 21 | Histórico ficava em branco (sem empty-state) após excluir o único abastecimento | Histórico | Excluí o único abastecimento → empty-state correto exibido |
| 22 | Edição de evento sempre falhava silenciosamente (backend exige `vehicleId` no PUT `/vehicle-events/{id}`) | Editar evento | Editei categoria/km do evento → salvou e refletiu corretamente |
| 23 | Lista de Eventos não recarregava após editar um evento (só create/delete propagavam sinal de refresh) | Eventos de Veículo | Lista atualizada corretamente após editar |
| 24 | `PUT /vehicles/{id}/odometer` sempre falhava — backend espera o valor como query param `currentKm`, não como corpo JSON | Atualizar odômetro | Atualizei odômetro do veículo → sucesso, snackbar correto |
| 25 | Cadastrar veículo sem preencher capacidade do tanque/bateria ou odômetro falhava 100% silenciosamente (sem snackbar, sem erro inline) | Cadastrar Veículo | Campo de odômetro checava a chave de erro errada do backend (`odometerKm` em vez de `currentKm`); capacidade não tinha `errorText` nenhum. Corrigido — erro agora aparece inline em "Capacidade do tanque (L)" |
| 26 | Deep links `flowfuel://` declarados no manifest sem nenhum tratamento de `Intent.data` no código | Navegação | Implementado tratamento básico em `MainActivity`/`FlowFuelNavHost`; validado com app fechado e em foreground, navegando para a tela correta |

## Funcionalidades testadas, sem bugs encontrados

- Cadastro de conta (Criar conta)
- Login com credenciais corretas
- Logout
- Home (dashboard): consumo médio, odômetro, gasto total, último abastecimento
- Lista de Veículos
- Detalhes do veículo
- Perfil: exibição de dados e estatísticas (contadores de veículos/abastecimentos/eventos corretos)
- Histórico de abastecimentos (lista, agrupamento por mês)
- Trocar senha (usuário já logado): login confirmado com a senha nova
- Excluir conta: confirmado que a conta é removida no backend (login pós-exclusão retorna credenciais inválidas)
- Edição e exclusão de abastecimento
- Edição e exclusão de evento de veículo
- Filtros de data/categoria no Histórico e em Eventos
- Múltiplos veículos cadastrados (troca de veículo ativo, isolamento de dados por veículo, campos condicionais de energia Elétrico/Híbrido)
- Modo offline: erros de rede tratados corretamente, recuperação automática ao reconectar, login offline não confunde falta de rede com credenciais inválidas

## Observação registrada, não corrigida (decisão de produto pendente)

- **Inconsistência de consumo médio**: a Home diz "0.0 km/L — mínimo 2 abastecimentos para calcular", mas o Histórico mostra "7,5 km/L" para o mesmo (único) abastecimento. São métricas diferentes do próprio backend (consumo agregado vs. consumo por trecho desde o último abastecimento), mas a UI não deixa isso claro. Não é bug de código do app — é uma decisão de UX/produto sobre como apresentar.
- **Mensagens de erro cruas em inglês** ainda aparecem para alguns campos sem tradução local (ex.: "must not be null" na capacidade do veículo) — mesmo padrão do bug #12, não corrigido de forma abrangente.

## Não testado nesta rodada

**Telas/fluxos nunca abertos no emulador:**
- Editar perfil
- Onboarding (carrossel inicial) — visto de relance, não testado a fundo
- Instalação em dispositivo físico real (só testado em emulador)
- Crash reporting do Sentry em produção real (DSN não configurado — `sentry.dsn` vazio em `local.properties`)
- Veículo Híbrido (campos condicionais combinando tanque + bateria) — só Elétrico foi testado

**Validações de código feitas, sem teste de UI:**
- Tratamento de `RATE_LIMIT_EXCEEDED` (rate limiting está desligado em produção, não há como provocar esse erro agora)
- Tratamento de `AUTH_REFRESH_REVOKED`/`AUTH_REFRESH_EXPIRED` ao reaparecer no login (motivo do logout forçado) — exigiria forçar revogação de refresh token em outro dispositivo/sessão para reproduzir
