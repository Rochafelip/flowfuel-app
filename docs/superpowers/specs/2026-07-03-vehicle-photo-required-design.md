# Design: Foto obrigatória na criação de veículo

## Contexto

Hoje `AddVehicleScreen` (`feature/vehicle/presentation/add/`) é um wizard de
3 etapas (Identificação → Classificação → Detalhes) que cria um veículo via
`POST /vehicles`, sem nenhum campo de foto. O usuário pediu que, no fluxo em
que o usuário cria/cadastra um veículo, seja **obrigatório** anexar uma foto
do carro.

Dois achados relevantes na exploração do código:

- `VehicleResponseDTO` (backend, ver `.claude/docs_api/openapi.yaml` e
  `VehicleApi.kt`) **já tem** um campo `photo: String?` — mas ele é somente
  leitura (não existe em `VehicleRequestDTO`, usado tanto para criar quanto
  atualizar). O domínio `Vehicle` do app nem mapeia esse campo hoje.
- **Não existe** endpoint de upload de foto de veículo no backend. Existe um
  padrão equivalente para foto de perfil (`POST
  /api/v1/auth/{userId}/upload-profile-picture`, multipart, limite 5MB,
  JPEG/PNG/WEBP) que serve de referência de contrato e de padrão de código
  (`ProfileApi`, `ProfileRepositoryImpl`, `UploadProfilePictureUseCase`,
  `core/media/ImagePickerHelper.compressToJpeg`).

Este repositório é só o app Android — a implementação do endpoint de upload
de foto de veículo no backend está **fora deste plano**. Este documento
especifica o contrato REST esperado para que o time de backend implemente
em paralelo, e o app é construído contra esse contrato.

**Premissas assumidas sem confirmação explícita do usuário** (perguntadas
duas vezes, sem resposta — seguindo com a opção de menor risco/escopo em
cada caso; ajustável na revisão desta spec):

