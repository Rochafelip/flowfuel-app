package com.flowfuel.app.feature.flexcalculator.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFNumberField
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.flexcalculator.domain.FlexFuel
import com.flowfuel.app.feature.flexcalculator.domain.FuelWinner
import java.util.Locale

private enum class FlexMode(val label: String) {
    Ratio("Regra dos 70%"),
    Exact("Cálculo exato"),
}

private data class FlexResult(val title: String, val lines: List<String>)

private val brLocale = Locale("pt", "BR")

private fun Double.formatBr(digits: Int): String = String.format(brLocale, "%.${digits}f", this)

private fun winnerTitle(winner: FuelWinner) = when (winner) {
    FuelWinner.Ethanol -> "Abasteça com etanol"
    FuelWinner.Gasoline -> "Abasteça com gasolina"
    FuelWinner.Tie -> "Tanto faz"
}

/**
 * Calculadora flex: diz se compensa abastecer com etanol ou gasolina, pela regra
 * dos 70% ou pelo custo por km a partir do consumo. Tudo local, sem backend —
 * por isso o estado fica em [rememberSaveable] em vez de um ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlexCalculatorScreen(onBack: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(FlexMode.Ratio) }
    var ethanolPrice by rememberSaveable { mutableStateOf("") }
    var gasolinePrice by rememberSaveable { mutableStateOf("") }
    var ethanolKm by rememberSaveable { mutableStateOf("") }
    var gasolineKm by rememberSaveable { mutableStateOf("") }

    val result = computeResult(mode, ethanolPrice, gasolinePrice, ethanolKm, gasolineKm)

    Scaffold(
        topBar = { FFTopBar(title = "Calculadora flex", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FFTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                FlexMode.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == mode,
                        onClick = { mode = option },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = FlexMode.entries.size),
                        label = { Text(option.label) },
                    )
                }
            }

            PriceField(
                value = ethanolPrice,
                onValueChange = { ethanolPrice = it },
                label = "Preço do etanol (R$/litro)",
                example = "Ex.: 4,29",
            )
            PriceField(
                value = gasolinePrice,
                onValueChange = { gasolinePrice = it },
                label = "Preço da gasolina (R$/litro)",
                example = "Ex.: 6,19",
                imeAction = if (mode == FlexMode.Exact) ImeAction.Next else ImeAction.Done,
            )

            if (mode == FlexMode.Exact) {
                PriceField(
                    value = ethanolKm,
                    onValueChange = { ethanolKm = it },
                    label = "Consumo com etanol (km/l)",
                    example = "Ex.: 8,5",
                )
                PriceField(
                    value = gasolineKm,
                    onValueChange = { gasolineKm = it },
                    label = "Consumo com gasolina (km/l)",
                    example = "Ex.: 12",
                    imeAction = ImeAction.Done,
                )
            }

            if (result != null) {
                FFCard(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                    Column {
                        Text(
                            text = result.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = FFTheme.semanticColors.success,
                        )
                        result.lines.forEach { line ->
                            Spacer(Modifier.height(FFTheme.spacing.xs))
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(FFTheme.spacing.lg))
        }
    }
}

@Composable
private fun PriceField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    example: String,
    imeAction: ImeAction = ImeAction.Next,
) {
    val invalid = value.isNotBlank() && FlexFuel.parseDecimal(value) == null
    FFNumberField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        helper = example,
        errorText = if (invalid) "Informe um número maior que zero" else null,
        imeAction = imeAction,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun computeResult(
    mode: FlexMode,
    ethanolPriceText: String,
    gasolinePriceText: String,
    ethanolKmText: String,
    gasolineKmText: String,
): FlexResult? {
    val ethanolPrice = FlexFuel.parseDecimal(ethanolPriceText) ?: return null
    val gasolinePrice = FlexFuel.parseDecimal(gasolinePriceText) ?: return null

    if (mode == FlexMode.Ratio) {
        val (ratio, winner) = FlexFuel.ratioRule(ethanolPrice, gasolinePrice)
        return FlexResult(
            title = winnerTitle(winner),
            lines = listOf("O etanol custa ${(ratio * 100).formatBr(1)}% do preço da gasolina (limite: 70%)"),
        )
    }

    val ethanolKm = FlexFuel.parseDecimal(ethanolKmText) ?: return null
    val gasolineKm = FlexFuel.parseDecimal(gasolineKmText) ?: return null
    val cost = FlexFuel.exactCost(ethanolPrice, gasolinePrice, ethanolKm, gasolineKm)
    val lines = buildList {
        add("Etanol: R$ ${cost.ethanolCostPerKm.formatBr(3)} por km")
        add("Gasolina: R$ ${cost.gasolineCostPerKm.formatBr(3)} por km")
        if (cost.winner != FuelWinner.Tie) add("Economia de ${cost.savingsPercent.formatBr(1)}% por km")
    }
    return FlexResult(title = winnerTitle(cost.winner), lines = lines)
}
