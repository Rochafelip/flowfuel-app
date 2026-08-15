package com.flowfuel.app.feature.home.domain.model

import com.flowfuel.app.feature.vehicle.domain.model.VehicleType

/**
 * Representação simplificada do veículo ativo para a tela principal.
 * Evita acoplar o domínio de Home ao domínio de Vehicle.
 */
data class ActiveVehicleData(
    val id: Int,
    val brand: String,
    val model: String,
    val fuelSubType: String?,
    val capacity: Double?,
    val licensePlate: String?,
    val energyType: String,
    val currentKm: Int,
    val photoUrl: String? = null,
    val vehicleType: VehicleType = VehicleType.Car,
)

/** Consumo e gasto separados por modal para veículos HYBRID. */
data class HybridConsumptionBreakdown(
    val fuelConsumption: Double?,
    val fuelConsumptionUnit: String?,
    val fuelAveragePrice: Double?,
    val fuelPriceUnit: String?,
    val fuelTotalSpent: Double?,
    val electricConsumption: Double?,
    val electricConsumptionUnit: String?,
    val electricAveragePrice: Double?,
    val electricPriceUnit: String?,
    val electricTotalSpent: Double?,
)

/** Um ponto do gráfico de gastos mensais (últimos 6 meses). [month] no formato ISO "yyyy-MM". */
data class MonthlySpendingEntry(
    val month: String,
    val amount: Double,
)

/** Dados do painel de controle para um veículo. */
data class DashboardData(
    val averageConsumption: Double?,
    /** Unidade de consumo informada pelo backend (ex: "km/L", "km/kWh"); null para HYBRID. */
    val consumptionUnit: String?,
    val totalSpent: Double,
    /** Gasto só com abastecimentos (sem eventos) — sempre o valor bruto do
     *  endpoint de dashboard, mesmo depois de [totalSpent] virar o total
     *  combinado em HomeViewModel.fetchDashboardWithEventsTotal. */
    val fuelSpent: Double,
    val totalRefuels: Int,
    val lastRefuelDate: String?,
    /** Litros ou kWh do último abastecimento — vem de /refuels, não do endpoint de dashboard. */
    val lastRefuelEnergyAmount: Double?,
    val lastRefuelAmount: Double?,
    /** Unidade do último abastecimento (ex: "L", "kWh"), inferida do refuelType/energyType. */
    val lastRefuelEnergyUnit: String?,
    /** Preço por litro/kWh pago no último abastecimento — vem de /refuels. */
    val lastRefuelPricePerUnit: Double? = null,
    /** Detalhamento por combustão/elétrico; preenchido apenas para HYBRID. */
    val hybridBreakdown: HybridConsumptionBreakdown? = null,
    /** Odômetro do último abastecimento registrado; null se não houver abastecimentos. */
    val lastOdometer: Int? = null,
    /** Custo médio por km rodado (totalSpent / km rodados no período). */
    val costPerKm: Double? = null,
    /** Preço médio por litro/kWh em todo o histórico de abastecimentos (não só o mês atual). */
    val averagePricePerUnit: Double? = null,
    /** Unidade do preço médio (ex: "R$/L", "R$/kWh"), informada pelo backend. */
    val priceUnit: String? = null,
    /** Gasto total (combustível + eventos) dos últimos 6 meses, do mais antigo ao mais recente. Sempre 6 entradas (backend garante). */
    val monthlySpending: List<MonthlySpendingEntry> = emptyList(),
) {
    val hasRefuels: Boolean get() = totalRefuels > 0
}

/** Parâmetros para registrar um novo abastecimento. */
data class CreateRefuelRequest(
    val vehicleId: Int,
    val odometer: Double,
    val liters: Double,
    val totalPrice: Double,
    val fullTank: Boolean,
    /** null para combustão/elétrico puro; "FUEL" ou "ELECTRIC" para híbridos. */
    val refuelType: String? = null,
)

/** Tipo de lembrete de manutenção exibido na seção "Próximos eventos" da Home. */
enum class UpcomingMaintenanceType { OIL_CHANGE, TIRE_ROTATION, LICENSING }

/**
 * Um dos 3 lembretes da seção "Próximos eventos". [remainingKm] é usado por
 * OIL_CHANGE/TIRE_ROTATION, [remainingDays] por LICENSING — os dois nunca são
 * preenchidos ao mesmo tempo. [needsSetup] só é true para LICENSING sem data
 * definida ainda pelo usuário (ver [com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStore]).
 */
data class UpcomingMaintenanceItem(
    val type: UpcomingMaintenanceType,
    val remainingKm: Int? = null,
    val remainingDays: Int? = null,
    val isOverdue: Boolean = false,
    val needsSetup: Boolean = false,
)