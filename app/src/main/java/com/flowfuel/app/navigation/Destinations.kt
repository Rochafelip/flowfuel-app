package com.flowfuel.app.navigation

/** Rotas do NavHost raiz (autenticação + container principal). */
object Destinations {
    const val SPLASH           = "splash"
    const val ONBOARDING       = "onboarding"
    const val LOGIN            = "login"
    const val REGISTER         = "register"
    const val FORGOT           = "forgot"
    const val CHECK_EMAIL      = "auth/check-email/{email}?token={token}"
    const val RESET_PASSWORD   = "auth/reset-password/{email}"
    const val VEHICLE_PICKER   = "vehicle/picker"
    const val VEHICLE_ADD      = "vehicle/add"
    const val VEHICLE_DETAILS   = "vehicle/details/{vehicleId}"
    const val VEHICLE_EDIT      = "vehicle/edit/{vehicleId}"
    const val VEHICLE_ODOMETER  = "vehicle/odometer/{vehicleId}/{currentKm}"
    /** Gestão completa de veículos (lista/editar/detalhes), acessível a partir do Perfil. */
    const val VEHICLE_MANAGE    = "vehicle/manage"
    /** Container com BottomBar; hospeda o NavHost das abas. */
    const val MAIN_CONTAINER   = "main"
    const val CHANGE_PASSWORD  = "auth/change-password"
    const val EDIT_PROFILE     = "auth/edit-profile"
    const val REFUEL_DETAILS   = "refuel/details/{refuelId}"
    const val REFUEL_EDIT      = "refuel/edit/{refuelId}"

    const val VEHICLE_EVENTS        = "vehicle/events/{vehicleId}"
    const val VEHICLE_EVENT_CREATE  = "vehicle/events/create/{vehicleId}"
    const val VEHICLE_EVENT_DETAILS = "vehicle/events/details/{eventId}"
    const val VEHICLE_EVENT_EDIT    = "vehicle/events/edit/{eventId}"

    fun checkEmail(email: String, token: String? = null): String {
        val base = "auth/check-email/${java.net.URLEncoder.encode(email, "UTF-8")}"
        return if (token.isNullOrBlank()) base
            else "$base?token=${java.net.URLEncoder.encode(token, "UTF-8")}"
    }
    fun resetPassword(email: String) =
        "auth/reset-password/${java.net.URLEncoder.encode(email, "UTF-8")}"

    fun vehicleDetails(id: Int)                    = "vehicle/details/$id"
    fun vehicleEdit(id: Int)                       = "vehicle/edit/$id"
    fun vehicleOdometer(id: Int, currentKm: Int)   = "vehicle/odometer/$id/$currentKm"
    fun refuelDetails(id: Int)                     = "refuel/details/$id"
    fun refuelEdit(id: Int)                        = "refuel/edit/$id"
    fun vehicleEvents(vehicleId: Int)              = "vehicle/events/$vehicleId"
    fun vehicleEventCreate(vehicleId: Int)         = "vehicle/events/create/$vehicleId"
    fun vehicleEventDetails(eventId: Int)          = "vehicle/events/details/$eventId"
    fun vehicleEventEdit(eventId: Int)             = "vehicle/events/edit/$eventId"
}

/** Rotas do NavHost aninhado dentro de MainContainerScreen. */
object MainDestinations {
    const val HOME     = "main/home"
    const val HISTORY  = "main/history"
    const val STATIONS = "main/stations"
    const val EVENTS   = "main/events"
    const val PROFILE  = "main/profile"
}