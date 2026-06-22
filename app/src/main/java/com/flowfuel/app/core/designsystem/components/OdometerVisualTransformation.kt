package com.flowfuel.app.core.designsystem.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * VisualTransformation para entrada de odômetro em km com 1 casa decimal.
 *
 * - Entrada no estado : somente dígitos, representa décimos de km.
 *                       Ex: "1000005" → 100000,5 km
 * - Exibição          : "100.000,5"
 *
 * Exemplos:
 * - ""        → ""
 * - "0"       → "0,0"
 * - "5"       → "0,5"
 * - "500000"  → "50.000,0"
 * - "1000005" → "100.000,5"
 *
 * O cursor é mantido sempre ao final (mesmo comportamento de [CurrencyBrlVisualTransformation]).
 */
class OdometerVisualTransformation : VisualTransformation {

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
        /** Máximo de dígitos aceitos (9 999 999,9 km é mais que suficiente). */
        const val MAX_DIGITS = 8

        /**
         * Formata uma string de dígitos como odômetro km com 1 decimal.
         */
        fun format(digits: String): String {
            if (digits.isEmpty()) return ""
            // Garante pelo menos 2 dígitos para separar 1 casa decimal
            val padded = digits.padStart(2, '0')
            val intRaw = padded.dropLast(1).trimStart('0').ifEmpty { "0" }
            val decPart = padded.takeLast(1)
            // Adiciona separador de milhar (ponto) em grupos de 3
            val withThousands = intRaw.reversed().chunked(3).joinToString(".").reversed()
            return "$withThousands,$decPart"
        }
    }
}