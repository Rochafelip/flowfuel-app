package com.flowfuel.app.core.designsystem.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * VisualTransformation para entrada monetária em BRL baseada em centavos.
 *
 * - Entrada no estado: somente dígitos, ex: "24944"
 * - Exibição transformada: "R$ 249,44"
 *
 * O cursor é sempre mantido ao final do texto transformado, que é o
 * comportamento correto para um campo de moeda alimentado pelo teclado numérico.
 */
class CurrencyBrlVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val formatted = format(raw)
        return TransformedText(
            text = AnnotatedString(formatted),
            offsetMapping = object : OffsetMapping {
                // Cursor sempre ao final do texto transformado
                override fun originalToTransformed(offset: Int): Int = formatted.length
                override fun transformedToOriginal(offset: Int): Int = raw.length
            },
        )
    }

    companion object {
        /**
         * Formata uma string de dígitos como moeda BRL.
         *
         * Exemplos:
         * - "" → ""
         * - "1" → "R$ 0,01"
         * - "100" → "R$ 1,00"
         * - "24944" → "R$ 249,44"
         * - "9999999" → "R$ 99.999,99"
         */
        fun format(digits: String): String {
            if (digits.isEmpty()) return ""
            val padded = digits.padStart(3, '0')
            val rawInt = padded.dropLast(2)
            val decPart = padded.takeLast(2)

            // Adiciona separador de milhar (ponto) em grupos de 3
            val intPart = rawInt.trimStart('0').ifEmpty { "0" }
            val withThousands = intPart.reversed().chunked(3).joinToString(".").reversed()

            return "R\$ $withThousands,$decPart"
        }
    }
}