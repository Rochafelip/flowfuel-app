# Design: Dashboard do Android Auto em grid (sem rolagem)

## Contexto

O `AutoDashboardScreen` (tela de sucesso) usa hoje `PaneTemplate` com 4
`Row`s (Consumo médio, Gasto total, Abastecimentos, Último abastecimento) e
uma `Action` ("Registrar abastecimento") anexada ao `Pane`. Testado ao vivo
via DHU em 2026-07-02: as 4 linhas não cabem inteiras na tela do carro sem
rolagem — o usuário precisa "abaixar a tela" pra ver a 4ª informação e o
botão de registrar abastecimento.

O Car App Library (1.4.0, já declarado no projeto) não dá controle de
layout livre — todo template é renderizado pelo host por regra de
segurança do motorista. O template mais próximo de um layout em blocos é o
`GridTemplate`, confirmado via documentação oficial do Google (`Design for
Driving`) com garantia de pelo menos 6 itens visíveis sem rolagem — folga
suficiente pras 4 informações atuais.

Investigação técnica que motivou a escolha da abordagem (ver conversa):
- `GridTemplate` + FAB (`addAction`/floating action button) foi descartado
  porque o FAB nesse template é **só ícone, sem texto** — perderia o
  rótulo explícito "Registrar abastecimento".
- A alternativa escolhida usa um 5º `GridItem` clicável no lugar do botão,
  já que `GridItem` aceita `title` + `onClick` sem exigir badge.

**Correção pós-implementação (2026-07-02):** a suposição inicial de que
`GridItem` aceita imagem opcional estava errada. Rodando os testes contra
a implementação real, `GridItem.Builder.build()` lança
`IllegalStateException: When a grid item is loading, the image must not
be set and vice versa` pra qualquer item que não esteja em `isLoading` e
não tenha `setImage()` chamado — confirmado decompilando o bytecode de
`GridItem.Builder.build()` (`GridItem.java:451` no `.aar`). Ou seja,
**imagem é obrigatória** em todo `GridItem` fora do estado de loading,
não "opcional mas recomendada" como a documentação de design sugeria.
Isso muda o escopo: os 5 blocos precisam de ícone. Ver seção "Ícones"
abaixo.

## Objetivo

Reorganizar a tela de sucesso do dashboard do Android Auto em um
`GridTemplate` com 5 blocos — 4 informativos + 1 de ação — todos visíveis
sem precisar rolar a tela, preservando os mesmos dados e formatação já
calculados hoje.

## Escopo

### Dentro

- Trocar `PaneTemplate`/`Pane`/`Row` por `GridTemplate`/`ItemList`/
  `GridItem` em `AutoDashboardScreen.successTemplate()`.
- 5 `GridItem`s, nesta ordem: Consumo médio, Gasto total, Abastecimentos,
  Último abastecimento, Registrar abastecimento (ação).
- Os 4 itens informativos usam `title` (rótulo) + `text` (valor) + um
  ícone obrigatório (`GridItem.setImage`), sem `onClick` — mesmo texto já
  calculado hoje (`consumptionText`, `spentText`, `totalRefuelsText`,
  `lastRefuelText`, sem alteração de formatação/regra).
- O 5º item ("Registrar abastecimento") usa `title` + ícone, com
  `onClick` navegando para `AutoRefuelStep1Screen` — mesmo
  destino/parâmetros do botão atual.
- 5 novos recursos `drawable` vetoriais (`res/drawable/ic_auto_*.xml`),
  um por bloco: `ic_auto_fuel`, `ic_auto_money`, `ic_auto_calendar`,
  `ic_auto_history`, `ic_auto_add`. Ícones do Material Icons clássico
  (24dp, path único, sem tint fixo) — não reaproveitam os ícones do
  Compose Material Icons Extended já usados no resto do app, porque
  `CarIcon`/`IconCompat` exige um recurso `drawable` real, não um
  `ImageVector` do Compose.
- Atualizar `AutoDashboardScreenTest` para verificar `GridTemplate` e os
  itens da `ItemList` (substituindo as asserções de `PaneTemplate`/
  `pane.rows`), mantendo a mesma cobertura de casos já testados
  (total de abastecimentos aparece, valor do último abastecimento
  aparece).

### Fora (não mexer nesta rodada)

- Loading (`loadingTemplate`) e erro (`errorTemplate`) continuam em
  `MessageTemplate`, sem alteração — só a tela de sucesso muda de
  template.
- Mudar os dados exibidos, a formatação de valores, ou a lógica de
  `loadData()`/`State` — só a camada de apresentação (template) muda.
- `minCarApiLevel` — `GridTemplate` já está disponível desde API level 1,
  não precisa de bump (diferente do `InputTemplate`/`InputSignInMethod`
  usado no fluxo de abastecimento, que já é `minCarApiLevel = 2`).

## Arquitetura

### `successTemplate()` — antes (PaneTemplate)

