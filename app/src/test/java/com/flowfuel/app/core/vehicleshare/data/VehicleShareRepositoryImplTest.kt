package com.flowfuel.app.core.vehicleshare.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.vehicleshare.data.remote.VehicleShareApi
import com.flowfuel.app.core.vehicleshare.data.remote.VehicleShareRequestDto
import com.flowfuel.app.core.vehicleshare.data.remote.VehicleShareResponseDto
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class VehicleShareRepositoryImplTest {

    private val api: VehicleShareApi = mockk()
    private val repository = VehicleShareRepositoryImpl(api)

    private fun dto(status: String = "PENDING") = VehicleShareResponseDto(
        id = 100,
        vehicleId = 10,
        vehicleBrand = "Toyota",
        vehicleModel = "Corolla",
        ownerId = 1,
        ownerName = "Dono",
        guestId = 2,
        guestName = "Convidado",
        status = status,
        createdAt = "2026-07-15T10:00:00",
        respondedAt = null,
        expiresAt = null,
    )

    @Test
    fun invite_sucesso_mapeiaParaDominio() = runTest {
        coEvery { api.createShare(VehicleShareRequestDto(10, "guest@test.com", 3)) } returns dto()

        val result = repository.invite(10, "guest@test.com", 3)

        assertTrue(result is AppResult.Success)
        val share = (result as AppResult.Success).value
        assertEquals(100, share.id)
        assertEquals(VehicleShareStatus.PENDING, share.status)
    }

    @Test
    fun accept_sucesso_mapeiaStatusAtivo() = runTest {
        coEvery { api.acceptShare(100) } returns dto(status = "ACTIVE")

        val result = repository.accept(100)

        assertTrue(result is AppResult.Success)
        assertEquals(VehicleShareStatus.ACTIVE, (result as AppResult.Success).value.status)
    }

    @Test
    fun getForVehicle_semShareAtivo_retornaNull() = runTest {
        coEvery { api.getShareForVehicle(10) } returns Response.success<VehicleShareResponseDto>(204, null)

        val result = repository.getForVehicle(10)

        assertTrue(result is AppResult.Success)
        assertNull((result as AppResult.Success).value)
    }

    @Test
    fun getForVehicle_comShareAtivo_mapeiaParaDominio() = runTest {
        coEvery { api.getShareForVehicle(10) } returns Response.success(dto(status = "ACTIVE"))

        val result = repository.getForVehicle(10)

        assertTrue(result is AppResult.Success)
        assertEquals(VehicleShareStatus.ACTIVE, (result as AppResult.Success).value?.status)
    }

    @Test
    fun getActiveForMe_listaComShares_mapeiaTodos() = runTest {
        coEvery { api.getActiveForMe() } returns listOf(dto(status = "ACTIVE"))

        val result = repository.getActiveForMe()

        assertTrue(result is AppResult.Success)
        assertEquals(1, (result as AppResult.Success).value.size)
    }
}
