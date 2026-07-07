# Spec — Home Screen (App de Controle de Abastecimento)

> Documento de referência externo (recebido fora do repositório), citado em
> [`docs/superpowers/specs/2026-07-07-home-upcoming-maintenance-design.md`](superpowers/specs/2026-07-07-home-upcoming-maintenance-design.md).
> Gerado a partir do prompt em [`docs/FlowFuel_Home_Spec_Prompt.md`](FlowFuel_Home_Spec_Prompt.md).
>
> **Status:** a `HomeScreen` real do app **não** segue este documento
> literalmente — ela usa o design system próprio do projeto (`FFCard`,
> `FFStatTile`, `FFTrendBadge`, tokens em
> `app/src/main/java/com/flowfuel/app/core/designsystem/theme/Color.kt`),
> definido nas specs nativas do repo:
> [`2026-06-28-design-system-colors-typography.md`](superpowers/specs/2026-06-28-design-system-colors-typography.md),
> [`2026-07-06-home-dashboard-design.md`](superpowers/specs/2026-07-06-home-dashboard-design.md)
> e [`2026-07-07-home-upcoming-maintenance-design.md`](superpowers/specs/2026-07-07-home-upcoming-maintenance-design.md).
> `TopBar` (menu hambúrguer) e FAB "docked" na bottom bar, descritos abaixo,
> seguem fora de escopo do app até hoje — ver a seção "Fora do escopo" da
> spec de 07/07 para os motivos. Mantido aqui apenas como referência de
> design original.

Front-end: **Kotlin + Jetpack Compose**. Tema escuro. Esta spec descreve a tela **Home** com base no design de referência.

---

## 1. Visão geral

Tela de dashboard de um veículo com resumo financeiro, indicadores, insight do mês, último abastecimento e próximos eventos. Fundo escuro azulado, cards com cantos arredondados, cores de destaque por categoria (verde = economia/positivo, roxo = preço, laranja/amarelo = alertas, azul = ação).

---

## 2. Design tokens

```kotlin
object AppColors {
    val Background      = Color(0xFF0A0E1A) // fundo geral (azul quase preto)
    val Surface         = Color(0xFF141B2D) // cards
    val SurfaceVariant  = Color(0xFF1A2236) // cards internos / chips
    val Primary         = Color(0xFF3B82F6) // azul (FAB, links, ativo)
    val Success         = Color(0xFF34D399) // verde (valores positivos)
    val Purple          = Color(0xFFA78BFA) // preço médio
    val Warning         = Color(0xFFF59E0B) // laranja (troca de óleo, "há 8 dias")
    val TextPrimary     = Color(0xFFF8FAFC)
    val TextSecondary   = Color(0xFF94A3B8)
    val Divider         = Color(0xFF22304A)
}

object AppDimens {
    val ScreenPadding    = 16.dp
    val CardRadius       = 20.dp
    val CardPadding      = 16.dp
    val GridGap          = 12.dp
    val SectionSpacing   = 20.dp
}
```

Tipografia: fonte sem serifa (Inter / SF-like). Valores grandes em ~28–32sp bold; labels em ~13–14sp regular na cor `TextSecondary`.

---

## 3. Estrutura da tela (top → bottom)

```
Scaffold
 ├─ TopBar (menu + notificação)
 ├─ LazyColumn (conteúdo scrollável)
 │   ├─ VehicleHeader
 │   ├─ FinancialSummaryCard
 │   ├─ IndicatorsSection (grid 2x2)
 │   ├─ InsightCard
 │   ├─ LastRefuelCard
 │   └─ UpcomingEventsCard
 └─ BottomNavigation (com FAB central)
```

---

## 4. Componentes detalhados

### 4.1 TopBar
- Ícone de menu hambúrguer à esquerda.
- Ícone de sino à direita com badge laranja (ponto de notificação).
- Fundo transparente sobre o background.

### 4.2 VehicleHeader
- Avatar circular (foto do carro), ~64.dp.
- Título: nome do veículo em bold ~22sp + ícone chevron (dropdown de troca de veículo).
- Subtítulo: ícone de calendário laranja + texto `"Há 8 dias sem abastecer"` — a parte "Há 8 dias" em `Warning`, resto em `TextSecondary`.

### 4.3 FinancialSummaryCard
Card grande com leve gradiente/destaque.
- Header: ícone verde de carteira + título "Resumo financeiro". À direita: seletor de mês "Julho de 2026" + chevron.
- Divider fino.
- Duas colunas:
  - Esquerda: "Gasto neste mês" / valor grande verde `R$ 480,23` / linha `↓ 12% vs. junho` (seta e % em verde).
  - Direita: "Gasto total" / valor grande branco `R$ 1.277,35` / "desde o primeiro registro".

### 4.4 IndicatorsSection
Título da seção "Seus indicadores" + ícone info.
Grid **2 colunas × 2 linhas**, cada célula é um `IndicatorCard`:

