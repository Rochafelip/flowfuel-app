# Design: Exibição e troca da foto do veículo

## Contexto

`docs/superpowers/plans/2026-07-03-vehicle-photo-required.md` (já 100%
implementado) tornou a foto obrigatória na criação do veículo via
`POST /vehicles/{id}/photo`. Naquele momento, exibir a foto em outras telas e
editá-la depois de criado o veículo foram explicitamente marcados como fora
de escopo (ver `docs/superpowers/specs/2026-07-03-vehicle-photo-required-design.md`,
seção "Fora").

O backend agora implementou os dois endpoints complementares que já estavam
documentados como "a considerar depois" em `docs/upload-foto-veiculo.md`:

- `GET /api/v1/vehicles/{id}/photo` — bytes da imagem (200) ou 204 se o
  veículo não tem foto. Mesmo formato do já existente
  `GET /api/v1/auth/{userId}/profile-picture`.
- `DELETE /api/v1/vehicles/{id}/photo` — remove a foto (204 sem corpo). Mesmo
  formato do já existente `DELETE` de foto de perfil.

Isso desbloqueia a próxima etapa natural: mostrar a foto já enviada na
criação e permitir trocá-la depois.

**Premissa a verificar empiricamente:** `VehicleResponseDTO.photo` (campo já
existente no schema, hoje sempre `null` porque nada escreve nele) deve passar
a vir populado pelo backend após o upload, com o mesmo formato de URL interna
relativa usado em `UserResponseDto.profilePicture` (ex.:
`/api/v1/vehicles/{id}/photo`). Este documento já foi corrigido uma vez antes
por assumir um contrato de resposta que não batia com a implementação real
do backend (`{photo}` vs `{internalUrl}`, ver commit `d3872c8`) — por isso a
Task de verificação manual no plano de implementação deve confirmar o
formato real antes de considerar a feature concluída, ajustando a lógica de
montagem de URL se necessário.

## Objetivo

1. Exibir a foto do veículo (quando existir) em `VehicleDetailsScreen`,
   com fallback para o ícone de tipo de veículo (carro/moto) quando não
   houver foto.
2. Permitir trocar a foto em `EditVehicleScreen`, reenviando via o mesmo
   `POST /vehicles/{id}/photo` já implementado (sobrescreve a foto anterior
   no backend — não é preciso chamar DELETE antes).
3. Implementar o client para `DELETE /vehicles/{id}/photo`
   (`VehicleApi`/`VehicleRepository`/Use Case) para ficar disponível, mesmo
   sem nenhum botão de UI chamando-o nesta rodada — todo veículo deve
   continuar tendo uma foto após criado, então "remover e ficar sem foto"
   não é uma ação exposta ao usuário agora.

## Escopo

### Dentro

- `Vehicle` (domínio) ganha `photoUrl: String?`, mapeado em
  `VehicleRepositoryImpl.toDomain()` a partir de `VehicleResponseDto.photo`,
  espelhando exatamente a lógica de `UserResponseDto.toDomain()` para
  `profilePictureUrl` (`BuildConfig.API_BASE_URL.trimEnd('/') + it`, já que é
  endpoint autenticado — o Coil `ImageLoader` global já compartilha o
  `OkHttpClient` autenticado, ver `NetworkModule.kt`).
- `VehicleApi.deleteVehiclePhoto(id: Int)`, `VehicleRepository.deletePhoto`,
  `DeleteVehiclePhotoUseCase` — mesmo padrão de `deleteVehicle`/
  `ProfileApi.deleteProfilePicture` (`ResponseBody?`/try-catch manual, já que
  204 não passa pelo conversor JSON).
- `VehicleDetailsScreen`: `VehicleHeader` mostra `vehicle.photoUrl` via
  `AsyncImage` (fallback por trás sempre renderizado = ícone atual de
  tipo), sem nenhuma ação de edição — só exibição, mesmo padrão visual do
  círculo de 64dp já existente.
