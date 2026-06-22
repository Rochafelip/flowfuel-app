package com.flowfuel.app.core.common

import android.util.Patterns

object Validators {
    fun isEmail(value: String): Boolean =
        value.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(value).matches()

    fun isStrongEnoughPassword(value: String): Boolean = value.length >= 8

    /**
     * Valida telefone no formato E.164 com ou sem `+`.
     * Aceita: +5511999999999, 5511999999999, 11999999999
     * Mínimo 10 dígitos, máximo 15.
     */
    fun isPhone(value: String): Boolean {
        val digits = value.trimStart('+').filter(Char::isDigit)
        return digits.length in 10..15
    }
}
