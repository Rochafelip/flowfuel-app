package com.flowfuel.app.feature.auto.refuel

/** O que o usuário digitou no Passo 1: km percorridos desde o último abastecimento, ou a leitura total atual do odômetro. */
sealed interface OdometerInput {
    data class Trip(val km: Double) : OdometerInput
    data class Odometer(val value: Double) : OdometerInput
}