- `EditVehicleScreen`: novo bloco de foto no topo do formulário — avatar
  clicável com badge de câmera (mesmo visual de `ProfileScreen`), abre
  `PickVisualMedia` ao tocar, upload imediato ao escolher (não fica pendente
  do botão "Salvar"), com `isUploadingPhoto`/`photoUploadError` próprios no
  `EditVehicleUiState`, independentes do `isDirty`/diálogo de descarte que já
  existe para os outros campos do formulário.
- Testes de `EditVehicleViewModel` cobrindo sucesso e falha do upload de
  foto, mesmo padrão de `ProfileViewModelTest`.
- Verificação manual contra o backend real confirmando o formato da URL da
  foto (ver premissa acima).

### Fora

- Nenhum botão de "remover foto" na UI (`DeleteVehiclePhotoUseCase` fica
  implementado e testável, mas não é chamado de nenhuma tela).
- Nenhuma mudança em `VehiclesScreen`/`VehiclePickerScreen` (cards de lista
  continuam sem thumbnail de foto).
- Captura por câmera (segue só galeria, como no cadastro).
- Fluxo de "trocar foto" via DELETE + POST — é só POST sobrescrevendo.

## Arquitetura da mudança

**`VehicleModels.kt`** — `Vehicle` ganha `val photoUrl: String?`.

**`VehicleApi.kt`** — novo método:
```kotlin
@DELETE("vehicles/{id}/photo")
suspend fun deleteVehiclePhoto(@Path("id") id: Int): ResponseBody?
```

**`VehicleRepository`/`VehicleRepositoryImpl`**:
- `toDomain()` ganha `photoUrl = photo?.let { BuildConfig.API_BASE_URL.trimEnd('/') + it }`.
- Novo `deletePhoto(vehicleId: Int): AppResult<Unit>`, try-catch manual
  igual a `deleteVehicle`/`setActiveVehicle`.

**`DeleteVehiclePhotoUseCase`** — novo, passthrough fino, mesmo padrão de
`UploadVehiclePhotoUseCase`.

**`VehicleDetailsScreen.kt`** — `VehicleHeader` recebe `vehicle.photoUrl` e
renderiza `AsyncImage` por cima do ícone de fallback quando não nulo (mesma
técnica de "fallback sempre atrás, imagem crossfade por cima" de
`UserAvatar.kt`), sem `onClick`.

**`EditVehicleUiState`/`EditVehicleViewModel`**:
- Novos campos: `photoUrl: String? = null`, `isUploadingPhoto: Boolean = false`,
  `photoUploadError: AppError? = null`.
- `load()` popula `photoUrl` a partir do veículo carregado.
- Novo `onPhotoPicked(uri: Uri)`: seta `isUploadingPhoto = true`, chama
  `UploadVehiclePhotoUseCase(vehicleId, uri)`; sucesso atualiza `photoUrl`
  no estado; falha seta `photoUploadError` — mesma estrutura de
  `ProfileViewModel.onPickImage`. Não passa por `updateWithDirtyCheck` (não
  é campo do formulário "Salvar").

**`EditVehicleScreen.kt`** — novo bloco de foto no topo (antes ou logo após
o header existente), reaproveitando `rememberLauncherForActivityResult` +
`PickVisualMedia` do mesmo jeito que `AddVehicleScreen`/`ProfileScreen`.

## Testes

- `EditVehicleViewModelTest`: `onPhotoPicked` sucesso atualiza `photoUrl` e
  zera `isUploadingPhoto`; falha seta `photoUploadError` e zera
  `isUploadingPhoto`; `load()` popula `photoUrl` inicial a partir do
  veículo retornado.
- Manual: fluxo completo no emulador contra `flowfuel-api.fly.dev` — criar
  ou usar veículo com foto já enviada, abrir `VehicleDetailsScreen` e
  confirmar que a foto aparece (não só o ícone), abrir `EditVehicleScreen`,
  trocar a foto e confirmar que `VehicleDetailsScreen` reflete a nova foto
  ao voltar.
