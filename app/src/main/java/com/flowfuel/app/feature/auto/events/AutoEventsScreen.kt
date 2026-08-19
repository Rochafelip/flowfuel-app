package com.flowfuel.app.feature.auto.events

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsUseCase
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private const val MAX_EVENTS_SHOWN = 10

class AutoEventsScreen(
    carContext: CarContext,
    private val vehicleId: Int,
    private val getVehicleEvents: GetVehicleEventsUseCase,
) : Screen(carContext) {

    private sealed interface State {
        data object Loading : State
        data class Success(val events: List<VehicleEvent>) : State
        data object Empty : State
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

            when (val result = getVehicleEvents(vehicleId)) {
                is AppResult.Success -> {
                    val events = result.value.sortedByDescending { it.eventDate }.take(MAX_EVENTS_SHOWN)
                    state = if (events.isEmpty()) State.Empty else State.Success(events)
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
        State.Empty -> messageTemplate("Nenhum evento registrado ainda.")
        is State.Error -> errorTemplate(s.error)
        is State.Success -> successTemplate(s.events)
    }

    private fun loadingTemplate(): Template =
        MessageTemplate.Builder("Carregando…")
            .setTitle("Eventos")
            .setHeaderAction(Action.BACK)
            .setLoading(true)
            .build()

    private fun messageTemplate(message: String): Template =
        MessageTemplate.Builder(message)
            .setTitle("Eventos")
            .setHeaderAction(Action.BACK)
            .build()

    private fun errorTemplate(error: AppError): Template {
        val msg = if (error == AppError.Unauthorized)
            "Sessão expirada. Abra o FlowFuel no celular para entrar novamente."
        else
            "Erro ao carregar eventos. Verifique sua conexão."
        val builder = MessageTemplate.Builder(msg)
            .setTitle("Eventos")
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

    private fun successTemplate(events: List<VehicleEvent>): Template {
        val brLocale = Locale("pt", "BR")
        val currencyFmt = NumberFormat.getCurrencyInstance(brLocale)
        val listBuilder = ItemList.Builder()
        events.forEach { event ->
            val title = event.title.ifBlank { event.category.label }
            val date = event.eventDate.takeIf { it.length >= 10 }
                ?.let { "${it.substring(8, 10)}/${it.substring(5, 7)}" }
                ?: event.eventDate
            val amount = event.amount?.let { " • ${currencyFmt.format(it)}" } ?: ""
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(title)
                    .addText("$date$amount")
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Eventos")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
