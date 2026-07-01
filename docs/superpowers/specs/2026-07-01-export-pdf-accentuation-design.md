# Export: correção de acentuação + exportação em PDF

Data: 2026-07-01

## Contexto

A exportação de CSV (`ExportRepositoryImpl`) já usa acentuação correta no
código-fonte (ex: "Preço", "Não", "Título"), mas o arquivo é gravado em UTF-8
puro sem BOM. O Excel (leitor mais comum no Android/Windows) sem BOM assume
Latin-1/Windows-1252 e exibe caracteres corrompidos (ex: "PreÃ§o"). Além
disso, hoje só existe a opção CSV — o usuário quer também poder exportar em
PDF.

## 1. Fix de acentuação (BOM UTF-8)

`saveFile` em `ExportRepositoryImpl` passa a prefixar o conteúdo do CSV com o
BOM UTF-8 (`0xEF 0xBB 0xBF`) antes de gravar os bytes. Aplica-se apenas ao
CSV (PDF não sofre desse problema, pois o texto é desenhado diretamente no
Canvas).

## 2. Exportação em PDF

### Formato

`ExportFormat` ganha um novo valor:

```kotlin
enum class ExportFormat(val value: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    PDF("pdf", "application/pdf"),
}
```

### Geração

Usa `android.graphics.pdf.PdfDocument` (nativo do Android, sem dependência
nova) com `Canvas` para desenhar texto e linhas, paginando quando o
conteúdo excede a altura útil da página (A4: 595x842pt @ 72dpi, com margens
de 40pt).

### Conteúdo do relatório

**Cabeçalho (ambos os relatórios):**
- Título: "Abastecimentos" ou "Eventos"
- Veículo: `"${brand} ${model}"` + placa (se houver), buscado via
  `VehicleRepository.getVehicleById(vehicleId)`
- Período: `"dd/MM/yyyy – dd/MM/yyyy"` se houver filtro de data, senão
  "Todo o histórico"

**Resumo — Abastecimentos:**
- Total gasto (R$)
- Total de litros/kWh abastecidos (rótulo depende de `EnergyType` do veículo)
- Consumo médio (média de `consumption` não-nulo)
- Número de abastecimentos no período

**Resumo — Eventos:**
- Total gasto (R$, soma de `amount` não-nulo)
- Contagem por categoria (`EventCategory.label` → count), uma linha por
  categoria presente nos dados

**Tabela:**
- Mesmas colunas do CSV correspondente, uma linha por registro
- Cabeçalho de coluna repetido no topo de cada nova página

### Fluxo de dados

`ExportRepositoryImpl` já busca todos os itens (`fetchAllRefuels` /
`fetchAllEvents`) antes de gerar o arquivo — esse fluxo não muda. Roteamento
por formato:

```kotlin
when (format) {
    ExportFormat.CSV -> buildRefuelsCsv(items)  // + BOM
    ExportFormat.PDF -> buildRefuelsPdf(vehicle, items, startDate, endDate)
}
```

Para PDF, busca adicional de `vehicleRepository.getVehicleById(vehicleId)`
antes de montar o documento. Falha nessa busca propaga como
`AppResult.Failure` (mesmo padrão dos outros métodos do repositório).

Nome do arquivo: `flowfuel-abastecimentos.csv` / `.pdf` (mesma convenção,
extensão conforme `format.value`).

## 3. UI — seletor de formato

`ExportBottomSheet` ganha uma nova seção "Formato" (chips CSV / PDF), no
mesmo padrão visual do seletor de tipo de evento (`FFChip` com `selected`),
posicionada acima do botão de exportar. Usa `viewModel.onFormatChange`, que
já existe em `ExportViewModel`.

## Testes

- Unit test para `buildRefuelsCsv`/`buildEventsCsv`: conteúdo começa com BOM.
- Unit test para o resumo do PDF: cálculo de total gasto, consumo médio e
  contagem por categoria com dados de exemplo (litros/valores conhecidos).
- Não é viável testar renderização visual do PDF em unit test — cobertura
  fica nos cálculos de resumo e na montagem das linhas de tabela (função
  pura separada da chamada ao `PdfDocument`).
