# Design: Ajuste de posição/zoom ao trocar foto de perfil e de veículo

## Contexto

Hoje, ao escolher uma foto da galeria (perfil em `ProfileScreen`, veículo em
`EditVehicleScreen` e `AddVehicleScreen`), a imagem é enviada direto:
`ImagePickerHelper.compressToJpeg(uri)` decodifica, reduz para 512px de
largura mantendo a proporção original e comprime em JPEG. Na exibição, todo
avatar (`UserAvatar`, `VehiclePhotoAvatar`) usa `ContentScale.Crop` num
container circular — ou seja, o recorte final é sempre um círculo centralizado
automaticamente, sem nenhum controle do usuário sobre qual parte da imagem
fica visível.

Isso já causa recortes ruins quando a foto não é quadrada ou quando o rosto/
objeto de interesse não está centralizado. O pedido é dar ao usuário controle
sobre isso: depois de escolher a imagem, uma tela de ajuste (arrastar +
zoom) dentro de uma máscara circular, antes do envio.

`ImagePickerHelper.compressToJpeg` também não corrige a orientação EXIF hoje
— um bug latente que não importava muito porque o recorte automático mascarava
o efeito. Ele passa a importar aqui: se o usuário reposiciona visualmente uma
imagem que a UI mostra rotacionada errado, o recorte final não vai bater com o
que ele viu na tela. Este design resolve isso corrigindo a orientação no ponto
de decodificação do crop (ver seção "Arquitetura da mudança").

## Objetivo

1. Ao escolher uma foto nova (perfil ou veículo, nos 3 fluxos existentes),
   abrir uma tela de ajuste em que o usuário arrasta (pan) e dá zoom (pinça)
   na imagem dentro de uma máscara circular, confirmando antes do envio.
2. O recorte confirmado — já corrigido de orientação e já no formato final —
   é o que é efetivamente comprimido e enviado, mantendo os pipelines de
   upload existentes (`UploadProfilePictureUseCase`, `UploadVehiclePhotoUseCase`,
   `VehicleRepositoryImpl`, `ProfileRepositoryImpl`) inalterados: eles
   continuam recebendo um `Uri` e não sabem que passou por um crop.
3. Ajustar a prévia de foto do Step 4 de `AddVehicleScreen` (hoje um quadrado
   com cantos arredondados) para circular, para bater visualmente com o
   recorte que o novo crop produz e com o avatar final exibido no resto do
   app.

## Escopo

### Dentro

- Novo componente `PhotoCropDialog` (Compose `Dialog` full-screen,
  `usePlatformDefaultWidth = false`) em
  `core/designsystem/components/PhotoCropDialog.kt`:
  - Assinatura: `PhotoCropDialog(uri: Uri, onConfirm: (Uri) -> Unit, onDismiss: () -> Unit)`.
  - Barra superior: "Cancelar" (dismiss sem aplicar nada) / título / "Confirmar".
  - Viewport circular fixo (280.dp) com máscara escura semi-transparente fora
    do círculo (mesma linguagem visual do "mover e escalonar" do Instagram).
  - Gestos via `detectTransformGestures` (pan + pinch), estado de
    `scale`/`offset` em `remember`.
  - Zoom mínimo = "cover" do círculo (a imagem nunca deixa espaço vazio
    dentro do círculo); zoom máximo = 3x o mínimo. Pan clampado para nunca
    expor área vazia em nenhum nível de zoom.
- Nova função em `ImagePickerHelper` (ou classe irmã no mesmo pacote
  `core/media`) responsável por dois passos:
  1. `loadForCropping(uri: Uri): Bitmap` — decodifica uma versão
     downsampled da imagem (mesma técnica de `inSampleSize` já usada em
     `compressToJpeg`) **já rotacionada conforme o EXIF** (ler
     `ExifInterface` a partir do `InputStream`, aplicar `Matrix.postRotate`
     conforme `ORIENTATION_ROTATE_90/180/270`/`FLIP_*`).
  2. `cropToCache(bitmap: Bitmap, scale: Float, offsetX: Float, offsetY: Float, viewportSizePx: Int): Uri` —
     replica a transform de tela (mesmos `scale`/`offset` usados no
     `graphicsLayer` da preview) em resolução real do bitmap carregado,
     recorta um quadrado via `Canvas`/`Matrix`, grava JPEG (qualidade 90) em
     `context.cacheDir/photo_crops/crop_<timestamp>.jpg` e retorna
     `Uri.fromFile(file)`.
- Integração nos 3 pontos de escolha de foto — em cada um, o callback do
  `rememberLauncherForActivityResult(PickVisualMedia())` passa a abrir
  `PhotoCropDialog(uri = pickedUri, ...)` em vez de chamar o callback do
  ViewModel direto; só no `onConfirm(croppedUri)` é que
  `viewModel.onPickImage`/`onPhotoPicked` é chamado, com o `Uri` do arquivo
  já cropado:
  - `ProfileScreen.ProfileContent` (avatar de perfil).
  - `EditVehicleScreen.EditVehiclePhotoSection` (foto do veículo já criado).
  - `AddVehicleScreen.Step4Content` (foto durante o cadastro).
- `AddVehicleScreen.Step4Content`: troca o `Box` de prévia de
  `.clip(MaterialTheme.shapes.medium)` + borda quadrada para
  `.clip(CircleShape)`, sem borda quadrada — mesma forma do círculo do crop
  e dos avatares (`UserAvatar`/`VehiclePhotoAvatar`) usados no resto do app.
