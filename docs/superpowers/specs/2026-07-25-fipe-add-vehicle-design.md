# Integração FIPE no cadastro de veículo (AddVehicleScreen)

**Data:** 2026-07-25
**Status:** Aprovado, aguardando plano de implementação

## Contexto

Hoje o Step 1 (`Identificação`) do wizard de `AddVehicleScreen.kt` tem 4
campos de texto livre — Marca, Modelo, Ano de fabricação, Ano do modelo
(`Step1Content`, `AddVehicleScreen.kt:259-349`) — sem nenhuma validação além
de "obrigatório" e formato numérico de 4 dígitos
(`AddVehicleViewModel.onNextStep()`, linhas 158-174). Isso permite marca/
modelo digitados de qualquer forma (erro de digitação, abreviação,
capitalização inconsistente), sem padronização.

`POST /vehicles` já aceita `brand: String`, `model: String`,
`manufactureYear: Int`, `modelYear: Int`
(`CreateVehicleRequestDto`, `VehicleApi.kt:30-43`) — **nenhuma mudança de
backend é necessária**. Este spec é puramente client-side: troca a forma de
preencher esses 4 campos por uma consulta em cascata à tabela FIPE
(Marca → Modelo → Ano), mantendo o mesmo contrato de envio.

`VehicleType` só tem dois valores hoje (`Car`, `Motorcycle`,
`VehicleModels.kt:3`), o que mapeia 1:1 com as duas categorias FIPE que nos
interessam (`carros`, `motos`).

Ver [[project_vehicles_module]] para o mapa geral de features do módulo.

## Requisitos

1. **Reordenar o Step 1**: o seletor `VehicleTypeSelector` (hoje em
   `Step2Content`, seção "Tipo do veículo") sobe para o topo do Step 1,
   pois define qual categoria FIPE consultar. Ele some do Step 2, que passa
   a ter só "Tipo de energia" (e "Tipo de combustível" condicional).
2. **Marca** (dropdown): lista carregada da FIPE para o tipo selecionado.
   Ao trocar o Tipo, a lista é recarregada e a seleção de Marca/Modelo/Ano
   é descartada.
3. **Modelo** (dropdown): desabilitado até uma Marca ser escolhida; lista
   carregada por marca+tipo. Trocar a Marca descarta a seleção de
   Modelo/Ano.
4. **Ano do modelo** (dropdown): desabilitado até um Modelo ser escolhido;
   lista carregada por marca+modelo+tipo. Ao selecionar um item (ex.:
   `"2016 Gasolina"`), extrai o ano numérico e preenche **tanto**
   `modelYear` quanto `manufactureYear` com esse valor.
5. **Ano de fabricação** continua como `FFTextField` numérico editável
   (mesmo campo/validação de hoje), pré-preenchido pela seleção do item 4,
   mas o usuário pode ajustá-lo manualmente depois (caso comum de
   fabricação no ano anterior ao do modelo).
