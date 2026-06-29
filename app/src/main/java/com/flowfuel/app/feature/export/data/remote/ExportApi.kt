package com.flowfuel.app.feature.export.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ExportApi {

    @Streaming
    @GET("exports/refuels")
    suspend fun exportRefuels(
        @Query("vehicleId") vehicleId: Int,
        @Query("format") format: String,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
    ): Response<ResponseBody>

    @Streaming
    @GET("exports/events")
    suspend fun exportEvents(
        @Query("vehicleId") vehicleId: Int,
        @Query("format") format: String,
        @Query("type") type: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
    ): Response<ResponseBody>
}
