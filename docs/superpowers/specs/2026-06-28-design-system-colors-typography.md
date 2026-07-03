# Design System — Auditoria de Cores e Tipografia

**Data:** 2026-06-28  
**Status:** Aprovado  

---

## Contexto

Uma auditoria do design system (`core/designsystem/theme/`) identificou 7 defeitos que precisam ser corrigidos antes de novas telas serem construídas. Os defeitos foram classificados em P0/P1/P2 por impacto:

- **P0 — crítico (3):** violações de acessibilidade ou bugs silenciosos com efeito visual imediato
- **P1 — alto (2):** degradação visual mensurável que afeta usabilidade
- **P2 — médio (2):** lacunas no sistema que causam inconsistência se não fechadas agora

Nenhuma lógica de negócio, ViewModel ou repositório é afetado — as mudanças ficam inteiramente na camada de tokens e numa limpeza de `HomeScreen.kt`.

---

## Defeitos identificados e decisões

### P0 — Críticos

**1. `labelSmall` em 11sp viola WCAG 2.1 AA**  
Mínimo exigido para texto legível em componentes pequenos (chips, badges) é 12sp. Decisão: aumentar para 12sp.

**2. `bodyLarge` com `letterSpacing = 0.5.sp` incorreto**  
0.5sp é o valor M3 para *labels*, não para body text — comprime o texto de conteúdo de forma perceptível. Decisão: corrigir para 0.15sp (especificação M3 para bodyLarge).

**3. `InterFamily` resolve para Roboto**  
`FontFamily.SansSerif` no Android é Roboto. A fonte Inter nunca foi efetivamente usada no app. Decisão: conectar Inter via `ui-text-google-fonts` (download em runtime pelo GMS, sem binários commitados).

**4. `OutlineDark` e `OutlineVariantDark` invisíveis no dark mode**  
`OutlineDark = #2C342F` sobre surface `#161D19` tem contraste ~1.5:1; `OutlineVariantDark = #222B26` tem ~1.3:1. WCAG AA para componentes de UI exige ≥ 3:1. Bordas de input e divisores são invisíveis no dark mode. Decisão: `OutlineDark → #6B7870` (3.6:1) e `OutlineVariantDark → #3A4642` (1.9:1).

### P1 — Altos

**5. `OutlineLight` com contraste 1.6:1**  
`OutlineLight = #C7CDC9` sobre branco tem contraste 1.6:1 — falha WCAG AA (3:1 para componentes de UI). Decisão: `OutlineLight → #6D7570` (4.7:1). `OutlineVariantLight` também ajustado levemente para manter proporção.

**6. Surfaces dark mode sem separação de camada**  
`BackgroundDark`, `SurfaceDark` e `SurfaceVariantDark` diferem em ~8 unidades de luminosidade — cards são indistinguíveis do fundo. Decisão: aumentar spread para ~12 unidades ajustando `SurfaceDark` e `SurfaceVariantDark`.

**7. 5 valores alpha hardcoded em `HomeScreen.kt`**  
`.copy(alpha = 0.75f)`, `.copy(alpha = 0.7f)` e `.copy(alpha = 0.15f)` aparecem 5 vezes sem semântica clara. Decisão: criar `FFAlpha` com tokens `medium` (0.74f) e `subtle` (0.12f), expor via `FFTheme`, e substituir os hardcoded.

### P2 — Médios

**8. `SurfaceContainer` tokens ausentes**  
M3 1.3.x (presente via BOM 2024.12.01) introduziu `surfaceContainer`, `surfaceContainerLow`, `surfaceContainerHigh`, etc. Sem mapeamento, o M3 usa valores auto-gerados que ignoram a paleta verde do FlowFuel. Decisão: mapear todos os 5 níveis para ambos os temas.

**9. Cor semântica `info` ausente**  
`FFSemanticColors` tem `success` e `warning` mas não `info`. Qualquer banner informativo futuro improvisaria com `secondary` ou `tertiary`. Decisão: adicionar `info`/`onInfo` em light e dark.

---

## Restrições

- BOM: `2024.12.01` — não bumpar dependências Compose/M3 individualmente fora do BOM
- minSdk 26, compileSdk 35
- Tokens em `app/src/main/java/com/flowfuel/app/core/designsystem/theme/`
- Sem testes de unidade para o design system — verificação é `./gradlew assembleDebug` + Previews no Android Studio
- NÃO alterar ViewModels, repositórios, ou lógica de negócio

---

## Arquivos afetados

```
app/src/main/java/com/flowfuel/app/core/designsystem/theme/
  Color.kt       — corrigir outline/surface; adicionar FFAlpha; ampliar FFExtraColors; SurfaceContainer
  Typography.kt  — corrigir escala (labelSmall, bodyLarge); conectar Inter
  Theme.kt       — expor FFAlpha via FFTheme; mapear SurfaceContainer

app/src/main/java/com/flowfuel/app/feature/home/presentation/
  HomeScreen.kt  — substituir 5 chamadas .copy(alpha = x) por tokens FFAlpha

gradle/
  libs.versions.toml     — alias ui-text-google-fonts
  app/build.gradle.kts   — dependência ui-text-google-fonts

app/src/main/res/values/
  font_certs.xml  — certificados GMS para o provedor Google Fonts (novo)
```

---

## Fora do escopo

- Alterações em telas além de `HomeScreen.kt` (limpeza de alpha hardcoded)
- Auditoria de outros tokens (espaçamento, elevação, formas)
- Suporte a fontes customizadas sem GMS (dispositivos sem Google Play)
- Modo alto contraste / acessibilidade além dos mínimos WCAG AA aqui descritos
- Dark mode dinâmico (Material You / wallpaper-based theming)
