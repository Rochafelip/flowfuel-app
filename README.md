# FlowFuel

App Android para controle de combustível e manutenção de veículos, com prontuário financeiro por veículo (abastecimentos, manutenções e outras despesas).

## Stack

- Kotlin + Jetpack Compose (Material 3)
- Arquitetura MVVM (ViewModel + StateFlow)
- Hilt para injeção de dependência
- Retrofit + OkHttp + kotlinx.serialization para acesso à API
- Room + DataStore para persistência local
- WorkManager para tarefas em background
- Coil para carregamento de imagens
- Sentry para monitoramento de crashes
- Testes com JUnit5, MockK, Turbine e Robolectric

## Estrutura

```
app/src/main/java/com/flowfuel/app/
├── core/          # infraestrutura compartilhada (rede, DI, design system, etc.)
├── feature/
│   ├── auth/          # login e ativação de conta
│   ├── onboarding/    # fluxo inicial
│   ├── home/          # tela principal
│   ├── vehicle/       # cadastro e gestão de veículos
│   ├── vehicleevent/  # eventos financeiros do veículo (abastecimentos, manutenções, despesas)
│   └── history/       # histórico
└── navigation/    # grafo de navegação
```

## Build

Requer JDK 17 e Android SDK 35.

```bash
./gradlew assembleDebug
```

Configurações locais (URL da API de desenvolvimento, DSN do Sentry, keystore de release) vão em `local.properties` — veja o template comentado nesse arquivo.

## Release

Builds de release são assinados e publicados automaticamente como GitHub Release sempre que uma tag `vX.Y.Z` é enviada ao repositório (ver `.github/workflows/release.yml`).

```bash
git tag v0.1.1
git push origin v0.1.1
```
