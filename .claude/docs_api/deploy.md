# Deploy em produção (Fly.io + Neon)

Setup de produção para uso pessoal (1-2 usuários), 24/7, sem custo de servidor dedicado.

## Arquitetura

- **API**: Fly.io, região `gru` (São Paulo), 1 máquina `shared-cpu-1x` / **512MB RAM**, sempre ativa (`min_machines_running = 1`, sem auto-stop).
- **Banco**: Neon.tech (Postgres serverless, free tier), região `sa-east-1`.
- Config da máquina: [fly.toml](../fly.toml).
- Imagem: [Dockerfile](../Dockerfile) (build multi-stage Maven + JRE Alpine).

URL pública: `https://flowfuel-api.fly.dev` (HTTPS automático via Fly).

## Por que não Render free / Oracle Cloud / Railway

- **Render free**: dorme após ~15min de inatividade (cold start de 30-60s) e o Postgres free expira após inatividade — não confiável para "sempre online".
- **Oracle Cloud Free Tier**: conta nova foi recusada na verificação ("high risk") sem cartão.
- **Fly.io**: também exigiu verificação de conta com cartão (`fly.io/high-risk-unlock`), mas sem outras barreiras. Foi o caminho seguido.

## Passo a passo (do zero)

### 1. Banco de dados (Neon)
1. Criar conta em neon.tech, criar projeto (região mais próxima — `sa-east-1` no nosso caso).
2. Copiar a connection string (`postgresql://user:pass@host/db?sslmode=require`).

### 2. Instalar a CLI do Fly e logar
```bash
curl -L https://fly.io/install.sh | sh
# adicionar ao ~/.bashrc:
export FLYCTL_INSTALL="$HOME/.fly"
export PATH="$FLYCTL_INSTALL/bin:$PATH"

flyctl auth login
```

### 3. Verificação de conta (obrigatório para contas novas)
Contas novas do Fly.io são marcadas "high risk" e bloqueadas até cadastrar um cartão em `fly.io/high-risk-unlock`. Sem isso, `flyctl launch` falha com `Error: Your account has been marked as high risk`.

### 4. Criar o app a partir do `fly.toml`
```bash
flyctl launch --no-deploy --copy-config --yes
```

### 5. Configurar os secrets
```bash
flyctl secrets set \
  SPRING_DATASOURCE_URL="jdbc:postgresql://<host-neon>/<db>?sslmode=require" \
  SPRING_DATASOURCE_USERNAME="<user-neon>" \
  SPRING_DATASOURCE_PASSWORD="<senha-neon>" \
  JWT_SECRET="$(openssl rand -hex 32)" \
  FLOWFUEL_RATE_LIMIT_ENABLED=false \
  MANAGEMENT_HEALTH_MAIL_ENABLED=false
```

### 6. Deploy
```bash
flyctl deploy
```

## Problemas encontrados e correções (histórico real do primeiro deploy)

1. **`fly.toml` inválido** — health check sem `type` (`http`/`tcp`). Corrigido (depois sobrescrito pelo próprio `flyctl launch`, que regenerou o arquivo num formato válido sem precisar do campo).
2. **OOM kill com 256MB de RAM** — a JVM com Spring Security + JPA + AWS S3 SDK + Sentry não cabe em 256MB mesmo limitando o heap a 75% (`-XX:MaxRAMPercentage=75`). A máquina entrava em loop de crash/restart. **Correção**: subir `[[vm]] memory` para `512mb` no [fly.toml](../fly.toml).
3. **Rate limiting exige Redis** — `RateLimitingConfig` tenta conectar em `redis://localhost:6379` por padrão (`REDIS_URL` env var), que não existe no Fly. **Correção**: desligar via `FLOWFUEL_RATE_LIMIT_ENABLED=false` (flag já existia no código, usada também nos testes de integração). Alternativa não aplicada: provisionar Redis externo (ex. Upstash) e setar `REDIS_URL`.
4. **`/actuator/health` retornando DOWN por causa do Mail health indicator** — com `MAIL_ENABLED=false` o envio de email é desligado, mas o Spring Boot ainda autoconfigura o `MailHealthIndicator` (porque `spring.mail.host` está definido, mesmo vazio), e ele falha por falta de credenciais. **Correção**: `MANAGEMENT_HEALTH_MAIL_ENABLED=false`.

## Pontos de atenção / pendências

- **Rate limiting está desligado em produção.** Os endpoints de auth (`/login`, `/register`, `/forgot-password`, `/resend-activation`) não têm proteção contra brute-force até que um Redis externo seja configurado e a flag seja revertida para `true`.
- **Envio de e-mail de ativação de conta está desligado** (`MAIL_ENABLED=false` não foi setado explicitamente — herda o default `false` do [application.properties](../src/main/resources/application.properties#L65)). O link de ativação só vai para o log da aplicação. Configurar SendGrid (`MAIL_*` secrets) se for necessário.
- **Senha do Postgres do Neon foi exposta em texto puro numa conversa antes de ser usada.** Recomendado resetar a senha no painel do Neon (Settings > Reset password) por precaução.
- **Upload de foto de perfil** agora usa o próprio Postgres (tabela `stored_files`, ver `docs/superpowers/specs/2026-06-18-photo-storage-in-postgres-design.md`) — sem dependência externa de storage.
- **Auto-deploy do GitHub Actions (`flyctl launch`) falhou ao tentar setar `FLY_API_TOKEN`** nos secrets do repositório (sem permissão da CLI `gh` configurada). Deploys atuais são manuais via `flyctl deploy`. Se quiser CI/CD automático, configurar `FLY_API_TOKEN` manualmente nos GitHub Secrets e criar um workflow equivalente ao [render.yaml](../render.yaml)/[ci.yml](../.github/workflows/ci.yml) (que ficou específico do Render e não é usado nesse caminho).

## Comandos úteis

```bash
flyctl status -a flowfuel-api        # estado da máquina e health checks
flyctl logs -a flowfuel-api --no-tail # logs (não usar sem --no-tail em scripts, é streaming)
flyctl secrets list -a flowfuel-api  # ver quais secrets estão setados (não mostra valores)
flyctl deploy                        # redeploy manual após mudanças no código ou fly.toml
```
