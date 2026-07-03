# Design: Foto do veículo nos cards de lista (FFVehicleCard)

## Contexto

`docs/superpowers/specs/2026-07-03-vehicle-photo-display-edit-design.md` (já
100% implementado) trouxe a foto do veículo para `VehicleDetailsScreen` via
`VehiclePhotoAvatar`, mas excluiu explicitamente os cards de lista de
veículos do escopo ("Nenhuma mudança em VehiclesScreen/VehiclePickerScreen —
cards de lista continuam sem thumbnail de foto").

Verificação manual (2026-07-03): usuário reportou que, em "Meus Veículos" e
no seletor de veículo, o ícone de carro fixo (verde) continua aparecendo
mesmo para veículos novos, cadastrados já com foto obrigatória. Investigação
de causa raiz confirmou que **não é um bug** — é o gap de escopo acima.
`VehicleDetailsScreen` já funciona corretamente; os cards de lista nunca
foram tocados.

## Objetivo

Exibir a foto do veículo (com fallback para o ícone de tipo) em todo lugar
que hoje usa `FFVehicleCard`, para eliminar a inconsistência visual entre
`VehicleDetailsScreen` (já mostra foto) e as telas de lista (ainda mostram
ícone fixo).

## Escopo

### Dentro

- `FFVehicleCard` (design system) ganha dois parâmetros novos:
  `photoUrl: String? = null` e `vehicleType: VehicleType = VehicleType.Car`.
  O `Box`/`Surface`/`Icon` fixo hoje em `FFVehicleCard.kt:34-47` é substituído
  por `VehiclePhotoAvatar(photoUrl = photoUrl, vehicleType = vehicleType,
  size = 48.dp)` — mesmo componente já usado em `VehicleDetailsScreen`, sem
  `onClick` (cards de lista não editam foto, só navegam/selecionam).
- Call sites atualizados para passar `vehicle.photoUrl` e `vehicle.type`:
  - `VehiclesScreen.kt:249` (Meus Veículos)
  - `VehiclePickerScreen.kt:162` (seletor de veículo, tela cheia)
  - `VehicleSwitcherBottomSheet.kt:74` (troca rápida de veículo na Home,
    bottom sheet)
- `UiKitDemoScreen.kt:99` — chamada de showcase permanece sem alteração
  (usa os defaults `photoUrl = null`, `vehicleType = VehicleType.Car`,
  preservando o ícone de fallback no catálogo de componentes).

### Fora

- Nenhuma mudança em `VehicleDetailsScreen`/`EditVehicleScreen` (já
  implementado e correto).
- Nenhuma mudança em paginação, cache ou nos endpoints — consumo de um
  campo (`Vehicle.photoUrl`) que já existe no domínio desde a implementação
  anterior.
- Nenhum novo teste automatizado: `FFVehicleCard` e as telas que o consomem
  não têm testes de UI/Compose hoje (mesmo padrão do resto do design
  system); verificação é manual.

## Arquitetura da mudança

**`FFVehicleCard.kt`**:
```kotlin
@Composable
fun FFVehicleCard(
    nickname: String,
    plate: String,
    odometerKm: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    photoUrl: String? = null,
    vehicleType: VehicleType = VehicleType.Car,
    onClick: (() -> Unit)? = null,
) {
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, onClick = onClick) {
        Row(...) {
            VehiclePhotoAvatar(photoUrl = photoUrl, vehicleType = vehicleType, size = 48.dp)
            Column(...) { /* inalterado */ }
        }
    }
}
```

Remove o `Box`/`Surface`/`Icon`/imports (`CircleShape`, `Icons.Default.DirectionsCar`)
que ficam órfãos em `FFVehicleCard.kt`, e adiciona o import de
`VehiclePhotoAvatar` (mesmo pacote `core.designsystem.components`, sem
import cross-package) e de `VehicleType`
(`com.flowfuel.app.feature.vehicle.domain.model.VehicleType` — já é uma
dependência aceita, `VehiclePhotoAvatar` já importa do mesmo pacote).

**Call sites** — cada um adiciona duas linhas ao `FFVehicleCard(...)` já
existente:
```kotlin
photoUrl = vehicle.photoUrl,
vehicleType = vehicle.type,
```

Nenhuma mudança de assinatura de função contentedora, nenhum novo estado —
`vehicle` já está no escopo de todas as três chamadas.

## Testes

- Compilar após a mudança em `FFVehicleCard.kt` (sem teste automatizado
  pré-existente a rodar contra este componente).
- Rodar a suíte completa de testes unitários para checar regressão (os
  ViewModels de `VehiclesScreen`/`VehiclePickerScreen`/`VehicleSwitcherBottomSheet`
  não mudam, mas confirma que nada mais quebrou).
- Manual, no emulador contra `flowfuel-api.fly.dev`: abrir "Meus Veículos"
  e o seletor de veículo (tela cheia e bottom sheet da Home) com um veículo
  que já tem foto (criado após o wizard de foto obrigatória, ou com foto
  adicionada via `EditVehicleScreen`) e confirmar que a foto aparece nos
  três lugares — não só o ícone.
