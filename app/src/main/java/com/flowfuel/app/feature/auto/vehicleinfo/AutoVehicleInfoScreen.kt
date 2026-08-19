package com.flowfuel.app.feature.auto.vehicleinfo

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.flowfuel.app.R
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.model.ActiveVehicleData
import com.flowfuel.app.feature.home.domain.model.DashboardData
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Mostra o valor em destaque (title) e o rótulo como texto secundário — em vez do
 * padrão inverso — porque essa é uma tela pra consulta rápida no painel: o número
 * é o que importa num relance, o rótulo só dá contexto.
 */
class AutoVehicleInfoScreen(
    carContext: CarContext,
    private val vehicle: ActiveVehicleData,
    private val getDashboard: GetDashboardUseCase,
) : Screen(carContext) {

    private sealed interface State {
        data object Loading : State
        data class Success(val dashboard: DashboardData) : State
        data class Error(val error: AppError) : State
    }

    private var state: State = State.Loading

    init {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) loadData()
        })
    }

    internal fun loadData() {
        lifecycleScope.launch {
            state = State.Loading
            invalidate()

            when (val result = getDashboard(vehicle.id)) {
                is AppResult.Success -> {
                    state = State.Success(result.value)
                    invalidate()
                }
                is AppResult.Failure -> {
                    state = State.Error(result.error)
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template = when (val s = state) {
        State.Loading -> loadingTemplate()
        is State.Error -> errorTemplate(s.error)
        is State.Success -> successTemplate(s.dashboard)
    }

    private fun loadingTemplate(): Template =
        MessageTemplate.Builder("Carregando…")
            .setTitle("Veículo")
            .setHeaderAction(Action.BACK)
            .setLoading(true)
            .build()

    private fun errorTemplate(error: AppError): Template {
        val msg = if (error == AppError.Unauthorized)
            "Sessão expirada. Abra o FlowFuel no celular para entrar novamente."
        else
            "Erro ao carregar informações. Verifique sua conexão."
        val builder = MessageTemplate.Builder(msg)
            .setTitle("Veículo")
            .setHeaderAction(Action.BACK)
        if (error != AppError.Unauthorized) {
            builder.addAction(
                Action.Builder()
                    .setTitle("Tentar novamente")
                    .setOnClickListener { loadData() }
                    .build()
            )
        }
        return builder.build()
    }

    private fun successTemplate(dashboard: DashboardData): Template {
        val brLocale = Locale("pt", "BR")

        val headerSubtitleParts = listOfNotNull(
            (vehicle.modelYear ?: vehicle.manufactureYear)?.toString(),
            vehicle.licensePlate,
        )
        val headerRow = Row.Builder()
            .setTitle("${vehicle.brand} ${vehicle.model}")
            .setImage(icon(R.drawable.ic_auto_car))
        if (headerSubtitleParts.isNotEmpty()) {
            headerRow.addText(headerSubtitleParts.joinToString(" • "))
        }

        val consumptionText = dashboard.averageConsumption?.let { value ->
            val unit = dashboard.consumptionUnit?.let { " $it" } ?: ""
            String.format(brLocale, "%.1f", value) + unit
        } ?: "—"

        val priceText = dashboard.averagePricePerUnit?.let { value ->
            "R$ " + String.format(brLocale, "%.2f", value)
        } ?: "—"

        val listBuilder = ItemList.Builder()
            .addItem(headerRow.build())
            .addItem(
                Row.Builder()
                    .setTitle("${vehicle.currentKm} km")
                    .addText("Odômetro")
                    .setImage(icon(R.drawable.ic_auto_speed))
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(consumptionText)
                    .addText("Consumo médio")
                    .setImage(icon(R.drawable.ic_auto_fuel))
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(priceText)
                    .addText("Preço médio")
                    .setImage(icon(R.drawable.ic_auto_money))
                    .build()
            )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Veículo")
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun icon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId))
            .setTint(ICON_TINT)
            .build()

    private companion object {
        // Verde da marca FlowFuel — sem tint explícito o host renderia os ícones
        // com o preto sólido do drawable, quase invisível no tema escuro do carro.
        val ICON_TINT: CarColor = CarColor.createCustom(0xFF0B6E4F.toInt(), 0xFF34D399.toInt())
    }
}
