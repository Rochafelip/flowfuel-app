// app/src/main/java/com/flowfuel/app/feature/vehicleevent/domain/model/VehicleTimelineItem.kt
package com.flowfuel.app.feature.vehicleevent.domain.model

import com.flowfuel.app.feature.history.domain.model.RefuelItem

sealed interface VehicleTimelineItem {
    val sortDate: String

    data class EventEntry(val event: VehicleEvent) : VehicleTimelineItem {
        override val sortDate get() = event.eventDate
    }

    data class RefuelEntry(val refuel: RefuelItem) : VehicleTimelineItem {
        override val sortDate get() = refuel.date
    }
}