1. A foto é obrigatória em **toda** criação de veículo — não só a primeira
   pós-cadastro. `AddVehicleScreen` é uma única tela reusada nos dois casos
   (redirecionamento automático quando o usuário tem 0 veículos, e "Novo
   veículo" a partir de Perfil → Meus veículos); diferenciar exigiria uma
   flag de navegação sem ganho claro.
2. A foto vem **só da galeria** (Android Photo Picker) nesta primeira
   versão — sem captura por câmera. Evita adicionar permissão `CAMERA` e
   configuração de `FileProvider` para captura. O Photo Picker
   (`ActivityResultContracts.PickVisualMedia`) não exige permissão de
   runtime em nenhuma versão suportada (`minSdk 26`).
3. Exibir a foto em telas existentes (`VehicleDetailsScreen`, listas de
   veículos, `VehiclePickerScreen`) está **fora de escopo** — o campo
   `photo` fica disponível no domínio, mas nenhuma tela de exibição é
   alterada nesta rodada.

## Objetivo

Tornar obrigatório selecionar uma foto do veículo antes de conseguir criar
o veículo em `AddVehicleScreen`, com upload da foto para o backend logo
após a criação bem-sucedida do registro do veículo.

## Escopo

### Dentro

- Novo 4º passo no wizard de `AddVehicleScreen` ("Foto"), depois do atual
  Step 3 (Detalhes). `WizardStepper` passa a ter 4 círculos/labels.
- Seleção de foto via `ActivityResultContracts.PickVisualMedia`, preview da
  imagem escolhida (Coil `AsyncImage`), opção de trocar a foto escolhida.
- CTA final do Step 4 ("Adicionar veículo") desabilitado até uma foto ser
  escolhida. **Sem** botão "Preencher depois" no Step 4 — o Step 3 mantém o
  seu próprio botão "Preencher depois" inalterado (continua pulando só
  placa/cor/odômetro/capacidade, como hoje).
- Fluxo de submissão em duas chamadas sequenciais, não atômicas:
  1. `POST /vehicles` (JSON, como já existe hoje, sem foto) → retorna
     `vehicleId`.
  2. `POST /vehicles/{id}/photo` (multipart, novo) com a imagem comprimida
     via `ImagePickerHelper.compressToJpeg` (reuso do helper existente).
  3. Só navega de volta (`AddVehicleEffect.NavigateBack`) quando as duas
     chamadas tiverem sucesso.
- Tratamento de falha parcial: se a criação do veículo for bem-sucedida mas
  o upload da foto falhar, o `vehicleId` recebido fica guardado no estado
  da ViewModel e a UI mostra erro com ação "Tentar novamente" que reenvia
  **só a foto** (não recria o veículo, não perde os outros dados já
  digitados). Se o usuário sair da tela nesse estado intermediário, o
  veículo permanece criado sem foto — não há rollback automático (DELETE)
  do veículo; é um caso raro e aceito.
- Novo endpoint no client Android (`VehicleApi.uploadVehiclePhoto`), novo
  método de repositório (`VehicleRepository.uploadVehiclePhoto`), novo
  `UploadVehiclePhotoUseCase` — todos espelhando o padrão já usado para
  foto de perfil.
- Novas strings de UI (label do 4º passo, mensagem de erro de upload,
  texto do botão "Tentar novamente", texto explicativo do passo).

### Fora

- Implementação do endpoint de backend (`POST /vehicles/{id}/photo`) — só o
  contrato é especificado aqui; a implementação é de outro time/repo.
- Captura de foto via câmera.
- Exibição da foto em `VehicleDetailsScreen`, cards de lista de veículos ou
  `VehiclePickerScreen`.
- Edição/troca de foto depois de criado o veículo (`EditVehicleScreen`
  continua sem esse campo).
- Migração ou tratamento especial de veículos já existentes sem foto
  (continuam com `photo: null`, sem impacto).
- Diferenciar "obrigatório só no primeiro veículo" vs. "obrigatório sempre"
  — ver premissa 1 acima.

## Contrato de backend esperado (a implementar externamente)

```
POST /api/v1/vehicles/{id}/photo
Content-Type: multipart/form-data
Part "file": binary, JPEG/PNG/WEBP, máx. 5MB (mesmos limites documentados
             para /auth/{userId}/upload-profile-picture)

200 OK:
  { "photo": "<url ou path interno>" }   -- mesmo campo que já existe em
                                             VehicleResponseDTO

400 Bad Request: arquivo ausente, tipo inválido, ou tamanho excedido
403 Forbidden: veículo não pertence ao usuário autenticado
404 Not Found: veículo não existe
```

## Arquitetura da mudança (app Android)

**`AddVehicleUiState`** (`AddVehicleViewModel.kt`) ganha:
- `photoUri: Uri? = null`
- `createdVehicleId: Int? = null` (preenchido após o passo 1 da submissão,
  usado para o retry do upload)
- `photoUploadError: AppError? = null`
- `canSubmit` no Step 4 passa a exigir `photoUri != null` além das
  validações já existentes.

**`AddVehicleViewModel`**:
- `onPhotoPicked(uri: Uri)` — seta `photoUri`, limpa erro.
- `onNextStep()` ganha o caso `3 -> currentStep = 4` (sem validação
  adicional, já que Step 3 mantém sua própria validação de placa).
- `submit()` reestruturado: se `createdVehicleId == null`, cria o veículo
  primeiro; em qualquer caso (novo ou retry), tenta o upload da foto usando
  `createdVehicleId`. Só em sucesso total dispara `NavigateBack`.

**`AddVehicleScreen.kt`**:
- `WizardStepper` recebe 4 labels em vez de 3.
- Novo `Step4Content`: launcher de `PickVisualMedia`, preview via
  `AsyncImage`, botão "Trocar foto" quando já há uma selecionada, texto de
  erro + botão "Tentar novamente" quando `photoUploadError != null`.
- `AnimatedContent` do wizard passa a tratar `step == 4`.
- Bottom bar: CTA vira "Adicionar veículo" (mandatório) só no step 4;
  "Preencher depois" continua restrito ao step 3.

**Camada de dados** (espelhando `ProfileRepositoryImpl`/`ProfileApi`):
- `VehicleApi.kt`: `@Multipart @POST("vehicles/{id}/photo") suspend fun
  uploadVehiclePhoto(@Path("id") id: Int, @Part file: MultipartBody.Part):
  VehiclePhotoResponseDto` (novo DTO simples com `photo: String?`).
- `VehicleRepository` / `VehicleRepositoryImpl`: novo método
  `uploadVehiclePhoto(vehicleId: Int, uri: Uri): AppResult<String>`,
  usando `ImagePickerHelper.compressToJpeg` do mesmo jeito que
  `ProfileRepositoryImpl.uploadProfilePicture`.
- `UploadVehiclePhotoUseCase` novo, injetado na `AddVehicleViewModel`.

## Testes

- ViewModel: novo teste cobrindo `canSubmit` falso sem foto no step 4,
  sucesso total (criação + upload), falha no upload com retry usando o
  `createdVehicleId` já existente (não deve chamar `createVehicle`
  novamente), e falha na criação (comportamento atual, inalterado).
- Manual: fluxo completo de criação de veículo (todos os 4 passos) contra
  o backend real só é testável fim-a-fim depois que o endpoint de upload
  existir lá. Até lá, testar isoladamente a UI do Step 4 (seleção, preview,
  troca de foto, estado de erro simulado) e o `canSubmit`.