| Card | Ícone (cor) | Título | Valor | Sub |
|------|-------------|--------|-------|-----|
| Consumo médio | velocímetro (verde) | Consumo médio | `12,4 km/L` | Baseado em 7 abastecimentos |
| Preço médio | bomba (roxo) | Preço médio | `R$ 6,97 /L` | ↓ R$ 0,18 vs. último abastecimento (verde) |
| Odômetro | painel (azul) | Odômetro atual | `67.275 km` | Total percorrido: 2.140 km |
| Último abastecimento | nota (laranja) | Último abastecimento | `R$ 148,42` | 28/06/2026 • 21,29 L |

`IndicatorCard`: ícone em círculo colorido no topo, valor destacado (unidade menor ao lado), label acima e sub abaixo.

### 4.5 InsightCard
- Fundo verde translúcido (`Success` com alpha ~0.12), borda sutil.
- Ícone de lâmpada em círculo verde.
- Label "Insight do mês" (pequeno) + destaque `"Você economizou R$ 74,32"` em verde bold + "em comparação a junho!".
- Chevron `>` à direita (navega para detalhe).

### 4.6 LastRefuelCard ("Último abastecimento")
- Header: título + link "Ver histórico" (azul, clicável).
- Bloco de data à esquerda (SÁB / 28 / JUN) com moldura arredondada.
- Coluna central com ícones + valores: `21,29 L`, `R$ 148,42`, `R$ 6,97 /L`.
- Coluna direita: `Posto Shell Centro` (pin), `67.275 km` (odômetro), `Gasolina comum` (gota).

### 4.7 UpcomingEventsCard ("Próximos eventos")
- Header: título + link "Ver todos" (azul).
- Linha de 3 chips horizontais:
  - Troca de óleo (ícone laranja) — "Em 320 km"
  - Rodízio de pneus (ícone roxo) — "Em 900 km"
  - Licenciamento (ícone azul) — "Vence em 18 dias"

### 4.8 BottomNavigation
5 posições, item central é FAB azul elevado:
`Home` (ativo, azul) · `Histórico` · **[+] Registrar abastecimento** (FAB azul circular) · `Postos` · `Perfil`.
Ícones + labels; item ativo em `Primary`, demais em `TextSecondary`.

---

## 5. Estado / modelo (UI)

```kotlin
data class HomeUiState(
    val vehicleName: String,
    val vehicleImageUrl: String?,
    val daysSinceRefuel: Int,
    val monthLabel: String,            // "Julho de 2026"
    val monthSpent: String,            // "R$ 480,23"
    val monthDeltaPercent: Int,        // -12
    val totalSpent: String,            // "R$ 1.277,35"
    val avgConsumption: String,        // "12,4 km/L"
    val consumptionBasis: Int,         // 7
    val avgPrice: String,              // "R$ 6,97 /L"
    val priceDelta: String,            // "R$ 0,18"
    val odometer: String,              // "67.275 km"
    val totalDistance: String,         // "2.140 km"
    val lastRefuel: RefuelSummary,
    val insightSaved: String,          // "R$ 74,32"
    val upcomingEvents: List<UpcomingEvent>,
)

data class RefuelSummary(
    val dayOfWeek: String, val day: String, val month: String,
    val liters: String, val total: String, val pricePerLiter: String,
    val station: String, val odometer: String, val fuelType: String,
)

data class UpcomingEvent(val icon: ImageVector, val tint: Color, val title: String, val subtitle: String)
```

---

## 6. Esqueleto Compose

```kotlin
@Composable
fun HomeScreen(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    Scaffold(
        containerColor = AppColors.Background,
        bottomBar = { HomeBottomBar(onEvent) },
        floatingActionButton = { RegisterFab(onClick = { onEvent(HomeEvent.Register) }) },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(AppDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionSpacing),
        ) {
            item { TopBar(onEvent) }
            item { VehicleHeader(state) }
            item { FinancialSummaryCard(state) }
            item { IndicatorsSection(state) }
            item { InsightCard(state) }
            item { LastRefuelCard(state) }
            item { UpcomingEventsCard(state) }
        }
    }
}

@Composable
private fun IndicatorsSection(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.GridGap)) {
        SectionTitle("Seus indicadores", showInfo = true)
        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.GridGap)) {
            IndicatorCard(Modifier.weight(1f), /* consumo */)
            IndicatorCard(Modifier.weight(1f), /* preço */)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.GridGap)) {
            IndicatorCard(Modifier.weight(1f), /* odômetro */)
            IndicatorCard(Modifier.weight(1f), /* último */)
        }
    }
}

@Composable
private fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimens.CardRadius))
            .background(AppColors.Surface)
            .padding(AppDimens.CardPadding),
        content = content,
    )
}
```

---

## 7. Critérios de aceite

- [ ] Layout escuro fiel às cores/tokens acima.
- [ ] Cards com cantos de 20.dp e espaçamentos consistentes.
- [ ] Grid de indicadores 2×2 responsivo (cada card `weight(1f)`).
- [ ] Valores monetários formatados em pt-BR (`R$` + vírgula decimal).
- [ ] Deltas positivos/negativos coloridos (verde para economia).
- [ ] Bottom bar com FAB central azul elevado.
- [ ] Links "Ver histórico" / "Ver todos" clicáveis com callbacks.
- [ ] Todos os textos vindo de `HomeUiState` (nada hardcoded na UI).
- [ ] Acessibilidade: `contentDescription` nos ícones informativos.