- Limpeza dos arquivos temporários em `cacheDir/photo_crops/`: apagar o
  arquivo anterior (se houver) sempre que um novo crop for confirmado,
  já que o Android pode limpar `cacheDir` a qualquer momento mas não é
  garantido que o faça logo — evita acumular lixo em uso prolongado do app.
- Testes unitários da função de crop de `ImagePickerHelper` (gerar bitmap
  sintético com orientação/EXIF conhecidos, validar que o recorte final tem
  o tamanho e os pixels esperados nos cantos).

### Fora

- Reposicionar uma foto **já salva** sem escolher outra da galeria — o passo
  de ajuste só aparece no momento em que uma imagem nova é selecionada da
  galeria (decisão confirmada durante o brainstorm).
- Captura por câmera (segue só galeria, como hoje).
- Qualquer filtro, rotação manual (girar 90°) ou espelhamento — só pan e
  zoom.
- Mudança de forma do crop (retangular/livre) — sempre círculo 1:1, já que é
  o único formato de exibição usado em todo o app.

## Arquitetura da mudança

**`core/media/ImagePickerHelper.kt`**
- Adiciona `loadForCropping(uri: Uri, maxDimensionPx: Int = 1024): Bitmap` —
  decodifica com `inSampleSize` (reaproveita `calculateInSampleSize`) e
  aplica rotação EXIF lida via `androidx.exifinterface.media.ExifInterface`
  (dependência já leve e comum no ecossistema AndroidX; se ainda não estiver
  no `build.gradle`, adicionar).
- Adiciona `cropToCache(bitmap: Bitmap, scale: Float, offset: Offset, viewportSizePx: Int): Uri` —
  desenha o `bitmap` num `Canvas` de saída quadrado (`viewportSizePx`px, ex.
  800px) aplicando a mesma `Matrix` (translate + scale) usada na tela,
  comprime em JPEG e grava em
  `File(context.cacheDir, "photo_crops/crop_${System.currentTimeMillis()}.jpg")`,
  criando o diretório se necessário e apagando arquivos antigos da mesma
  pasta antes de gravar o novo.

**`core/designsystem/components/PhotoCropDialog.kt`** (novo)
- `Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false))`.
- Carrega o bitmap via `ImagePickerHelper.loadForCropping(uri)` num
  `LaunchedEffect`/`produceState`, mostrando um `CircularProgressIndicator`
  enquanto decodifica.
- `Image` com `Modifier.graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }`
  dentro de um `Box` recortado em `CircleShape`, mais uma máscara externa
  (`Canvas`/`drawRect` com `BlendMode.SrcOut` ou 4 retângulos ao redor do
  círculo) escurecendo a área fora do viewport.
- `Modifier.pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> ... } }`
  atualizando `scale`/`offset` com os clamps de zoom mínimo/máximo e pan
  descritos no Escopo.
- Botão "Confirmar": chama `ImagePickerHelper.cropToCache(...)` (em
  `viewModelScope`-like coroutine local via `rememberCoroutineScope`, já que
  é I/O) e então `onConfirm(resultUri)`.

**`ProfileScreen.kt` / `EditVehicleScreen.kt` / `AddVehicleScreen.kt`**
- Cada um ganha `var pendingCropUri by remember { mutableStateOf<Uri?>(null) }`;
  o launcher do `PickVisualMedia` seta essa variável em vez de chamar o
  ViewModel direto; `if (pendingCropUri != null) PhotoCropDialog(uri = pendingCropUri!!, onConfirm = { cropped -> pendingCropUri = null; viewModel.onPickImage(cropped) /* ou onPhotoPicked */ }, onDismiss = { pendingCropUri = null })`.
- Nenhuma mudança de assinatura nos ViewModels (`ProfileViewModel.onPickImage`,
  `EditVehicleViewModel.onPhotoPicked`, `AddVehicleViewModel.onPhotoPicked`
  continuam recebendo `Uri`).

**`AddVehicleScreen.kt` — `Step4Content`**
- Troca a forma do `Box` de prévia (linha ~536-544) de
  `.clip(MaterialTheme.shapes.medium)` + `.border(..., shape = MaterialTheme.shapes.medium)`
  para `.clip(CircleShape)` + `.border(..., shape = CircleShape)`.

## Testes

- Teste unitário de `ImagePickerHelper.cropToCache`/`loadForCropping`
  (JVM, sem Robolectric se possível — `Bitmap` puro do `android.graphics`
  funciona em teste unitário com o shadow padrão, senão usar Robolectric):
  gerar um bitmap sintético com cores conhecidas em cada quadrante, aplicar
  um `scale`/`offset` conhecido e validar que o pixel central do resultado
  corresponde ao quadrante esperado; validar que uma imagem com EXIF
  `ORIENTATION_ROTATE_90` sai com dimensões trocadas (largura/altura
  invertidas) após `loadForCropping`.
- Manual: nos 3 fluxos (perfil, editar veículo, cadastrar veículo), escolher
  uma foto retangular (não quadrada) da galeria, arrastar para posicionar um
  detalhe específico no centro do círculo, dar zoom, confirmar, e checar que
  o mesmo enquadramento aparece depois nos avatares (`ProfileScreen`,
  `VehicleDetailsScreen`, cards de lista) — sem re-recorte automático
  diferente do que foi escolhido. Testar também uma foto tirada com o
  celular em orientação vertical vinda da câmera (tende a ter EXIF não
  identidade) para confirmar que a prévia do crop já aparece na orientação
  certa.