6. **Fallback manual**: um botão de texto "Não encontrei meu veículo"
   alterna Marca/Modelo/Ano-do-modelo de volta para os 3 campos de texto
   livre originais (Marca, Modelo, Ano do modelo — "Ano de fabricação" já é
   sempre um campo de texto, não muda). Um segundo toque ("Usar busca
   FIPE") volta ao modo dropdown. A troca de modo não apaga o que já foi
   digitado/selecionado.
7. Falha ao carregar qualquer nível da cascata (rede indisponível, timeout,
   erro do serviço) mostra um erro inline junto ao dropdown afetado, com
   ação de tentar novamente — sem bloquear o restante do formulário nem
   forçar o fallback manual (o usuário pode optar por ele, mas não é
   obrigatório).
8. Validação existente do Step 1 (`onNextStep()`) não muda: Marca/Modelo
   não podem ficar em branco, ambos os anos precisam ser inteiros de 4
   dígitos. Vale tanto para valores vindos da FIPE quanto do modo manual.

## Fora de escopo

- `EditVehicleScreen` não é alterado — o pedido foi especificamente sobre a
  tela de cadastro (`AddVehicleScreen`). Os 4 campos de texto livre
  continuam como estão na edição.
- Usar o ano/combustível retornado pela FIPE (ex.: "Gasolina") para
  pré-preencher "Tipo de combustível" no Step 2 — mencionado como possível
  bônus futuro, mas não pedido; manter os steps independentes por ora.
- Autocomplete com busca por texto nos dropdowns — decisão explícita pelo
  formato mais simples (dropdown com lista completa), já que a FIPE não tem
  volume grande o suficiente para justificar a complexidade de debounce/
  filtro numa primeira versão.
- Suporte a caminhões — a FIPE tem uma terceira categoria (`caminhoes`),
  mas o app não tem esse `VehicleType`; fora de escopo até existir.

## Arquitetura

**Provedor de dados:** `parallelum.com.br/fipe/api/v1` — API pública,
gratuita, sem chave/cadastro. Testada manualmente nesta sessão e
funcionando (a alternativa Brasil API estava retornando 403 no momento do
teste — proxy dela para o FIPE quebrado). Estrutura confirmada:

```
GET /{tipo}/marcas                              → [{codigo, nome}, ...]
GET /{tipo}/marcas/{marcaCodigo}/modelos        → {modelos: [{codigo, nome}, ...]}
GET /{tipo}/marcas/{marcaCodigo}/modelos/{modeloCodigo}/anos
                                                 → [{codigo: "2016-1", nome: "2016 Gasolina"}, ...]
```
onde `tipo` é `carros` para `VehicleType.Car` e `motos` para
`VehicleType.Motorcycle`. O ano é extraído de `codigo` (parte antes do
`-`) ou dos 4 primeiros dígitos de `nome`.

**Rede (`core/network/NetworkModule.kt`):** hoje existem duas instâncias de
Retrofit/OkHttp, ambas com `BuildConfig.API_BASE_URL` — a principal
(autenticada) e uma exclusiva para refresh de token (`@Named("refresh")`).
Este spec adiciona uma **terceira**, seguindo o mesmo padrão:
- `@Provides @Singleton @Named("fipe") fun provideFipeOkHttp(...)`: sem
  `AuthInterceptor`/`TokenRefreshAuthenticator` (API pública, sem auth),
  com `chucker` e `logging` como as demais, timeout menor (conexão/leitura
  em ~10s, já que é uma dependência externa fora do nosso controle e não
  deve travar o wizard).
- `@Provides @Singleton @Named("fipe") fun provideFipeRetrofit(...)`:
  `baseUrl("https://parallelum.com.br/fipe/api/v1/")`, mesmo
  `Json`/converter kotlinx serialization já usado nas outras duas.

**Camada de dados** (novo pacote `feature/vehicle/data/remote/fipe/`):
- `FipeApi.kt` — interface Retrofit com os 3 endpoints acima
  (`FipeBrandDto(codigo: String, nome: String)`,
  `FipeModelsResponseDto(modelos: List<FipeModelDto>)`,
  `FipeModelDto(codigo: Int, nome: String)`,
  `FipeYearDto(codigo: String, nome: String)`).
- `FipeRepositoryImpl` implementa `FipeRepository`
  (`feature/vehicle/domain/repository/FipeRepository.kt`):
  ```
  suspend fun getBrands(vehicleType: VehicleType): AppResult<List<FipeOption>>
  suspend fun getModels(vehicleType: VehicleType, brandCode: String): AppResult<List<FipeOption>>
  suspend fun getYears(vehicleType: VehicleType, brandCode: String, modelCode: String): AppResult<List<FipeOption>>
  ```
  Cada chamada usa o helper existente `apiCall { ... }`
  (`core/network/ApiCall.kt`) para mapear para `AppResult`/`AppError`,
  igual a todos os outros repositórios do projeto (`AppError.Network` para
  `IOException`/timeout, `AppError.Api` para HTTP de erro, etc. — o parsing
  de `ProblemDetails` simplesmente não encontra nada no corpo da FIPE e cai
  no fallback de `e.message()`, sem quebrar).
  Mapeamento `VehicleType → "carros"/"motos"` fica como função privada
  dentro do repositório.
- `FipeOption(val code: String, val name: String)` — modelo de domínio
  genérico reaproveitado pelas 3 listas
  (`feature/vehicle/domain/model/FipeModels.kt`).
- DI: `@Module @InstallIn(SingletonComponent)` novo (ou adicionado ao
  módulo de vehicle existente) com `@Binds` de `FipeRepository`, seguindo o
  padrão já estabelecido.

**Use cases** (mesma granularidade de `CreateVehicleUseCase`/
`UploadVehiclePhotoUseCase`): `GetFipeBrandsUseCase`, `GetFipeModelsUseCase`,
`GetFipeYearsUseCase`, cada um um wrapper fino de uma função do
`FipeRepository`.

**`AddVehicleUiState`** ganha:
```kotlin
val useFipeSearch: Boolean = true,
val fipeBrands: List<FipeOption> = emptyList(),
val fipeModels: List<FipeOption> = emptyList(),
val fipeYears: List<FipeOption> = emptyList(),
val selectedFipeBrandCode: String? = null,
val selectedFipeModelCode: String? = null,
val fipeBrandsLoading: Boolean = false,
val fipeModelsLoading: Boolean = false,
val fipeYearsLoading: Boolean = false,
val fipeError: String? = null,
```
`brand`, `model`, `manufactureYear`, `modelYear` continuam exatamente como
estão (destino final, tanto do fluxo FIPE quanto do manual).

**`AddVehicleViewModel`** ganha:
- `init { loadFipeBrands() }` — carrega marcas para `VehicleType.Car`
  (default) assim que a tela abre.
- `onVehicleTypeChange(v)` (já existe, hoje só usado no Step 2 — passa a
  ser chamado do Step 1): além de atualizar `vehicleType`, limpa
  brand/model/years/selectedCodes/fipeModels/fipeYears e chama
  `loadFipeBrands()` de novo.
- `onFipeBrandSelected(option: FipeOption)`: seta `brand = option.name`,
  `selectedFipeBrandCode = option.code`, limpa model/year e `fipeYears`,
  chama `loadFipeModels()`.
- `onFipeModelSelected(option: FipeOption)`: seta `model = option.name`,
  `selectedFipeModelCode = option.code`, limpa year, chama
  `loadFipeYears()`.
- `onFipeYearSelected(option: FipeOption)`: extrai o ano e faz
  `modelYear = ano; manufactureYear = ano` (sobrescrevendo edição manual
  anterior — a seleção do dropdown é sempre a fonte da verdade no momento
  em que é feita; o usuário pode reeditar `manufactureYear` depois).
- `onToggleManualEntry()`: inverte `useFipeSearch`; não mexe em nenhum
  outro campo.
- `loadFipeBrands()/loadFipeModels()/loadFipeYears()`: cada um seta a
  flag de loading correspondente, chama o use case, em sucesso preenche a
  lista e limpa `fipeError`; em falha seta `fipeError` com mensagem curta
  (reaproveita `AppError` já existente convertido a texto, mesmo padrão de
  outras telas).
- `onManufactureYearChange`/`onModelChange`/`onBrandChange`/
  `onModelYearChange` (já existem) continuam sendo os handlers do modo
  manual — sem mudança de assinatura.

**UI (`AddVehicleScreen.kt`)**:
- Novo composable reutilizável `FFDropdownField` (em
  `core/designsystem/components/`, ao lado de `FFTextField`) envolvendo
  `ExposedDropdownMenuBox` do Material 3: label, valor selecionado
  (read-only), lista de `FipeOption`, estado de loading (ícone de progresso
  no trailing), estado de erro com texto + "Tentar novamente", `enabled`
  (para desabilitar Modelo/Ano até o passo anterior ser escolhido).
- `Step1Content` reorganizado:
  1. `VehicleTypeSelector` (movido de `Step2Content`, mesmo composable).
  2. Se `state.useFipeSearch`: 3× `FFDropdownField` (Marca/Modelo/Ano do
     modelo) + `FFTextField` de "Ano de fabricação" (como hoje) + texto
     "Não encontrei meu veículo".
     Senão: os 4 `FFTextField`s atuais (Marca, Modelo, Ano de fabricação,
     Ano do modelo) + texto "Usar busca FIPE".
- `Step2Content` perde a seção "Tipo do veículo" (só sobra Energia/
  Combustível).

**Strings novas** (`strings.xml`): `vehicle_fipe_not_found` ("Não encontrei
meu veículo"), `vehicle_fipe_use_search` ("Usar busca FIPE"),
`vehicle_fipe_load_error` ("Não foi possível carregar. Toque para tentar
novamente."), `vehicle_fipe_select_brand`/`_model`/`_year` (placeholders
dos dropdowns). Reaproveita `vehicle_brand`, `vehicle_model`,
`vehicle_manufacture_year`, `vehicle_model_year` já existentes como labels.

## Erros e casos de borda

- **API FIPE fora do ar / timeout**: erro inline no dropdown afetado, com
  retry; os demais campos do formulário continuam usáveis; "Não encontrei
  meu veículo" sempre disponível como saída.
- **Veículo raro/importado fora da tabela FIPE**: mesmo caminho do item
  acima — usuário troca pro modo manual.
- **Usuário troca o Tipo depois de já ter escolhido Marca/Modelo/Ano**:
  seleção é descartada e a cascata reinicia da Marca (evita inconsistência
  tipo carro + modelo de moto).
- **Usuário volta do Step 2 pro Step 1** (`onPreviousStep`): estado FIPE já
  carregado permanece (não recarrega do zero), igual ao comportamento hoje
  para os campos de texto.
- **Erro de validação do servidor** (`serverErrors` por campo, ex.:
  `brand`/`model`/`manufactureYear`/`modelYear`): continua funcionando
  igual — os `FFDropdownField`/`FFTextField` exibem a mensagem do backend
  do mesmo jeito que hoje, independente do valor ter vindo da FIPE ou
  digitado manualmente.
- **Resposta da FIPE com corpo inesperado** (mudança de schema upstream):
  `SerializationException` já é tratada por `apiCall` como
  `AppError.Unknown`, cai no mesmo erro inline com retry.

## Testes

- `AddVehicleViewModelTest`: testes novos para
  `onVehicleTypeChange_recarregaMarcasELimpaSelecaoAnterior`,
  `onFipeBrandSelected_carregaModelosELimpaAnoSelecionado`,
  `onFipeModelSelected_carregaAnos`,
  `onFipeYearSelected_preencheAnoFabricacaoEAnoModeloIguais`,
  `onToggleManualEntry_alternaSemLimparCampos`, e um caso de falha
  (`loadFipeBrands_falhaDeRedeSetaFipeError`) — seguindo o padrão de testes
  de ViewModel já usado no projeto (fakes de use case retornando
  `AppResult.Success`/`Failure`).
- `FipeRepositoryImplTest` (se existir padrão de teste de repositório no
  projeto — verificar durante o plano; senão, cobrir só via
  `AddVehicleViewModelTest` com fake do `FipeRepository`).
- Sem testes de UI Compose (padrão estabelecido do projeto, ver
  [[project_architecture]]).
- Verificação manual no emulador (conta de QA, ver
  [[project_qa_test_account]]):
  1. Abrir "Cadastrar veículo", confirmar que Marca já vem carregada da
     FIPE para "Carro" (default).
  2. Selecionar Marca → Modelo → Ano do modelo; confirmar que "Ano de
     fabricação" foi preenchido igual; editar manualmente e confirmar que
     fica com o valor editado.
  3. Trocar Tipo para "Moto"; confirmar que a lista de Marca recarrega e
     reflete marcas de moto.
  4. Tocar "Não encontrei meu veículo"; confirmar troca para os 4 campos de
     texto livre; preencher manualmente e completar o cadastro.
  5. Desligar a rede do emulador antes de abrir a tela; confirmar erro
     inline com retry no dropdown de Marca, sem travar a tela.
  6. Completar um cadastro via fluxo FIPE do início ao fim (Step 1 a 4) e
     confirmar no backend/Chucker que o payload de `POST /vehicles` chega
     idêntico ao formato atual (`brand`/`model` como string, anos como
     int).