```kotlin
return PaneTemplate.Builder(
    Pane.Builder()
        .addRow(Row.Builder().setTitle("Consumo médio").addText(consumptionText).build())
        .addRow(Row.Builder().setTitle("Gasto total").addText(spentText).build())
        .addRow(Row.Builder().setTitle("Abastecimentos").addText(totalRefuelsText).build())
        .addRow(Row.Builder().setTitle("Último abastecimento").addText(lastRefuelText).build())
        .addAction(
            Action.Builder()
                .setTitle("Registrar abastecimento")
                .setOnClickListener {
                    screenManager.push(AutoRefuelStep1Screen(carContext, vehicle = v, createRefuel = createRefuel))
                }
                .build()
        )
        .build()
)
    .setTitle(title)
    .setHeaderAction(Action.APP_ICON)
    .build()
```

### `successTemplate()` — depois (GridTemplate)

```kotlin
return GridTemplate.Builder()
    .setSingleList(
        ItemList.Builder()
            .addItem(GridItem.Builder().setTitle("Consumo médio").setText(consumptionText).setImage(icon(R.drawable.ic_auto_fuel)).build())
            .addItem(GridItem.Builder().setTitle("Gasto total").setText(spentText).setImage(icon(R.drawable.ic_auto_money)).build())
            .addItem(GridItem.Builder().setTitle("Abastecimentos").setText(totalRefuelsText).setImage(icon(R.drawable.ic_auto_calendar)).build())
            .addItem(GridItem.Builder().setTitle("Último abastecimento").setText(lastRefuelText).setImage(icon(R.drawable.ic_auto_history)).build())
            .addItem(
                GridItem.Builder()
                    .setTitle("Registrar abastecimento")
                    .setImage(icon(R.drawable.ic_auto_add))
                    .setOnClickListener {
                        screenManager.push(AutoRefuelStep1Screen(carContext, vehicle = v, createRefuel = createRefuel))
                    }
                    .build()
            )
            .build()
    )
    .setTitle(title)
    .setHeaderAction(Action.APP_ICON)
    .build()

private fun icon(resId: Int): CarIcon =
    CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
```

`icon()` é um helper privado no `AutoDashboardScreen` que embrulha um
recurso `drawable` em `CarIcon` via `IconCompat.createWithResource()` —
única forma de satisfazer a exigência de imagem do `GridItem` sem
duplicar a construção em cada chamada.

Novos imports necessários em `AutoDashboardScreen.kt`:
`androidx.car.app.model.GridTemplate`, `androidx.car.app.model.GridItem`,
`androidx.car.app.model.ItemList`, `androidx.car.app.model.CarIcon`,
`androidx.core.graphics.drawable.IconCompat`, `com.flowfuel.app.R`. Os
imports de `Pane`, `PaneTemplate`, `Row` deixam de ser usados nesse
arquivo e são removidos.

`consumptionText`, `spentText`, `totalRefuelsText`, `lastRefuelText`
continuam calculados exatamente como hoje — só o container muda.

## Testes

`AutoDashboardScreenTest` já tem os testes (adicionados na rodada
anterior) `dashboard exibe total de abastecimentos` e `dashboard exibe
valor monetario do ultimo abastecimento quando disponivel`, que hoje
fazem cast pra `PaneTemplate` e leem `template.pane!!.rows`. Precisam
mudar pra:

```kotlin
val template = screen.onGetTemplate() as GridTemplate
val items = template.singleList!!.items
assertTrue(items.size >= 4)
assertTrue(
    items.any { item -> (item as GridItem).text?.toString()?.contains("12") == true }
)
```

Mesmo padrão de asserção "contém o texto", evitando fragilidade com
formatação exata — igual ao que já existe hoje.

Nenhum teste novo de `onClick` do item de ação é necessário além do que já
existe implicitamente: `AutoDashboardScreenTest` não testava clique no
botão "Registrar abastecimento" antes (não havia teste pra isso), então
não é regressão remover — mas dá pra adicionar um teste simples
verificando que o 5º item tem `onClick` não nulo, já que antes o clique do
botão também não era testado diretamente.

## Auto-revisão do spec

- **Sem placeholders:** código antes/depois, imports afetados e mudança
  de teste estão todos explícitos.
- **Consistência interna:** a ordem dos 5 itens é a mesma em todo o
  documento (info x4 + ação), e bate com a ordem já usada no
  `PaneTemplate` atual.
- **Escopo:** mudança pontual de um arquivo de produção
  (`AutoDashboardScreen.kt`) + um arquivo de teste
  (`AutoDashboardScreenTest.kt`), sem tocar em loading/erro, dados ou
  outras telas do fluxo Auto — não precisa de decomposição.
- **Ambiguidade:** resolvido explicitamente por que FAB foi descartado
  (perda do rótulo de texto) e por que o 5º `GridItem` resolve isso sem
  exigir ícone (campo de imagem é opcional na API).
