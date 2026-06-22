package com.flowfuel.app.core.designsystem.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * VisualTransformation para números inteiros (sem casa decimal), só com
 * separador de milhar. Usado para odômetro de veículo, cujo `odometerKm` no
 * backend é `Int` — diferente do odômetro de abastecimento (`Double`), que
 * usa [OdometerVisualTransformation] com décimos.
 *
 * - Entrada no estado : somente dígitos, representa km inteiros.
 * - Exibição          : com separador de milhar (ex: "15000" → "15.000").
 */
class WholeNumberVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val formatted = format(raw)
        return TransformedText(
            text = AnnotatedString(formatted),
            offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = formatted.length
                override fun transformedToOriginal(offset: Int): Int = raw.length
            },
        )
    }

    companion object {
        const val MAX_DIGITS = 8

        fun format(digits: String): String {
            if (digits.isEmpty()) return ""
            val trimmed = digits.trimStart('0').ifEmpty { "0" }
            return trimmed.reversed().chunked(3).joinToString(".").reversed()
        }
    }
}
