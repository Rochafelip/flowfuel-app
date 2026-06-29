package com.flowfuel.app.feature.auto

import androidx.car.app.CarAppService
import androidx.car.app.validation.HostValidator
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.feature.home.domain.usecase.CreateRefuelUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetActiveVehicleUseCase
import com.flowfuel.app.feature.home.domain.usecase.GetDashboardUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoCarAppServiceEntryPoint {
    fun sessionStore(): SessionStore
    fun getActiveVehicle(): GetActiveVehicleUseCase
    fun getDashboard(): GetDashboardUseCase
    fun createRefuel(): CreateRefuelUseCase
}

class AutoCarAppService : CarAppService() {

    // FIXME: Replace with signed-host validator before Google Play submission
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): androidx.car.app.Session {
        val ep = EntryPointAccessors.fromApplication(
            applicationContext,
            AutoCarAppServiceEntryPoint::class.java,
        )
        return AutoSession(ep.sessionStore(), ep.getActiveVehicle(), ep.getDashboard(), ep.createRefuel())
    }
}
