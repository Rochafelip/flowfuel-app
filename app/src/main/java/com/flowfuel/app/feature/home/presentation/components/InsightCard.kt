package com.flowfuel.app.feature.home.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFCardVariant
import java.time.LocalDate

private val dailyTips = listOf(
    "Calibrar os pneus regularmente pode economizar até 3% de combustível.",
    "Evite acelerações bruscas: dirigir suave reduz o consumo em até 20%.",
    "Troque o óleo no intervalo recomendado pelo fabricante para manter o motor eficiente.",
    "Ar-condicionado ligado em velocidade baixa consome mais que janelas abertas.",
    "Excesso de peso no porta-malas aumenta o consumo de combustível.",
    "Marcha alta em baixa rotação economiza combustível em trajetos urbanos.",
    "Pneus murchos aumentam o atrito e o consumo — confira a calibragem mensalmente.",
    "Evite deixar o carro ligado parado por muito tempo: prefira desligar o motor.",
    "Filtro de ar sujo reduz a eficiência do motor — verifique a cada revisão.",
    "Planeje trajetos para evitar horários de trânsito intenso e economizar combustível.",
    "Use o freio motor em descidas para poupar as pastilhas e economizar combustível.",
    "Revisões preventivas evitam consumo excessivo por problemas mecânicos não percebidos.",
)

@Composable
fun InsightCard(modifier: Modifier = Modifier) {
    val tip = remember { dailyTips[LocalDate.now().dayOfYear % dailyTips.size] }
    FFCard(modifier = modifier, variant = FFCardVariant.Flat, title = "Dica do dia") {
        Text(
            text = tip,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InsightCardPreview() {
    InsightCard()
}
