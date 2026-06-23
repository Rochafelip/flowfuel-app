package com.flowfuel.app.feature.vehicleevent.data.remote

import com.flowfuel.app.feature.vehicle.data.remote.PagedResponseDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface VehicleEventApi {
    @POST("vehicle-events")
    suspend fun createEvent(@Body body: CreateVehicleEventRequestDto): VehicleEventResponseDto

    @GET("vehicle-events/vehicle/{vehicleId}")
    suspend fun getEventsByVehicle(
        @Path("vehicleId") vehicleId: Int,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("type") type: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
    ): PagedResponseDto<VehicleEventResponseDto>

    @GET("vehicle-events/{id}")
    suspend fun getEventById(@Path("id") id: Int): VehicleEventResponseDto

    @PUT("vehicle-events/{id}")
    suspend fun updateEvent(
        @Path("id") id: Int,
        @Body body: UpdateVehicleEventRequestDto,
    ): VehicleEventResponseDto

    @DELETE("vehicle-events/{id}")
    suspend fun deleteEvent(@Path("id") id: Int): ResponseBody?
}
